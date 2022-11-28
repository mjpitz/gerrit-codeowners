package io.storj.gerrit.plugins.codeowners;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class HashRingTest {
    @Test
    public void test() throws NoSuchAlgorithmException {
        HashRing ring = new HashRing();

        for (int i = 0; i < 10; i++) {
            ring = ring.withNode("node-" + i + ".dev");
        }

        assertEquals(10, ring.size());

        {
            final List<String> nodes = Lists.newArrayList("node-4.dev", "node-2.dev");
            assertEquals(Sets.newLinkedHashSet(nodes), ring.getNodes("I46d7836449e4b866f98bd66e0bf4cf3cb16e71a4", 2));
            assertEquals("node-4.dev", ring.getNode("I46d7836449e4b866f98bd66e0bf4cf3cb16e71a4"));
        }

        {
            final List<String> nodes = Lists.newArrayList("node-1.dev", "node-3.dev");
            assertEquals(Sets.newLinkedHashSet(nodes), ring.getNodes("I2a76d48bd1241367aa2d1e3309f6f65d6d6ea4dc", 2));
            assertEquals("node-1.dev", ring.getNode("I2a76d48bd1241367aa2d1e3309f6f65d6d6ea4dc"));
        }

        {
            final List<String> nodes = Lists.newArrayList("node-2.dev", "node-6.dev");
            assertEquals(Sets.newLinkedHashSet(nodes), ring.getNodes("I18a8968b4f0385a1e4de6784dee68e1b51df86f7", 2));
            assertEquals("node-2.dev", ring.getNode("I18a8968b4f0385a1e4de6784dee68e1b51df86f7"));
        }

        ring = ring.withoutNode("node-0.dev");

        assertEquals(9, ring.size());

        {
            final List<String> nodes = Lists.newArrayList("node-4.dev", "node-2.dev");
            assertEquals(Sets.newLinkedHashSet(nodes), ring.getNodes("I46d7836449e4b866f98bd66e0bf4cf3cb16e71a4", 2));
            assertEquals("node-4.dev", ring.getNode("I46d7836449e4b866f98bd66e0bf4cf3cb16e71a4"));
        }

        {
            final List<String> nodes = Lists.newArrayList("node-1.dev", "node-3.dev");
            assertEquals(Sets.newLinkedHashSet(nodes), ring.getNodes("I2a76d48bd1241367aa2d1e3309f6f65d6d6ea4dc", 2));
            assertEquals("node-1.dev", ring.getNode("I2a76d48bd1241367aa2d1e3309f6f65d6d6ea4dc"));
        }

        {
            final List<String> nodes = Lists.newArrayList("node-2.dev", "node-6.dev");
            assertEquals(Sets.newLinkedHashSet(nodes), ring.getNodes("I18a8968b4f0385a1e4de6784dee68e1b51df86f7", 2));
            assertEquals("node-2.dev", ring.getNode("I18a8968b4f0385a1e4de6784dee68e1b51df86f7"));
        }
    }

    @Test
    public void collectionConstructor() throws NoSuchAlgorithmException {
        Set<Integer> reviewers = new HashSet<>();
        reviewers.add(1000002);
        reviewers.add(1000001);
        List<String> converted = reviewers.stream().map(Object::toString).collect(Collectors.toList());
        HashRing reviewersRing = HashRing.fromElements(HashRing.MD5, converted);
        Set<String> chosenReviewers = reviewersRing.getNodes("testrepo~main~Icfc6ff06f3d72526ceb5f74c6a7cd99fa429f51f", 2);

        Assert.assertEquals(2, chosenReviewers.size());

    }

    @Test
    public void fromEnd() throws NoSuchAlgorithmException {
        HashRing ring = new HashRing(s -> s);
        ring = ring.withNode("aaa");
        ring = ring.withNode("bbb");
        ring = ring.withNode("ccc");

        Assert.assertEquals(Sets.newHashSet("bbb", "ccc"), ring.getNodes("bb1", 2));
        Assert.assertEquals(Sets.newHashSet("aaa", "bbb"), ring.getNodes("ddd", 2));
    }

    @Test
    public void emptyRing() throws NoSuchAlgorithmException {
        HashRing ring = new HashRing(s -> s);
        Assert.assertEquals(new HashSet<>(), ring.getNodes("bb1", 2));
    }

    @Test
    public void zeroRequest() throws NoSuchAlgorithmException {
        HashRing ring = new HashRing(s -> s);
        ring = ring.withNode("asd");
        ring = ring.withNode("basd");
        Assert.assertEquals(new HashSet<>(), ring.getNodes("bb1", 0));
    }

    @Test
    public void notEnoughAssignee() throws NoSuchAlgorithmException {
        HashRing ring = new HashRing(s -> s);
        ring = ring.withNode("ccc");

        Assert.assertEquals(Sets.newHashSet("ccc"), ring.getNodes("ddd", 1));
        Assert.assertEquals(Sets.newHashSet("ccc"), ring.getNodes("ddd", 2));
        Assert.assertEquals(Sets.newHashSet("ccc"), ring.getNodes("bb1", 2));

    }
}
