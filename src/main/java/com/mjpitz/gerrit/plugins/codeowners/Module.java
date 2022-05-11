package com.mjpitz.gerrit.plugins.codeowners;

import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;

public class Module extends AbstractModule {
    public static class MissingRequiredConfiguration extends RuntimeException {
        public MissingRequiredConfiguration(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    protected void configure() {
        final GitHub github;

        try {
            // via PAT...
            // export GITHUB_LOGIN=my_org
            // export GITHUB_OAUTH=xxxxxx
            //
            // https://github-api.kohsuke.org/#Environmental_variables
            //
            github = GitHubBuilder.fromEnvironment().build();
        } catch (final IOException e) {
            // rethrow, missing required configuration
            throw new MissingRequiredConfiguration("missing required environment variables", e);
        }

        final Binder binder = binder();

        // Guice bindings
        binder.bind(GitHub.class).toInstance(github);

        // Gerrit bindings
        DynamicSet.bind(binder, WorkInProgressStateChangedListener.class).to(ReviewAssigner.class);
        DynamicSet.bind(binder, CommentAddedListener.class).to(ReviewAssigner.class);
        DynamicSet.bind(binder, RevisionCreatedListener.class).to(ReviewAssigner.class);
    }
}
