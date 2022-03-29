package com.mjpitz.gerrit.plugins.codeowners;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Singleton
public class ReviewAssigner implements WorkInProgressStateChangedListener {
    private final GerritApi api;
    private final GitRepositoryManager mgr;

    @Inject
    public ReviewAssigner(final GerritApi api, final GitRepositoryManager mgr) {
        this.api = api;
        this.mgr = mgr;
    }

    private void unassign(final ChangeInfo change, final RevisionInfo revision) {
    }

    private void assign(final ChangeInfo change, final RevisionInfo revision) throws IOException, GitAPIException {
        final Repository repo = mgr.openRepository(Project.nameKey(change.project));
        // read and parse CODEOWNERS

        // - First, we'll attempt to match files from the review to entries in the CODEOWNERS file
        // - Then, we expand all teams to their associated members, giving us a complete list of possible reviewers.
        // - If that list is empty or contains less than the number of needed auto-assigned reviewers, then source
        //   additional reviewers from the log of files in the review, prioritizing those who recently touched the file.
        // - Once we have a complete list of possible assignees, we need to do selection.
        //   - A random selection is likely sufficient for a v0.
        //   - Supporting something like a hashring probably wouldn't be that much more work and might offer better
        //     results compared to the random selection.

        final Git git = Git.wrap(repo);
        final LogCommand log = git.log();

        for (final Map.Entry<String, FileInfo> entry : revision.files.entrySet()) {
            log.addPath(entry.getKey());
        }

        for (final RevCommit commit : log.call()) {
            final PersonIdent author = commit.getAuthorIdent();
        }
    }

    @Override
    public void onWorkInProgressStateChanged(final WorkInProgressStateChangedListener.Event event) {
        final ChangeInfo change = event.getChange();
        final RevisionInfo revision = event.getRevision();

        try {
            if (change.workInProgress || change.isPrivate) {
                // if a review is toggled from active => work in progress / private, remove auto-assigned reviewers.
                unassign(change, revision);
            } else {
                // if a review is toggled from work in progress / private => active, add auto-assigned reviewers.
                assign(change, revision);
            }
        } catch (final IOException|GitAPIException e) {
            // log err
        }
    }
}
