package com.mjpitz.codeowners;

import com.google.common.base.Splitter;
import org.eclipse.jgit.ignore.FastIgnoreRule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Config {
    public static class Rule {
        public final FastIgnoreRule pattern;
        public final Set<String> owners;

        public Rule(final FastIgnoreRule pattern, final Collection<String> owners) {
            this.pattern = pattern;
            this.owners = new HashSet<>(owners);
        }
    }

    public final List<Rule> rules;

    public Config(final List<Rule> rules) {
        this.rules = rules;
    }

    public Set<String> ownersFor(final String path) {
        Set<String> owners = new HashSet<>();

        for (final Rule rule : this.rules) {
            // code reviews typically do not deal with directories...
            if (rule.pattern.isMatch(path, false)) {
                owners = rule.owners;
            }
        }

        return owners;
    }

    private static final Splitter SPLITTER = Splitter.on(' ');

    public static Config open(final File file) throws IOException {
        return parse(new BufferedReader(new FileReader(file)).lines());
    }

    public static Config parse(final Stream<String> stream) {
        final List<Rule> rules = stream
                .filter(Objects::nonNull)
                .map((line) -> {
                    final int commentStart = line.lastIndexOf("#");
                    if (commentStart == 0 || (commentStart > 0 && line.charAt(commentStart - 1) != '\\')) {
                        // comment, and comment unescaped
                        line = line.substring(0, commentStart);
                    }

                    if ("".equals(line)) {
                        return null;
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

                    return new Config.Rule(
                            new FastIgnoreRule(parts.get(0)),
                            parts.subList(1, parts.size())
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new Config(rules);
    }
}
