package io.storj.codeowners;

import com.google.common.base.Splitter;
import org.eclipse.jgit.ignore.FastIgnoreRule;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Config {

    private static final Splitter SPLITTER = Splitter.on(' ');

    public static final Pattern REVIEWER_COUNT_PATTERN = Pattern.compile("#\\s*gerrit-codeowners.reviewer-count:\\s*(\\d+)\\s*");

    public static final Pattern USE_GIT_HISTORY = Pattern.compile("#\\s*gerrit-codeowners.use-git-history: true");


    public final List<Rule> rules;
    public final int reviewerCount;
    public boolean useGitHistory;

    public Config(final List<Rule> rules, int assigneNo, boolean useGitHistory) {
        this.rules = rules;
        this.reviewerCount = assigneNo;
        this.useGitHistory = useGitHistory;
    }

    public Set<String> ownersFor(final String path) {
        Set<String> owners = new HashSet<>();

        for (final Rule rule : this.rules) {
            // code reviews typically do not deal with directories...
            if (rule.pattern.isMatch(path, false)) {
                owners.addAll(rule.owners);
            }
        }

        return owners;
    }

    public static Config open(final File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            return parse(new BufferedReader(reader).lines());
        }
    }

    public static Config open(final Reader reader) throws IOException {
        return parse(new BufferedReader(reader).lines());
    }

    public static Config parse(final Stream<String> stream) {
        final List<Rule> rules = new ArrayList<>();
        AtomicInteger assigneeNo = new AtomicInteger(2);
        AtomicBoolean useGitHistory = new AtomicBoolean(false);
        stream
                .filter(Objects::nonNull)
                .forEach((line) -> {
                    final int commentStart = line.lastIndexOf("#");
                    if (commentStart == 0 || (commentStart > 0 && line.charAt(commentStart - 1) != '\\')) {
                        // comment, and comment unescaped

                        String comment = line.substring(commentStart);
                        Matcher matcher = REVIEWER_COUNT_PATTERN.matcher(comment);
                        if (matcher.find()) {
                            assigneeNo.set(Integer.parseInt(matcher.group(1)));
                        }
                        Matcher randomizeMatcher = USE_GIT_HISTORY.matcher(comment);
                        if (randomizeMatcher.find()) {
                            useGitHistory.set(true);
                        }

                        line = line.substring(0, commentStart);
                    }

                    if ("".equals(line)) {
                        return;
                    }

                    final List<String> parts = SPLITTER
                            .splitToStream(line)
                            .filter(Objects::nonNull)
                            .filter((part) -> part.length() > 0)
                            .map((part) -> {
                                // re-insert spaces that were escaped
                                if (part.charAt(part.length() - 1) == '\\') {
                                    part = part + " ";
                                }

                                return part;
                            })
                            .collect(Collectors.toList());

                    rules.add(new Config.Rule(
                            new FastIgnoreRule(parts.get(0)),
                            parts.subList(1, parts.size())
                    ));
                });

        return new Config(rules, assigneeNo.get(), useGitHistory.get());
    }

    public static class Rule {
        public final FastIgnoreRule pattern;
        public final Set<String> owners;

        public Rule(final FastIgnoreRule pattern, final Collection<String> owners) {
            this.pattern = pattern;
            this.owners = new HashSet<>(owners);
        }

        @Override
        public String toString() {
            return "Rule{" +
                    "pattern=" + pattern +
                    ", owners=" + owners +
                    '}';
        }
    }

}
