package io.storj.codeowners;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigTest {
    @Test
    public void test() throws URISyntaxException, IOException {
        final Config config = Config.parse(Files.lines(
                Paths.get(ClassLoader.getSystemResource("TEST_CODEOWNERS").toURI())
        ));

        assertEquals(11, config.rules.size());
        assertEquals(3, config.reviewerCount);

        assertEquals(3, config.ownersFor("/apps/github").size());
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@js-owner", "@octocat"), config.ownersFor("/apps/main.js"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "docs@example.com", "@octocat"), config.ownersFor("/apps/main.go"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@octocat", "@doctocat"), config.ownersFor("/scripts/deploy.sh"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@doctocat", "docs@example.com"), config.ownersFor("/docs/README.md"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@octocat", "@js-owner"), config.ownersFor("/internal/apps/main.js"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@octocat", "docs@example.com"), config.ownersFor("/internal/apps/main.go"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2"), config.ownersFor("/internal/docs/README.md"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@doctocat"), config.ownersFor("/build/logs/out.json"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@octo-org/octocats"), config.ownersFor("/internal/lib/lib.txt"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "docs@example.com"), config.ownersFor("/internal/lib/lib.go"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@js-owner"), config.ownersFor("/internal/lib/index.js"));
        assertEquals(Sets.newHashSet("@global-owner1", "@global-owner2", "@global-owner1", "@global-owner2"), config.ownersFor("Jenkinsfile"));
    }

    @Test
    public void multiUser() throws Exception {
        try (Reader reader = new StringReader("* @kossuth @lajos")) {
            Config config = Config.open(reader);

            Assert.assertEquals(1, config.rules.size());
            Config.Rule rule = config.rules.get(0);
            Assert.assertEquals(2, rule.owners.size());
        }
    }

    @Test
    public void reviewerCountPattern() throws Exception {
        Matcher matcher = Config.REVIEWER_COUNT_PATTERN.matcher("# gerrit-codeowners.reviewer-count: 3");
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("3", matcher.group(1));

        matcher = Config.REVIEWER_COUNT_PATTERN.matcher("#gerrit-codeowners.reviewer-count:55");
        Assert.assertTrue(matcher.find());
        Assert.assertEquals("55", matcher.group(1));

        matcher = Config.REVIEWER_COUNT_PATTERN.matcher("#gerrit-codeownersxxx: 5");
        Assert.assertFalse(matcher.find());
    }

    @Test
    public void geitHistoryEnabled() throws Exception {
        final Config config = Config.parse(Files.lines(
                Paths.get(ClassLoader.getSystemResource("TEST_CODEOWNERS4").toURI())
        ));

        assertTrue(config.useGitHistory);
    }

    @Test
    public void simple() throws URISyntaxException, IOException {
        final Config config = Config.parse(Files.lines(
                Paths.get(ClassLoader.getSystemResource("TEST_CODEOWNERS3").toURI())
        ));

        assertEquals(1, config.rules.size());
        assertEquals(2, config.reviewerCount);

        assertEquals(Sets.newHashSet("@storj/example"), config.ownersFor("/README.md"));
    }
}
