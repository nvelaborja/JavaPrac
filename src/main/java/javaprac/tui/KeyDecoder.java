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
        WORD_LEFT, WORD_RIGHT, DELETE_WORD_BACK, DELETE_WORD_FORWARD, DELETE_BLANK_WORD_BACK,
        DELETE_TO_LINE_START, DELETE_TO_LINE_END,
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
            case 2 -> Key.of(Type.LEFT);      // Ctrl-B
            case 6 -> Key.of(Type.RIGHT);     // Ctrl-F
            case 23 -> Key.of(Type.DELETE_BLANK_WORD_BACK);  // Ctrl-W
            case 21 -> Key.of(Type.DELETE_TO_LINE_START);    // Ctrl-U (also Cmd-Backspace in iTerm2)
            case 11 -> Key.of(Type.DELETE_TO_LINE_END);      // Ctrl-K
            case 4 -> Key.of(Type.SUBMIT);    // Ctrl-D
            case 7 -> Key.of(Type.HINT);      // Ctrl-G
            case 18 -> Key.of(Type.REVEAL);   // Ctrl-R
            case 14 -> Key.of(Type.SKIP);     // Ctrl-N
            case 3, 17 -> Key.of(Type.QUIT);  // Ctrl-C, Ctrl-Q
            default -> c >= 32 ? new Key(Type.CHAR, c) : Key.of(Type.UNKNOWN);
        };
    }

    private Key escape() throws IOException {
        int introducer = source.peek(ESCAPE_SEQUENCE_WAIT_MS);
        if (introducer == TIMED_OUT || introducer == EOF) {
            return Key.of(Type.ESCAPE);
        }
        if (introducer != '[' && introducer != 'O') {
            source.read();
            return meta(introducer);
        }
        source.read();
        StringBuilder params = new StringBuilder();
        int c = source.read();
        while (c >= 0 && (c < 0x40 || c > 0x7E)) {
            params.append((char) c);
            c = source.read();
        }
        int modifier = modifier(params.toString());
        return switch (c) {
            case 'A' -> Key.of(Type.UP);
            case 'B' -> Key.of(Type.DOWN);
            case 'C' -> horizontal(modifier, Type.RIGHT, Type.WORD_RIGHT, Type.END);
            case 'D' -> horizontal(modifier, Type.LEFT, Type.WORD_LEFT, Type.HOME);
            case 'H' -> Key.of(Type.HOME);
            case 'F' -> Key.of(Type.END);
            case '~' -> tilde(params.toString(), modifier);
            default -> Key.of(Type.UNKNOWN);
        };
    }

    /** Meta (Option or Alt) plus a letter arrives as ESC then the letter, as in readline. */
    private static Key meta(int c) {
        return switch (c) {
            case 'b' -> Key.of(Type.WORD_LEFT);
            case 'f' -> Key.of(Type.WORD_RIGHT);
            case 'd' -> Key.of(Type.DELETE_WORD_FORWARD);
            case 127, 8 -> Key.of(Type.DELETE_WORD_BACK);   // Option-Backspace
            default -> Key.of(Type.UNKNOWN);
        };
    }

    /**
     * xterm's modifier parameter: 2 Shift, 3 Alt, 4 Shift+Alt, 5 Ctrl, 6 Ctrl+Shift, 7 Ctrl+Alt, 8 all three, 9 Meta
     * (Cmd on some terminals). Absent means unmodified (1).
     */
    private static int modifier(String params) {
        int semicolon = params.lastIndexOf(';');
        if (semicolon < 0) {
            return 1;
        }
        try {
            return Integer.parseInt(params.substring(semicolon + 1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Alt or Ctrl moves a word, Meta (Cmd) moves to the line edge, anything else is a plain arrow. */
    private static Key horizontal(int modifier, Type plain, Type word, Type edge) {
        if (modifier >= 3 && modifier <= 8) {
            return Key.of(word);
        }
        return Key.of(modifier == 9 || modifier == 10 ? edge : plain);
    }

    private static Key tilde(String params, int modifier) {
        String code = params.contains(";") ? params.substring(0, params.indexOf(';')) : params;
        return switch (code) {
            case "3" -> Key.of(modifier >= 3 ? Type.DELETE_WORD_FORWARD : Type.DELETE);
            case "1", "7" -> Key.of(Type.HOME);
            case "4", "8" -> Key.of(Type.END);
            default -> Key.of(Type.UNKNOWN);
        };
    }
}
