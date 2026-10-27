package javaprac.tui;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javaprac.grade.Grader;
import javaprac.grade.Verdict;
import javaprac.model.Challenge;
import javaprac.store.History;
import javaprac.tui.KeyDecoder.Key;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

/** The interactive loop: show a challenge, edit, submit, buzz or celebrate, repeat. */
public final class Tui {
    private static final long TICK_MS = 250;
    /** While keys keep arriving (a held arrow, a paste) redraw at most this often, roughly 30 frames a second. */
    private static final long MIN_FRAME_MS = 33;
    private static final long FLASH_MS = 1200;
    /** Reveal one more hint after every this many failed attempts. */
    private static final int FAILURES_PER_HINT = 2;

    public record SessionStats(int solved, int skipped, Duration solveTime) {}

    private enum Next { CONTINUE, QUIT }

    private final Terminal terminal;
    private final PrintWriter out;
    private final KeyPump keys;
    private final Grader grader;
    private final Sounds sounds;
    private final History history;
    private int solved;
    private int skipped;
    private Duration solveTime = Duration.ZERO;
    private int lastWidth;
    private int lastHeight;

    public Tui(Terminal terminal, Grader grader, History history, boolean sound) {
        this.terminal = terminal;
        this.out = terminal.writer();
        NonBlockingReader reader = terminal.reader();
        this.keys = new KeyPump(new KeyDecoder(new KeyDecoder.Source() {
            @Override
            public int read() throws IOException {
                return reader.read();
            }

            @Override
            public int peek(long timeoutMs) throws IOException {
                return reader.peek(timeoutMs);
            }
        }));
        this.grader = grader;
        this.history = history;
        this.sounds = new Sounds(sound, out);
    }

    /** Plays up to {@code count} challenges (or until the user quits) and returns what happened. */
    public SessionStats run(Selector selector, int count) throws IOException {
        out.print(Ansi.ALT_SCREEN_ON + Ansi.ESC + "2J");
        out.flush();
        try {
            for (int i = 0; i < count; i++) {
                if (play(selector.next()) == Next.QUIT) {
                    break;
                }
            }
        } finally {
            out.print(Ansi.SHOW_CURSOR + Ansi.ALT_SCREEN_OFF);
            out.flush();
        }
        return new SessionStats(solved, skipped, solveTime);
    }

    private Next play(Challenge challenge) throws IOException {
        Editor editor = new Editor(challenge.starter());
        Screen screen = new Screen();
        int attempts = 0;
        int hintsShown = 0;
        List<String> feedback = List.of();
        boolean revealed = false;
        boolean showSolution = false;
        Instant startedAt = null;
        Instant flashUntil = Instant.MIN;
        boolean dirty = true;
        long lastDrawNanos = 0;

        while (true) {
            if (screenResized()) {
                dirty = true;
            }
            boolean frameDue = (System.nanoTime() - lastDrawNanos) / 1_000_000 >= MIN_FRAME_MS;
            // draw at once when idle; only hold a frame back while more keys are already waiting and one was just drawn
            if (dirty && (frameDue || !keys.hasPending())) {
                Instant now = Instant.now();
                Duration elapsed = startedAt == null ? Duration.ZERO : Duration.between(startedAt, now);
                Screen.Border border = now.isBefore(flashUntil) ? Screen.Border.FAIL : Screen.Border.NEUTRAL;
                draw(screen, new Screen.Play(challenge, editor, elapsed, startedAt != null, attempts, feedback,
                        challenge.hints().subList(0, hintsShown), false, border, solutionLines(challenge, showSolution)));
                dirty = false;
                lastDrawNanos = System.nanoTime();
            }
            Key key = keys.poll(dirty ? 1 : TICK_MS);
            if (key == null) {
                dirty = true; // the tick keeps the clock moving and clears the red flash
                continue;
            }
            boolean realKeystroke = switch (key.type()) {
                case ESCAPE, UNKNOWN, HINT, REVEAL -> false;
                default -> true;
            };
            if (startedAt == null && realKeystroke) {
                startedAt = Instant.now();
            }
            switch (key.type()) {
                case CHAR -> editor.insert(new String(Character.toChars(key.ch())));
                case ENTER -> editor.newline();
                case TAB -> editor.insert("\t");
                case BACKSPACE -> editor.backspace();
                case DELETE -> editor.delete();
                case LEFT -> editor.left();
                case RIGHT -> editor.right();
                case UP -> editor.up();
                case DOWN -> editor.down();
                case HOME -> editor.home();
                case END -> editor.end();
                case HINT -> hintsShown = Math.min(challenge.hints().size(), hintsShown + 1);
                case REVEAL -> {
                    revealed = true;
                    showSolution = !showSolution;
                }
                case QUIT -> {
                    return Next.QUIT;
                }
                case SKIP -> {
                    skipped++;
                    record(challenge, false, startedAt, attempts, hintsShown, true);
                    return summary(challenge, false, startedAt, attempts, hintsShown, true, editor.text());
                }
                case SUBMIT -> {
                    attempts++;
                    draw(screen, new Screen.Play(challenge, editor, elapsedSince(startedAt), true, attempts, List.of(),
                            challenge.hints().subList(0, hintsShown), true, Screen.Border.NEUTRAL, List.of()));
                    Instant gradingStarted = Instant.now();
                    Verdict verdict = grader.grade(challenge, editor.text());
                    // the clock measures the player, not javac and the sandbox JVM
                    startedAt = startedAt.plus(Duration.between(gradingStarted, Instant.now()));
                    if (verdict.passed()) {
                        solved++;
                        solveTime = solveTime.plus(elapsedSince(startedAt));
                        sounds.pass();
                        record(challenge, true, startedAt, attempts, hintsShown, revealed);
                        return summary(challenge, true, startedAt, attempts, hintsShown, revealed, editor.text());
                    }
                    feedback = List.of(verdict.message().split("\n"));
                    flashUntil = Instant.now().plusMillis(FLASH_MS);
                    sounds.fail();
                    hintsShown = Math.max(hintsShown, Math.min(challenge.hints().size(), attempts / FAILURES_PER_HINT));
                }
                default -> { }
            }
            dirty = true;
        }
    }

    private Next summary(Challenge challenge, boolean passed, Instant startedAt, int attempts, int hints,
                         boolean revealed, String submitted) throws IOException {
        String frame = new Screen().renderSummary(
                new Screen.Summary(challenge, passed, elapsedSince(startedAt), attempts, hints, revealed, submitted, solved),
                width(), height());
        out.print(Ansi.ESC + "2J" + frame);
        out.flush();
        while (true) {
            Key key = keys.take();
            if (key.type() == KeyDecoder.Type.ENTER) {
                out.print(Ansi.ESC + "2J");
                return Next.CONTINUE;
            }
            if (key.type() == KeyDecoder.Type.QUIT || (key.type() == KeyDecoder.Type.CHAR && (key.ch() == 'q' || key.ch() == 'Q'))) {
                return Next.QUIT;
            }
        }
    }

    private static List<String> solutionLines(Challenge challenge, boolean show) {
        return show ? List.of(challenge.reference().split("\n")) : List.of();
    }

    private void record(Challenge challenge, boolean passed, Instant startedAt, int attempts, int hints, boolean revealed) {
        history.append(new History.Entry(Instant.now(), challenge.id(), passed, elapsedSince(startedAt).toSeconds(),
                attempts, hints, revealed));
    }

    private void draw(Screen screen, Screen.Play play) {
        Screen.Frame frame = screen.renderPlay(play, width(), height());
        out.print(frame.ansi() + Ansi.moveTo(frame.cursorRow(), frame.cursorCol()) + Ansi.SHOW_CURSOR);
        out.flush();
    }

    /** True (and clears the screen) when the terminal changed size since the last check. */
    private boolean screenResized() {
        if (width() == lastWidth && height() == lastHeight) {
            return false;
        }
        boolean first = lastWidth == 0;
        lastWidth = width();
        lastHeight = height();
        if (!first) {
            out.print(Ansi.ESC + "2J");
        }
        return true;
    }

    private static Duration elapsedSince(Instant start) {
        return start == null ? Duration.ZERO : Duration.between(start, Instant.now());
    }

    private int width() {
        Size size = terminal.getSize();
        return size.getColumns() > 0 ? size.getColumns() : 80;
    }

    private int height() {
        Size size = terminal.getSize();
        return size.getRows() > 0 ? size.getRows() : 24;
    }
}
