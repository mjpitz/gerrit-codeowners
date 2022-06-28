package com.mjpitz.gerrit.plugins.codeowners;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mjpitz.codeowners.Config;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class ReviewAssigner implements WorkInProgressStateChangedListener, CommentAddedListener, RevisionCreatedListener {
    private static final Logger log = Logger.getLogger(ReviewAssigner.class);

    private final GitHub github;
    private final GerritApi gerrit;
    private final GitRepositoryManager git;

    // use a global cache to reduce calls to the gerrit APIs
    // [user/email:]name -> gerritAccountId
    private final ConcurrentMap<String, Integer> cache = new ConcurrentHashMap<>();
    private final Function<String, Integer> loader;

    @Inject
    public ReviewAssigner(final GitHub github, final GerritApi gerrit, final GitRepositoryManager git) {
        this.github = github;
        this.gerrit = gerrit;
        this.git = git;

        this.loader = (k) -> {
            try {
                final List<AccountInfo> accounts = this.gerrit.accounts().query(k).get();

                if (accounts.size() > 0) {
                    return accounts.get(0)._accountId;
                }
            } catch (final RestApiException e) {
                log.error("failed to query account", e);
            }

            return null;
        };
    }

    private void unassign(final ChangeInfo change, final RevisionInfo revision) {
        // TODO
    }

    private Config loadCodeOwners(final Repository repo, final ChangeInfo change) {
        final String ref = "refs/heads/" + change.branch;

        final Optional<byte[]> data = Optional.<byte[]>empty().or(() -> JgitWrapper.getBlobAsBytes(repo, ref, "CODEOWNERS"))
                .or(() -> JgitWrapper.getBlobAsBytes(repo, ref, ".github/CODEOWNERS"))
                .or(() -> JgitWrapper.getBlobAsBytes(repo, ref, "docs/CODEOWNERS"));

        //noinspection OptionalIsPresent
        if (!data.isPresent()) {
            return null;
        }

        return Config.parse(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data.get()))).lines());
    }

    public Set<Integer> fromCodeOwners(Config config, Set<String> changedFiles) throws IOException {
        Set<Integer> accounts = new HashSet<>();

        final Set<String> owners = new HashSet<>();
        for (final String path : changedFiles) {
            owners.addAll(config.ownersFor(path));
        }

        for (String owner : owners) {
            final int splitIndex = owner.indexOf('/');

            //format of: @username
            if (!owner.startsWith("@")) {
                Integer accountId = cache.computeIfAbsent("email:" + owner, loader);
                if (accountId != null) {
                    accounts.add(accountId);
                }
                continue;

                //format of: email@domain.com
            } else if (splitIndex == -1) {
                // User
                Integer accountId = cache.computeIfAbsent("username:" + owner.substring(1), loader);
                if (accountId != null) {
                    accounts.add(accountId);
                }
                // TODO: translate users we couldn't match
                continue;
            }

            // `owner` starts with `@` and contains a '/', therefore it's a org-team pair
            // Format: @{org}/{team}
            final String orgName = owner.substring(1, splitIndex);
            final String teamName = owner.substring(splitIndex + 1);

            final List<GHUser> members = new ArrayList<>();

            GHOrganization organization = github.getOrganization(orgName);
            if (organization != null) {
                GHTeam team = organization.getTeamBySlug(teamName);
                if (team != null) {
                    members.addAll(team.getMembers());
                } else {
                    log.warn(String.format("Github team couldn't be found: %s/%s", orgName, teamName));
                }
            } else {
                log.warn(String.format("Github organization couldn't be found: %s", orgName));
            }

            members.sort(Comparator.comparing(GHUser::getLogin));

            for (final GHUser member : members) {
                Integer accountId = cache.computeIfAbsent("username:" + member.getLogin(), loader);
                if (accountId != null) {
                    accounts.add(accountId);
                }

                // TODO: translate users we couldn't match
            }
        }

        return accounts;
    }

    private Set<Integer> fromGit(Integer ownerId, Set<Integer> accounts, Repository repo, Set<String> changedFiles, int requiredCount) throws GitAPIException {
        final Git git = Git.wrap(repo);
        final LogCommand logCommand = git.log();

        for (final String path : changedFiles) {
            logCommand.addPath(path);
        }

        final Iterator<RevCommit> log = logCommand.call().iterator();

        while (requiredCount > accounts.size() && log.hasNext()) {
            final RevCommit commit = log.next();
            final PersonIdent author = commit.getAuthorIdent();

            Integer accountId = cache.computeIfAbsent(author.getEmailAddress(), loader);
            if (accountId != null && !accountId.equals(ownerId)) {
                accounts.add(accountId);
            }
        }

        return accounts;
    }

    private void assign(final ChangeInfo change, final RevisionInfo revision) throws IOException, GitAPIException, NoSuchAlgorithmException, RestApiException {
        try (final Repository repo = git.openRepository(Project.nameKey(change.project))) {
            Config config;
            // use hash-ring to track possible reviewers
            Set<Integer> reviewers;
            int missingReviewers = 0;

            // attempt to load the code owners file

            config = loadCodeOwners(repo, change);

            if (config == null) {
                //no CODEOWNERS file, nothing to do.
                return;
            }

            reviewers = fromCodeOwners(config, revision.files.keySet());
            // original owner is not a reviwer
            reviewers.remove(change.owner._accountId);

            int existingReviewers = 0;
            if (change.reviewers != null && change.reviewers.get(ReviewerState.REVIEWER) != null){
                existingReviewers = change.reviewers.get(ReviewerState.REVIEWER).size();
            }
            missingReviewers = config.reviewerCount - existingReviewers;

            // if insufficient after populating from the owners file, attempt to augment with git history
            if (reviewers.size() < config.reviewerCount) {
                reviewers = fromGit(change.owner._accountId, reviewers, repo, revision.files.keySet(), missingReviewers);
            }

            // assign reviewers from the stable hash-ring
            final int numberToAssign = Math.min(reviewers.size(), missingReviewers);
            log.info(change.id);
            log.info(reviewers);
            HashRing reviewersRing = HashRing.fromElements(HashRing.MD5, reviewers.stream().map(Object::toString).collect(Collectors.toList()));
            Set<String> chosenReviewers = reviewersRing.getNodes(change.id, numberToAssign);
            log.info(reviewersRing.size());
            log.info(String.format("assigning %d (%s) reviewers to change %s choosing from %s", numberToAssign, chosenReviewers, change.id, reviewers));

            // update the change
            final ReviewInput request = new ReviewInput();

            request.reviewers = chosenReviewers.stream().map((reviewer) -> {
                final ReviewerInput r = new ReviewerInput();
                r.reviewer = reviewer;
                r.state = ReviewerState.REVIEWER;
                return r;
            }).collect(Collectors.toList());

            ReviewResult result = gerrit.changes().id(change.id).current().review(request);
            if (result.error != null && result.error != "") {
                log.error(String.format("Error on setting reviewers for %s: %s", change.id, result.error));
            }
        }
    }

    @Override
    public void onWorkInProgressStateChanged(final WorkInProgressStateChangedListener.Event event) {
        final ChangeInfo change = event.getChange();
        final RevisionInfo revision = event.getRevision();

        final boolean workInProgress = change.workInProgress != null && change.workInProgress;
        final boolean isPrivate = change.isPrivate != null && change.isPrivate;

        try {
            if (workInProgress || isPrivate) {
                // if a review is toggled from active => work in progress / private, remove auto-assigned reviewers.
                log.info("removing reviewers from " + change.id);
                unassign(change, revision);
            } else {
                // if a review is toggled from work in progress / private => active, add auto-assigned reviewers.
                log.info("assigning reviewers to " + change.id);
                assign(change, revision);
            }
        } catch (final IOException | GitAPIException | NoSuchAlgorithmException | RestApiException e) {
            log.error("failed to update reviewers on " + change.id, e);
        }
    }


    @Override
    public void onCommentAdded(CommentAddedListener.Event event) {
        if (event.getComment() != null && event.getComment().contains("autoassign")) {
            try {
                // if a review is toggled from work in progress / private => active, add auto-assigned reviewers.
                log.info("assigning reviewers to " + event.getChange().id);
                assign(event.getChange(), event.getRevision());
            } catch (final IOException | GitAPIException | NoSuchAlgorithmException | RestApiException e) {
                log.error("failed to update reviewers on " + event.getChange().id, e);
            }
        }
    }

    @Override
    public void onRevisionCreated(RevisionCreatedListener.Event event) {
        try {
            // this is empty during the rebase.
            if (event.getChange() == null) {
                return;
            }
            if (event.getChange() != null && event.getChange().isPrivate != null && event.getChange().isPrivate) {
                return;
            }
            if (event.getChange() != null && event.getChange().workInProgress != null && event.getChange().workInProgress) {
                return;
            }

            // if a review is toggled from work in progress / private => active, add auto-assigned reviewers.
            log.info("assigning reviewers to " + event.getChange().id);
            assign(event.getChange(), event.getRevision());
        } catch (final IOException | GitAPIException | NoSuchAlgorithmException | RestApiException e) {
            log.error("failed to update reviewers on " + event.getChange().id, e);
        }
    }

}
