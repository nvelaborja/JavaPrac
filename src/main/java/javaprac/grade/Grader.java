package javaprac.grade;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javaprac.model.Challenge;

/**
 * Grades a submission in three stages: compile it with the real {@code javac}, check its syntax-tree structure, then
 * run it in a child JVM (so {@code System.exit} and infinite loops can't take the TUI down) and read back a verdict.
 */
public final class Grader {
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(5);

    public Verdict grade(Challenge challenge, String userCode) {
        if (userCode.isBlank()) {
            return new Verdict(Verdict.Outcome.COMPILE_ERROR, "Nothing to grade yet. Write some code first.");
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("javaprac-");
            SourceBuilder.Built built = SourceBuilder.build(challenge, userCode);
            Path file = dir.resolve(SourceBuilder.CLASS_NAME + ".java");
            Files.writeString(file, built.source(), StandardCharsets.UTF_8);

            Verdict compileError = compile(file, dir, built);
            if (compileError != null) {
                return compileError;
            }
            Verdict structureError = StructureChecker.check(challenge, userCode);
            if (structureError != null) {
                return structureError;
            }
            return run(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private static Verdict compile(Path file, Path outDir, SourceBuilder.Built built) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler found. Run JavaPrac on a JDK, not a JRE.");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            ok = compiler.getTask(null, fm, diagnostics,
                    List.of("-d", outDir.toString(), "-proc:none", "-Xlint:none"), null, fm.getJavaFileObjects(file)).call();
        }
        if (ok) {
            return null;
        }
        List<String> messages = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            String description = describe(d.getMessage(Locale.ENGLISH));
            int userLine = built.userLine(d.getLineNumber());
            messages.add(userLine > 0
                    ? "line " + userLine + ", col " + d.getColumnNumber() + ": " + description + "\n" + sourceWithCaret(built, d)
                    : "your code doesn't fit what this challenge expects (" + description + ")");
            if (messages.size() == 3) {
                break;
            }
        }
        return new Verdict(Verdict.Outcome.COMPILE_ERROR, String.join("\n", messages));
    }

    /** javac's first line, plus the offending symbol and where it was looked up, e.g. "cannot find symbol: class range". */
    static String describe(String javacMessage) {
        List<String> lines = javacMessage.lines().toList();
        StringBuilder out = new StringBuilder(lines.isEmpty() ? "compile error" : lines.getFirst());
        lines.stream().filter(l -> l.strip().startsWith("symbol:")).findFirst()
                .ifPresent(l -> out.append(": ").append(l.strip().substring("symbol:".length()).strip()));
        lines.stream().filter(l -> l.strip().startsWith("location:")).findFirst()
                .ifPresent(l -> out.append(" in ").append(l.strip().substring("location:".length()).strip()));
        return out.toString();
    }

    /** The user's line indented two spaces, then a caret under the error column. */
    private static String sourceWithCaret(SourceBuilder.Built built, Diagnostic<? extends JavaFileObject> d) {
        String line = built.source().lines().skip(d.getLineNumber() - 1).findFirst().orElse("");
        return "  " + line + "\n  " + " ".repeat((int) Math.max(0, d.getColumnNumber() - 1)) + "^";
    }

    private static Verdict run(Path dir) throws IOException {
        Path stdout = dir.resolve("stdout.txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Xmx256m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                "-cp", dir.toString(), SourceBuilder.CLASS_NAME)
                .redirectOutput(stdout.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        try {
            if (!process.waitFor(RUN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new Verdict(Verdict.Outcome.TIMEOUT,
                        "Your code ran for more than " + RUN_TIMEOUT.toSeconds() + " seconds. Is there an infinite loop?");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while grading", e);
        }
        return parseVerdict(Files.readAllLines(stdout, StandardCharsets.UTF_8));
    }

    static Verdict parseVerdict(List<String> lines) {
        String line = lines.reversed().stream().filter(l -> l.startsWith(SourceBuilder.MARKER)).findFirst().orElse(null);
        if (line == null) {
            return new Verdict(Verdict.Outcome.RUNTIME_ERROR, "The program ended before it finished (did it call System.exit?).");
        }
        String body = line.substring(SourceBuilder.MARKER.length());
        if (body.equals("PASS")) {
            return Verdict.pass();
        }
        String[] parts = body.split("\\|", 3);
        if (parts.length == 3 && parts[1].equals("CHECK")) {
            return new Verdict(Verdict.Outcome.CHECK_FAILED, "Check failed: " + parts[2]);
        }
        if (parts.length == 3) {
            return new Verdict(Verdict.Outcome.RUNTIME_ERROR, "Your code threw: " + parts[2]);
        }
        return new Verdict(Verdict.Outcome.RUNTIME_ERROR, "Unreadable result from the sandbox: " + body);
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp directory cleanup is best effort
        }
    }
}
