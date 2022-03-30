package com.mjpitz.gerrit.plugins.codeowners;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class HashRing {
    @FunctionalInterface
    public interface Hasher {
        String hash(String val) throws NoSuchAlgorithmException;
    }

    public static Hasher MD5 = val -> {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final byte[] digest = md.digest(val.getBytes());
        final BigInteger no = new BigInteger(1, digest);

        // Convert message digest into hex value
        StringBuilder hashtext = new StringBuilder(no.toString(16));
        while (hashtext.length() < 32) {
            hashtext.insert(0, "0");
        }

        return hashtext.toString();
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

    private HashRing(
            final Hasher hasher,
            final LinkedHashMap<String, Integer> weights
    ) {
        this.hasher = hasher;
        this.weights = weights;
        this.ring = new TreeMap<>();
    }

    public int size() {
        return weights.size();
    }

    private void make() throws NoSuchAlgorithmException {
        for (final Map.Entry<String, Integer> entry : weights.entrySet()) {
            final String node = entry.getKey();
            final int weight = entry.getValue();

            for (int i = 0; i < weight; i++) {
                final String key = hasher.hash(node + "-" + i);
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

    public Set<String> getNodes(final String key, final int size) throws NoSuchAlgorithmException {
        final int l = size();
        if (l == 0) {
            return new HashSet<>();
        } else if (size > l) {
            return new HashSet<>();
        }

        final Set<String> nodes = new LinkedHashSet<>();
        final String needle = hasher.hash(key);

        Iterator<Map.Entry<String, String>> iterator = ring.tailMap(needle, false).entrySet().iterator();
        while (nodes.size() < size && iterator.hasNext()) {
            nodes.add(iterator.next().getValue());

            if (!iterator.hasNext()) {
                // wrap around when we exhaust the end of the map
                iterator = ring.headMap(needle, true).entrySet().iterator();
            }
        }

        return nodes;
    }
}
