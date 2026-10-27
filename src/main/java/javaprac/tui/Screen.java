package javaprac.tui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javaprac.model.Challenge;

/** Pure rendering: turns the current state into one ANSI string, so it can be tested without a terminal. */
public final class Screen {
    public static final int MIN_WIDTH = 60;
    public static final int MIN_HEIGHT = 20;
    private static final int GUTTER = 4;
    private static final String FOOTER = "^D submit   ^K hint   ^R solution   ^N skip   ^Q quit";

    public enum Border { NEUTRAL, FAIL, PASS }

    /** What the play screen shows. {@code feedback} is the last verdict message, {@code hints} those revealed so far. */
    public record Play(Challenge challenge, Editor editor, Duration elapsed, boolean timerStarted, int attempts,
                       List<String> feedback, List<String> hints, boolean grading, Border border,
                       List<String> solution) {}

    public record Summary(Challenge challenge, boolean passed, Duration elapsed, int attempts, int hintsUsed,
                          boolean revealed, String submitted, int solvedThisSession) {}

    /** The rendered frame plus where the real terminal cursor belongs. */
    public record Frame(List<String> rows, int cursorRow, int cursorCol) {
        /** Every row, positioned absolutely: what a terminal needs to show this frame from scratch. */
        public String ansi() {
            return compose(rows, rows.size());
        }
    }

    private int top;
    private int left;

    public Frame renderPlay(Play p, int width, int height) {
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            return tooSmall(width, height);
        }
        Challenge c = p.challenge();
        List<String> rows = new ArrayList<>();

        rows.add(header(p, width));
        rows.add("");
        for (String line : wrap(c.prompt(), width - 4)) {
            rows.add("  " + Ansi.style(Ansi.BOLD, line));
        }
        rows.add("");

        int feedbackCap = 6;
        int solutionCap = 13;
        int givenCap = 6;
        List<String> feedback = feedbackLines(p, width - 4, feedbackCap, solutionCap);
        List<String> given = givenLines(c, width, givenCap);
        int bodyHeight = height - (rows.size() + given.size() + 2 + feedback.size() + 1);
        if (bodyHeight < 5) {
            feedback = feedbackLines(p, width - 4, 3, 6);
            given = givenLines(c, width, 3);
            bodyHeight = height - (rows.size() + given.size() + 2 + feedback.size() + 1);
        }
        if (bodyHeight < 3) {
            return tooSmall(width, height);
        }
        rows.addAll(given);

        String borderColor = switch (p.border()) {
            case FAIL -> Ansi.BOLD + Ansi.RED;
            case PASS -> Ansi.BOLD + Ansi.GREEN;
            case NEUTRAL -> Ansi.GRAY;
        };
        String title = " " + boxTitle(c.kind()) + " ";
        rows.add(Ansi.style(borderColor, "┌─" + title + "─".repeat(Math.max(0, width - 3 - title.length())) + "┐"));
        int bodyStartRow = rows.size() + 1;

        Editor ed = p.editor();
        int contentWidth = width - 2 - GUTTER;
        scrollToCursor(ed, bodyHeight, contentWidth);
        for (int i = 0; i < bodyHeight; i++) {
            rows.add(editorRow(ed, top + i, contentWidth, borderColor));
        }
        rows.add(Ansi.style(borderColor, "└" + "─".repeat(width - 2) + "┘"));
        rows.addAll(feedback);

        while (rows.size() < height - 1) {
            rows.add("");
        }
        rows.add(Ansi.style(Ansi.DIM, " " + FOOTER));

        int cursorRow = bodyStartRow + (ed.row() - top);
        int cursorCol = 2 + GUTTER + (ed.col() - left);
        return new Frame(List.copyOf(rows.subList(0, Math.min(rows.size(), height))), cursorRow, cursorCol);
    }

    public String renderSummary(Summary s, int width, int height) {
        List<String> rows = new ArrayList<>();
        rows.add("");
        rows.add(s.passed()
                ? Ansi.style(Ansi.BOLD + Ansi.GREEN, "  ✓ Passed: " + s.challenge().title())
                : Ansi.style(Ansi.BOLD + Ansi.YELLOW, "  ↷ Skipped: " + s.challenge().title()));
        rows.add(Ansi.style(Ansi.GRAY, "  " + clock(s.elapsed()) + "   attempts " + s.attempts() + "   hints " + s.hintsUsed()
                + (s.revealed() ? "   solution revealed" : "") + "   solved this session " + s.solvedThisSession()));
        rows.add("");
        rows.add(Ansi.style(Ansi.BOLD, "  Reference solution"));
        for (String line : s.challenge().reference().split("\n", -1)) {
            rows.add("    " + Ansi.style(Ansi.CYAN, fit(line, width - 6)));
        }
        if (s.passed() && !s.submitted().strip().equals(s.challenge().reference().strip())) {
            rows.add("");
            rows.add(Ansi.style(Ansi.BOLD, "  Your solution"));
            for (String line : s.submitted().split("\n", -1)) {
                rows.add("    " + fit(line, width - 6));
            }
        }
        while (rows.size() < height - 1) {
            rows.add("");
        }
        rows.add(Ansi.style(Ansi.DIM, "  Enter next   Q quit"));
        return Ansi.HIDE_CURSOR + compose(rows, height);
    }

    static String clock(Duration d) {
        long s = d.toSeconds();
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private String header(Play p, int width) {
        Challenge c = p.challenge();
        String stars = "★".repeat(c.difficulty()) + "☆".repeat(Math.max(0, 3 - c.difficulty()));
        String left = " JavaPrac · " + c.title() + " · " + String.join(", ", c.tags()) + " · " + stars;
        String timer = p.timerStarted() ? clock(p.elapsed()) : "--:--";
        String right = "⏱ " + timer + "   tries " + p.attempts() + " ";
        int gap = Math.max(1, width - left.length() - right.length());
        String line = fit(left + " ".repeat(gap) + right, width);
        return Ansi.style(Ansi.BOLD + Ansi.CYAN, line);
    }

    /** Tells the player what shape of answer is expected, since the snippet is spliced into a generated program. */
    static String boxTitle(Challenge.Kind kind) {
        return switch (kind) {
            case EXPRESSION -> "Your expression (value is checked; no return)";
            case STATEMENTS -> "Your statements";
            case DECLARATIONS -> "Your declarations (top-level types)";
        };
    }

    private static List<String> givenLines(Challenge c, int width, int cap) {
        List<String> out = new ArrayList<>();
        if (c.given().isBlank()) {
            return out;
        }
        out.add(Ansi.style(Ansi.DIM, "  Given"));
        String[] lines = c.given().split("\n", -1);
        for (int i = 0; i < Math.min(lines.length, cap); i++) {
            out.add("    " + Ansi.style(Ansi.CYAN, fit(lines[i], width - 6)));
        }
        if (lines.length > cap) {
            out.add(Ansi.style(Ansi.DIM, "    … " + (lines.length - cap) + " more lines"));
        }
        out.add("");
        return out;
    }

    /** Errors and hints (at most {@code statusCap} lines), then the revealed solution if any (at most {@code solutionCap}). */
    private static List<String> feedbackLines(Play p, int width, int statusCap, int solutionCap) {
        if (p.grading()) {
            return List.of(Ansi.style(Ansi.DIM, "  … compiling and running"));
        }
        List<String> out = statusLines(p, width, statusCap);
        if (!p.solution().isEmpty()) {
            out.addAll(solutionLines(p, width, solutionCap));
        }
        return out;
    }

    private static List<String> solutionLines(Play p, int width, int cap) {
        List<String> out = new ArrayList<>();
        out.add(Ansi.style(Ansi.BOLD + Ansi.YELLOW, "  Solution (type it in to finish; ^R hides it)"));
        for (String line : p.solution()) {
            out.add("    " + Ansi.style(Ansi.CYAN, fit(line, width - 2)));
        }
        if (out.size() > cap) {
            out = new ArrayList<>(out.subList(0, cap - 1));
            out.add(Ansi.style(Ansi.DIM, "    … more lines; ^N skip shows the full solution"));
        }
        return out;
    }

    /** Errors then hints. When space is short the errors shrink first, because the hint is what unblocks the player. */
    private static List<String> statusLines(Play p, int width, int cap) {
        List<String> errors = errorLines(p, width);
        List<String> hints = hintLines(p, width);
        int errorBudget = Math.max(2, cap - hints.size());
        if (errors.size() > errorBudget) {
            errors = new ArrayList<>(errors.subList(0, errorBudget));
        }
        List<String> out = new ArrayList<>(errors);
        out.addAll(hints);
        return out.size() > cap + 2 ? new ArrayList<>(out.subList(0, cap + 2)) : out;
    }

    private static List<String> errorLines(Play p, int width) {
        List<String> out = new ArrayList<>();
        for (String message : p.feedback()) {
            if (message.startsWith(" ")) {
                // preformatted (a source line or caret): wrapping would misalign the caret
                out.add("  " + Ansi.style(Ansi.RED, fit(message, width - 2)));
                continue;
            }
            List<String> wrapped = wrap(message, width - 2);
            for (int i = 0; i < wrapped.size(); i++) {
                out.add("  " + Ansi.style(Ansi.RED, (i == 0 ? "✗ " : "  ") + wrapped.get(i)));
            }
        }
        return out;
    }

    private static List<String> hintLines(Play p, int width) {
        List<String> out = new ArrayList<>();
        for (int h = 0; h < p.hints().size(); h++) {
            List<String> wrapped = wrap("Hint " + (h + 1) + ": " + p.hints().get(h), width - 2);
            for (int i = 0; i < wrapped.size(); i++) {
                out.add("  " + Ansi.style(Ansi.YELLOW, (i == 0 ? "💡 " : "   ") + wrapped.get(i)));
            }
        }
        return out;
    }

    private String editorRow(Editor ed, int index, int contentWidth, String borderColor) {
        String vertical = Ansi.style(borderColor, "│");
        String number = index < ed.lineCount() ? String.format("%3d ", index + 1) : "    ";
        String text = "";
        if (index < ed.lineCount()) {
            String line = ed.line(index);
            int from = Math.min(left, line.length());
            text = line.substring(from, Math.min(line.length(), from + contentWidth));
        }
        return vertical + Ansi.style(Ansi.GRAY, number) + pad(text, contentWidth) + vertical;
    }

    private void scrollToCursor(Editor ed, int bodyHeight, int contentWidth) {
        if (ed.row() < top) {
            top = ed.row();
        } else if (ed.row() >= top + bodyHeight) {
            top = ed.row() - bodyHeight + 1;
        }
        if (ed.col() < left) {
            left = ed.col();
        } else if (ed.col() >= left + contentWidth) {
            left = ed.col() - contentWidth + 1;
        }
    }

    private static Frame tooSmall(int width, int height) {
        String msg = "Terminal too small (" + width + "x" + height + "). Need at least " + MIN_WIDTH + "x" + MIN_HEIGHT + ".";
        List<String> rows = new ArrayList<>();
        rows.add(msg);
        while (rows.size() < height) {
            rows.add("");
        }
        return new Frame(List.copyOf(rows), 1, 1);
    }

    static String compose(List<String> rows, int height) {
        StringBuilder sb = new StringBuilder(Ansi.HIDE_CURSOR);
        for (int i = 0; i < Math.min(rows.size(), height); i++) {
            sb.append(Ansi.moveTo(i + 1, 1)).append(rows.get(i)).append(Ansi.CLEAR_TO_EOL);
        }
        return sb.toString();
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                while (word.length() > width) {
                    if (!line.isEmpty()) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    out.add(word.substring(0, width));
                    word = word.substring(width);
                }
                if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
            out.add(line.toString());
        }
        return out;
    }

    private static String fit(String s, int width) {
        return s.length() > width ? s.substring(0, Math.max(0, width)) : s;
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }
}
