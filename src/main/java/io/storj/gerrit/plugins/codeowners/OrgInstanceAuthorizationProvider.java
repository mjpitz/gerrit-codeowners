package io.storj.gerrit.plugins.codeowners;

import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.AuthorizationProvider;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

// OrgInstanceAuthorizationProvider implements automatic refreshing token that can be used to do
// per instance operations.
public class OrgInstanceAuthorizationProvider extends GitHub.DependentAuthorizationProvider {
    private final long instanceID;
    private String authorization;
    @Nonnull
    private Instant validUntil;

    public OrgInstanceAuthorizationProvider(long instanceID, AuthorizationProvider authorizationProvider) {
        super(authorizationProvider);
        this.validUntil = Instant.MIN;
        this.instanceID = instanceID;
    }

    public String getEncodedAuthorization() throws IOException {
        synchronized(this) {
            if (this.authorization == null || Instant.now().isAfter(this.validUntil)) {
                String token = this.refreshToken();
                this.authorization = String.format("token %s", token);
            }

            return this.authorization;
        }
    }

    private String refreshToken() throws IOException {
        GitHub gitHub = this.gitHub();
        GHAppInstallation installationByOrganization = gitHub.getApp().getInstallationById(this.instanceID);
        GHAppInstallationToken ghAppInstallationToken = installationByOrganization.createToken().create();
        this.validUntil = ghAppInstallationToken.getExpiresAt().toInstant().minus(Duration.ofMinutes(5L));
        return (String) Objects.requireNonNull(ghAppInstallationToken.getToken());
    }
}
