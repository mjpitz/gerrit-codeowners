package com.mjpitz.gerrit.plugins.codeowners;

import com.google.common.collect.Lists;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
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
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Singleton
public class ReviewAssigner implements WorkInProgressStateChangedListener {
    private static final Logger log = Logger.getLogger(ReviewAssigner.class);

    private final GitHub github;
    private final GerritApi gerrit;
    private final GitRepositoryManager git;

    // use a global cache to reduce calls to the gerrit APIs
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
    private final BiFunction<String, String, String> loader;

    @Inject
    public ReviewAssigner(
            final GitHub github,
            final GerritApi gerrit,
            final GitRepositoryManager git
    ) {
        this.github = github;
        this.gerrit = gerrit;
        this.git = git;

        this.loader = (k, v) -> {
            if (v != null) {
                return v;
            }

            try {
                final List<AccountInfo> accounts = this.gerrit.accounts().query(k).get();

                if (accounts.size() > 0) {
                    return accounts.get(0)._accountId.toString();
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

        final Optional<byte[]> data = Optional.<byte[]>empty()
                .or(() -> JgitWrapper.getBlobAsBytes(repo, ref, "CODEOWNERS"))
                .or(() -> JgitWrapper.getBlobAsBytes(repo, ref, ".github/CODEOWNERS"))
                .or(() -> JgitWrapper.getBlobAsBytes(repo, ref, "docs/CODEOWNERS"));

        //noinspection OptionalIsPresent
        if (!data.isPresent()) {
            return null;
        }

        return Config.parse(
                new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data.get()))).lines()
        );
    }

    public HashRing fromCodeOwners(final Config config, final RevisionInfo revision) throws NoSuchAlgorithmException, IOException {
        HashRing possibleReviewers = new HashRing();

        final Set<String> owners = new HashSet<>();
        for (final String path : revision.files.keySet()) {
            owners.addAll(config.ownersFor(path));
        }

        for (String owner : owners) {
            final int splitIndex = owner.indexOf('/');

            if (!owner.startsWith("@")) {
                final String username = cache.compute("email:" + owner, loader);
                if (username != null) {
                    possibleReviewers = possibleReviewers.withNode(username);
                }

                continue;
            } else if (splitIndex == -1) {
                // User
                final String username = cache.compute("username:" + owner.substring(1), loader);
                if (username != null) {
                    possibleReviewers = possibleReviewers.withNode(username);
                }

                // TODO: translate users we couldn't match
                continue;
            }

            // `owner` starts with `@` and contains a '/', therefore it's a org-team pair
            // Format: @{org}/{team}

            final String orgName = owner.substring(1, splitIndex);
            final String teamName = owner.substring(splitIndex + 1);

            final List<GHUser> members = Lists.newArrayList(github.getOrganization(orgName).getTeamByName(teamName).getMembers());
            members.sort(Comparator.comparing(GHUser::getLogin));

            for (final GHUser member : members) {
                final String username = cache.compute("username:" + member.getLogin(), loader);
                if (username != null) {
                    possibleReviewers = possibleReviewers.withNode(username);
                }

                // TODO: translate users we couldn't match
            }
        }

        return possibleReviewers;
    }

    private HashRing fromGit(HashRing possibleReviewers, final Repository repo, final RevisionInfo revision, int reviewerCount) throws GitAPIException, NoSuchAlgorithmException {
        final Git git = Git.wrap(repo);
        final LogCommand logCommand = git.log();

        for (final String path : revision.files.keySet()) {
            logCommand.addPath(path);
        }

        final Iterator<RevCommit> log = logCommand.call().iterator();

        while (possibleReviewers.size() < reviewerCount && log.hasNext()) {
            final RevCommit commit = log.next();
            final PersonIdent author = commit.getAuthorIdent();

            final String username = cache.compute(author.getEmailAddress(), loader);
            if (username != null) {
                possibleReviewers = possibleReviewers.withNode(username);
            }
        }

        return possibleReviewers;
    }

    private void assign(final ChangeInfo change, final RevisionInfo revision) throws IOException, GitAPIException, NoSuchAlgorithmException, RestApiException {
        // use hash-ring to track possible reviewers
        HashRing possibleReviewers = new HashRing();
        int missingReviewers = 0;
        try (final Repository repo = git.openRepository(Project.nameKey(change.project))) {
            // attempt to load the code owners file

            final Config config = loadCodeOwners(repo, change);
            if (config != null) {
                possibleReviewers = fromCodeOwners(config, revision);
            }

            missingReviewers = config.reviewerCount - change.reviewers.size();

            if (missingReviewers <= 0) {
                // we have enough reviewers.
                return;
            }
            // if insufficient after populating from the owners file, attempt to augment with git history
            if (possibleReviewers.size() < missingReviewers) {
                possibleReviewers = fromGit(possibleReviewers, repo, revision, missingReviewers);
            }
        }

        // assign reviewers from the stable hash-ring

        final int numberToAssign = Math.min(possibleReviewers.size(), missingReviewers);
        log.info("assigning " + numberToAssign + " reviewers to " + change.id);

        final Set<String> reviewers = possibleReviewers.getNodes(change.id, numberToAssign);

        // update the change

        final ReviewInput request = new ReviewInput();
        request.reviewers = reviewers.stream()
                .map((reviewer) -> {
                    final ReviewerInput r = new ReviewerInput();
                    r.reviewer = reviewer;
                    r.state = ReviewerState.REVIEWER;
                    return r;
                })
                .collect(Collectors.toList());

        gerrit.changes().id(change.id).current().review(request);
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
}
