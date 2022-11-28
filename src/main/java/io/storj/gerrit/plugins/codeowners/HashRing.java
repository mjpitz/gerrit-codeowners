package io.storj.gerrit.plugins.codeowners;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

public class HashRing {

    public interface Hasher extends Function<String, String> {
    }

    public static Hasher MD5 = val -> {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final byte[] digest = md.digest(val.getBytes());
            final BigInteger no = new BigInteger(1, digest);

            // Convert message digest into hex value
            StringBuilder hashtext = new StringBuilder(no.toString(16));
            while (hashtext.length() < 32) {
                hashtext.insert(0, "0");
            }

            return hashtext.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    };

    private final Hasher hasher;
    private final LinkedHashMap<String, Integer> weights;
    private final TreeMap<String, String> ring;

    public HashRing() {
        this(MD5);
    }

    public HashRing(final Hasher hasher) {
        this(hasher, new LinkedHashMap<>());
    }


    private HashRing(final Hasher hasher, final LinkedHashMap<String, Integer> weights) {
        this.hasher = hasher;
        this.weights = weights;
        this.ring = new TreeMap<>();
    }

    public static HashRing fromElements(final Hasher hasher, Collection<String> elements) throws NoSuchAlgorithmException {
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        for (String element : elements) {
            weights.put(element, 1);
        }
        HashRing hashRing = new HashRing(hasher, weights);
        hashRing.make();
        return hashRing;
    }

    public int size() {
        return weights.size();
    }

    private void make() throws NoSuchAlgorithmException {
        for (final Map.Entry<String, Integer> entry : weights.entrySet()) {
            final String node = entry.getKey();
            final int weight = entry.getValue();

            for (int i = 0; i < weight; i++) {
                final String key = hasher.apply(node + "-" + i);
                ring.put(key, node);
            }
        }
    }

    public HashRing withNode(final String node) throws NoSuchAlgorithmException {
        return withWeightedNode(node, 1);
    }

    public HashRing withWeightedNode(final String node, final int weight) throws NoSuchAlgorithmException {
        if (weight <= 0) {
            return this;
        }

        final LinkedHashMap<String, Integer> weights = new LinkedHashMap<>(this.weights);
        weights.put(node, weight);

        final HashRing ring = new HashRing(hasher, weights);
        ring.make();
        return ring;
    }

    public HashRing withoutNode(final String node) throws NoSuchAlgorithmException {
        final LinkedHashMap<String, Integer> weights = new LinkedHashMap<>(this.weights);
        weights.remove(node);

        final HashRing ring = new HashRing(hasher, weights);
        ring.make();
        return ring;
    }

    public String getNode(final String key) throws NoSuchAlgorithmException {
        for (final String node : getNodes(key, 1)) {
            return node;
        }

        return "";
    }

    public Set<String> getNodes(final String key, final int size) {

        final int l = size();
        if (l == 0 || size == 0) {
            return new HashSet<>();
        }

        final Set<String> nodes = new LinkedHashSet<>();
        final String needle = hasher.apply(key);

        Iterator<Map.Entry<String, String>> iterator = ring.tailMap(needle, false).entrySet().iterator();
        while (iterator.hasNext()) {
            nodes.add(iterator.next().getValue());
            if (nodes.size() == size) {
                return nodes;
            }
        }

        iterator = ring.headMap(needle, false).entrySet().iterator();
        while (iterator.hasNext()) {
            nodes.add(iterator.next().getValue());
            if (nodes.size() == size) {
                return nodes;
            }
        }

        return nodes;
    }
}
