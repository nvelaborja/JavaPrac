package javaprac.tui;

import java.io.IOException;

/** Turns raw terminal bytes (including ANSI escape sequences) into {@link Key}s. */
public final class KeyDecoder {
    /** Returned by {@link Source#peek} when nothing arrives in time. */
    public static final int TIMED_OUT = -2;
    public static final int EOF = -1;

    private static final long ESCAPE_SEQUENCE_WAIT_MS = 40;

    public enum Type {
        CHAR, ENTER, BACKSPACE, DELETE, TAB, UP, DOWN, LEFT, RIGHT, HOME, END,
        SUBMIT, HINT, REVEAL, SKIP, QUIT, ESCAPE, UNKNOWN
    }

    public record Key(Type type, int ch) {
        static Key of(Type type) {
            return new Key(type, 0);
        }
    }

    /** A source of terminal input; {@code peek} waits up to the timeout without consuming. */
    public interface Source {
        int read() throws IOException;

        int peek(long timeoutMs) throws IOException;
    }

    private final Source source;

    public KeyDecoder(Source source) {
        this.source = source;
    }

    public Key next() throws IOException {
        int c = source.read();
        return switch (c) {
            case EOF -> Key.of(Type.QUIT);
            case 27 -> escape();
            case 13, 10 -> Key.of(Type.ENTER);
            case 127, 8 -> Key.of(Type.BACKSPACE);
            case 9 -> Key.of(Type.TAB);
            case 1 -> Key.of(Type.HOME);      // Ctrl-A
            case 5 -> Key.of(Type.END);       // Ctrl-E
            case 4 -> Key.of(Type.SUBMIT);    // Ctrl-D
            case 11 -> Key.of(Type.HINT);     // Ctrl-K
            case 18 -> Key.of(Type.REVEAL);   // Ctrl-R
            case 14 -> Key.of(Type.SKIP);     // Ctrl-N
            case 3, 17 -> Key.of(Type.QUIT);  // Ctrl-C, Ctrl-Q
            default -> c >= 32 ? new Key(Type.CHAR, c) : Key.of(Type.UNKNOWN);
        };
    }

    private Key escape() throws IOException {
        int introducer = source.peek(ESCAPE_SEQUENCE_WAIT_MS);
        if (introducer != '[' && introducer != 'O') {
            return Key.of(Type.ESCAPE);
        }
        source.read();
        StringBuilder params = new StringBuilder();
        int c = source.read();
        while (c >= 0 && (c < 0x40 || c > 0x7E)) {
            params.append((char) c);
            c = source.read();
        }
        return switch (c) {
            case 'A' -> Key.of(Type.UP);
            case 'B' -> Key.of(Type.DOWN);
            case 'C' -> Key.of(Type.RIGHT);
            case 'D' -> Key.of(Type.LEFT);
            case 'H' -> Key.of(Type.HOME);
            case 'F' -> Key.of(Type.END);
            case '~' -> tilde(params.toString());
            default -> Key.of(Type.UNKNOWN);
        };
    }

    private static Key tilde(String params) {
        return switch (params) {
            case "3" -> Key.of(Type.DELETE);
            case "1", "7" -> Key.of(Type.HOME);
            case "4", "8" -> Key.of(Type.END);
            default -> Key.of(Type.UNKNOWN);
        };
    }
}
