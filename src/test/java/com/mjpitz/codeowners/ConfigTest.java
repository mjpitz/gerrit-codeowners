package com.mjpitz.codeowners;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class ConfigTest {
    @Test
    public void test() throws URISyntaxException, IOException {
        final Config config = Config.parse(Files.lines(
                Paths.get(ClassLoader.getSystemResource("TEST_CODEOWNERS").toURI())
        ));

        assertEquals(11, config.rules.size());

        assertEquals(0, config.ownersFor("/apps/github").size());
        assertEquals(Sets.newHashSet("@octocat"), config.ownersFor("/apps/main.js"));
        assertEquals(Sets.newHashSet("@octocat"), config.ownersFor("/apps/main.go"));
        assertEquals(Sets.newHashSet("@octocat", "@doctocat"), config.ownersFor("/scripts/deploy.sh"));
        assertEquals(Sets.newHashSet("@doctocat"), config.ownersFor("/docs/README.md"));
        assertEquals(Sets.newHashSet("@octocat"), config.ownersFor("/internal/apps/main.js"));
        assertEquals(Sets.newHashSet("@octocat"), config.ownersFor("/internal/apps/main.go"));
        //assertEquals(Sets.newHashSet("docs@example.com"), config.ownersFor("/internal/docs/README.md")); // hmmm....
        assertEquals(Sets.newHashSet("@doctocat"), config.ownersFor("/build/logs/out.json"));
        assertEquals(Sets.newHashSet("@octo-org/octocats"), config.ownersFor("/internal/lib/lib.txt"));
        assertEquals(Sets.newHashSet("docs@example.com"), config.ownersFor("/internal/lib/lib.go"));
        assertEquals(Sets.newHashSet("@js-owner"), config.ownersFor("/internal/lib/index.js"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2"), config.ownersFor("Jenkinsfile"));
    }
}
