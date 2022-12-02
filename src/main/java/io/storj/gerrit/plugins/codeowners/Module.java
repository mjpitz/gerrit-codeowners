package io.storj.gerrit.plugins.codeowners;

import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Inject;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

public class Module extends AbstractModule {
    @Inject
    private PluginConfigFactory cfg;
    @Inject
    private SitePaths sitePaths;

    public static class MissingRequiredConfiguration extends RuntimeException {
        public MissingRequiredConfiguration(final String message, final Throwable cause) {
            super(message, cause);
        }
        public MissingRequiredConfiguration(final String message) {
            super(message);
        }
    }

    public static class UnableToCreateGithubInstance extends RuntimeException {
        public UnableToCreateGithubInstance(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    protected void configure() {
        PluginConfig config = cfg.getFromGerritConfig("codeowners");

        String githubToken = config.getString("githubToken", "");

        String githubAppID = config.getString("githubAppID", "");
        long githubInstanceID = config.getLong("githubInstanceID", 0);
        String githubKeyPath = config.getString("githubKeyPath", "");


        final GitHub github;

        try {
            if(!githubAppID.equals("")) {
                if(githubInstanceID == 0)
                    throw new MissingRequiredConfiguration("githubInstanceID missing");
                if(githubKeyPath.equals(""))
                    throw new MissingRequiredConfiguration("githubKeyPath missing");

                Path keyPath = sitePaths.etc_dir.resolve(githubKeyPath);
                if(!Files.isReadable(keyPath))
                    throw new MissingRequiredConfiguration("unable to find key: " + githubKeyPath);

                JWTTokenProvider appAuth = new JWTTokenProvider(githubAppID, keyPath);
                OrgInstanceAuthorizationProvider instanceAuth = new OrgInstanceAuthorizationProvider(githubInstanceID, appAuth);
                github = new GitHubBuilder().withAuthorizationProvider(instanceAuth).build();
            } else if (!githubToken.equals("")){
                github = new GitHubBuilder().withJwtToken(githubToken).build();
            } else {
                throw new MissingRequiredConfiguration("github client authentication not configured");
            }
        } catch (final IOException e) {
            // rethrow, missing required configuration
            throw new MissingRequiredConfiguration("missing required environment variables", e);
        } catch (GeneralSecurityException e) {
            throw new UnableToCreateGithubInstance("creating a github client failed", e);
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
