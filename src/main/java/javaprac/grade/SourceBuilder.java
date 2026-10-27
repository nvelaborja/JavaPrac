package javaprac.grade;

import java.util.ArrayList;
import java.util.List;
import javaprac.model.Challenge;

/**
 * Splices the user's code into a self-contained program ({@code Attempt.java}) that runs every case and reports one
 * verdict line, prefixed with {@link #MARKER}, on the real stdout.
 */
final class SourceBuilder {
    static final String MARKER = "@@JP@@";
    static final String CLASS_NAME = "Attempt";

    /** A run of generated lines that came straight from the user's text. */
    record Region(int firstLine, int lineCount) {
        boolean contains(long line) {
            return line >= firstLine && line < firstLine + lineCount;
        }
    }

    record Built(String source, List<Region> userRegions) {
        /** Maps a generated line to the line within the user's text, or -1 if it isn't the user's. */
        int userLine(long generatedLine) {
            for (Region r : userRegions) {
                if (r.contains(generatedLine)) {
                    return (int) (generatedLine - r.firstLine()) + 1;
                }
            }
            return -1;
        }
    }

    private SourceBuilder() {}

    static Built build(Challenge ch, String userCode) {
        StringBuilder sb = new StringBuilder();
        List<Region> regions = new ArrayList<>();
        int cases = ch.cases().size();

        sb.append("""
                import java.io.*;
                import java.math.*;
                import java.nio.charset.StandardCharsets;
                import java.time.*;
                import java.util.*;
                import java.util.function.*;
                import java.util.regex.*;
                import java.util.stream.*;

                public class Attempt {
                    private static final ByteArrayOutputStream OUT = new ByteArrayOutputStream();

                    /** Everything the user's code printed during the current case. */
                    static String out() { return OUT.toString(StandardCharsets.UTF_8); }

                    static void check(boolean ok, String text) { if (!ok) throw new CheckFailed(text); }

                    /** True when {@code r} throws an instance of {@code type}. */
                    static boolean throwsA(Class<? extends Throwable> type, Runnable r) {
                        try {
                            r.run();
                            return false;
                        } catch (Throwable t) {
                            return type.isInstance(t);
                        }
                    }

                    static final class CheckFailed extends RuntimeException {
                        CheckFailed(String text) { super(text, null, false, false); }
                    }

                    public static void main(String[] args) {
                        PrintStream real = System.out;
                        String verdict = "%1$sPASS";
                        for (int c = 0; c < %2$d; c++) {
                            OUT.reset();
                            System.setOut(new PrintStream(OUT, true, StandardCharsets.UTF_8));
                            String label = %2$d > 1 ? "case " + (c + 1) + ": " : "";
                            try {
                                switch (c) {
                """.formatted(MARKER, cases));
        for (int i = 0; i < cases; i++) {
            sb.append("                case ").append(i).append(": case").append(i).append("(); break;\n");
        }
        sb.append("""
                                    default: break;
                                }
                            } catch (CheckFailed f) {
                                verdict = "%1$sFAIL|CHECK|" + label + f.getMessage();
                                break;
                            } catch (Throwable t) {
                                verdict = "%1$sFAIL|EXCEPTION|" + label + t;
                                break;
                            }
                        }
                        System.setOut(real);
                        real.println(verdict.replace('\\n', ' '));
                        real.flush();
                        System.exit(0);
                    }

                """.formatted(MARKER));

        for (int i = 0; i < cases; i++) {
            Challenge.Case c = ch.cases().get(i);
            sb.append("    static void case").append(i).append("() throws Throwable {\n");
            sb.append(ch.givenFor(c)).append('\n');
            appendUserCode(sb, regions, ch.kind(), userCode);
            for (String a : c.asserts()) {
                sb.append("        check(").append(a).append(", ").append(javaString(a)).append(");\n");
            }
            sb.append("    }\n\n");
        }
        sb.append("}\n");

        if (ch.kind() == Challenge.Kind.DECLARATIONS) {
            // Top-level types may not be public in a file named after a different class.
            String declarations = userCode.replaceAll("(?m)^public\\s+", "");
            regions.add(new Region(lineCount(sb) + 1, linesIn(declarations)));
            sb.append(declarations).append('\n');
        }
        return new Built(sb.toString(), regions);
    }

    private static void appendUserCode(StringBuilder sb, List<Region> regions, Challenge.Kind kind, String userCode) {
        switch (kind) {
            case EXPRESSION -> {
                sb.append("        var result = (\n");
                String expr = stripTrailingSemicolons(userCode);
                regions.add(new Region(lineCount(sb) + 1, linesIn(expr)));
                sb.append(expr).append("\n        );\n");
            }
            case STATEMENTS -> {
                regions.add(new Region(lineCount(sb) + 1, linesIn(userCode)));
                sb.append(userCode).append('\n');
            }
            case DECLARATIONS -> {
                // declarations live at the top level instead; nothing goes inside the case method
            }
        }
    }

    static String stripTrailingSemicolons(String code) {
        String s = code.stripTrailing();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).stripTrailing();
        }
        return s;
    }

    /** Lines occupied by text that doesn't end in a newline. */
    private static int linesIn(String text) {
        return lineCount(text) + 1;
    }

    /** Newlines in {@code s}; for the output so far this is the index of the line being written. */
    private static int lineCount(CharSequence s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    static String javaString(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}
