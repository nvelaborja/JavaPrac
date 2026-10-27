package javaprac;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import javaprac.grade.Grader;
import javaprac.grade.Verifier;
import javaprac.model.Challenge;
import javaprac.model.ChallengeLoader;
import javaprac.store.History;
import javaprac.tui.Selector;
import javaprac.tui.Tui;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public final class Main {
    private static final String USAGE = """
            JavaPrac: practice Java syntax with timed, compiler-graded challenges.

            Usage:
              javaprac [play] [--tag T]... [--max-difficulty N] [--id ID] [--count N] [--mute]
              javaprac list [--tag T]...     list challenges
              javaprac stats                 your history by topic
              javaprac verify                run every reference solution through the grader

            In the editor:  ^D submit   ^K hint   ^N skip   ^Q quit   (arrows, Home/End, Tab = 4 spaces)
            """;

    private Main() {}

    public static void main(String[] args) throws IOException {
        List<String> rest = new ArrayList<>(List.of(args));
        String command = !rest.isEmpty() && !rest.getFirst().startsWith("-") ? rest.removeFirst() : "play";
        int exit;
        try {
            exit = switch (command) {
                case "play" -> play(Options.parse(rest));
                case "list" -> list(Options.parse(rest));
                case "stats" -> stats();
                case "verify" -> verify();
                case "help" -> usage(0);
                default -> usage(2);
            };
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            exit = usage(2);
        }
        System.exit(exit);
    }

    private record Options(List<String> tags, int maxDifficulty, String id, int count, boolean mute) {
        static Options parse(List<String> args) {
            List<String> tags = new ArrayList<>();
            int maxDifficulty = 3;
            String id = null;
            int count = Integer.MAX_VALUE;
            boolean mute = false;
            for (int i = 0; i < args.size(); i++) {
                switch (args.get(i)) {
                    case "--tag" -> tags.add(value(args, ++i));
                    case "--max-difficulty" -> maxDifficulty = Integer.parseInt(value(args, ++i));
                    case "--id" -> id = value(args, ++i);
                    case "--count" -> count = Integer.parseInt(value(args, ++i));
                    case "--mute" -> mute = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + args.get(i));
                }
            }
            return new Options(tags, maxDifficulty, id, count, mute);
        }

        private static String value(List<String> args, int index) {
            if (index >= args.size()) {
                throw new IllegalArgumentException("Missing value for " + args.get(index - 1));
            }
            return args.get(index);
        }

        /** A tag matches either one of the challenge's tags or its topic (the id prefix, e.g. {@code streams}). */
        List<Challenge> filter(List<Challenge> all) {
            return all.stream()
                    .filter(c -> id == null || c.id().equals(id))
                    .filter(c -> tags.isEmpty() || c.tags().stream().anyMatch(tags::contains) || tags.contains(topic(c)))
                    .filter(c -> c.difficulty() <= maxDifficulty)
                    .toList();
        }
    }

    private static String topic(Challenge c) {
        return c.id().substring(0, c.id().indexOf('.'));
    }

    private static int usage(int code) {
        (code == 0 ? System.out : System.err).print(USAGE);
        return code;
    }

    private static int play(Options options) throws IOException {
        if (System.console() == null) {
            System.err.println("JavaPrac needs an interactive terminal.");
            return 2;
        }
        List<Challenge> pool = options.filter(ChallengeLoader.loadBundled());
        if (pool.isEmpty()) {
            System.err.println("No challenges match those filters. Try `javaprac list`.");
            return 2;
        }
        Tui.SessionStats stats;
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            Attributes saved = terminal.enterRawMode();
            try {
                Tui tui = new Tui(terminal, new Grader(), new History(History.defaultFile()), !options.mute());
                stats = tui.run(new Selector(pool, new Random()), options.count());
            } finally {
                terminal.setAttributes(saved);
            }
        }
        System.out.println("Solved " + stats.solved() + ", skipped " + stats.skipped()
                + (stats.solved() > 0 ? ", average " + stats.solveTime().dividedBy(stats.solved()).toSeconds() + "s per solve" : "") + ".");
        return 0;
    }

    private static int list(Options options) {
        for (Challenge c : options.filter(ChallengeLoader.loadBundled())) {
            System.out.printf("%-38s %s  %s%n", c.id(), "★".repeat(c.difficulty()), c.title());
        }
        return 0;
    }

    private static int stats() {
        List<History.Entry> entries = new History(History.defaultFile()).readAll();
        if (entries.isEmpty()) {
            System.out.println("No history yet. Play a few challenges first.");
            return 0;
        }
        Map<String, List<History.Entry>> byTopic = new TreeMap<>();
        entries.forEach(e -> byTopic.computeIfAbsent(e.topic(), k -> new ArrayList<>()).add(e));
        System.out.printf("%-14s %6s %6s %10s %9s%n", "topic", "tried", "solved", "avg time", "avg tries");
        byTopic.forEach((topic, list) -> {
            List<History.Entry> solved = list.stream().filter(History.Entry::passed).toList();
            Duration avg = Duration.ofSeconds((long) solved.stream().mapToLong(History.Entry::seconds).average().orElse(0));
            double avgTries = solved.stream().mapToInt(History.Entry::attempts).average().orElse(0);
            System.out.printf("%-14s %6d %6d %10s %9.1f%n", topic, list.size(), solved.size(),
                    avg.toMinutesPart() + "m" + avg.toSecondsPart() + "s", avgTries);
        });
        return 0;
    }

    private static int verify() {
        Verifier verifier = new Verifier(new Grader());
        List<Challenge> all = ChallengeLoader.loadBundled();
        int failures = 0;
        for (Challenge c : all) {
            Verifier.Result result = verifier.verify(c);
            System.out.println((result.ok() ? "ok    " : "FAIL  ") + c.id());
            result.problems().forEach(p -> System.out.println("        " + p));
            failures += result.ok() ? 0 : 1;
        }
        System.out.println(all.size() - failures + "/" + all.size() + " challenges verified");
        return failures == 0 ? 0 : 1;
    }
}
