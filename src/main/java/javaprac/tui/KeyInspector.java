package javaprac.tui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javaprac.tui.KeyDecoder.Key;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

/**
 * Shows exactly which bytes a terminal sends for each keypress and how JavaPrac reads them. Terminals disagree about
 * what Option, Alt, Ctrl and Cmd plus an arrow send, and often have a setting that changes it, so this is how to find out.
 */
public final class KeyInspector {
    private KeyInspector() {}

    /** Runs interactively until Ctrl-Q. The terminal must already be in raw mode. */
    public static void run(Terminal terminal) throws IOException {
        PrintWriter out = terminal.writer();
        NonBlockingReader reader = terminal.reader();
        out.print("Press keys to see what your terminal sends. Ctrl-Q quits.\r\n\r\n");
        out.flush();
        while (true) {
            List<Integer> bytes = new ArrayList<>();
            bytes.add(reader.read());
            // the bytes of one keypress arrive together; a pause means the next key
            while (reader.peek(40) >= 0) {
                bytes.add(reader.read());
            }
            Key key = decode(bytes);
            out.print(describe(bytes, key) + "\r\n");
            out.flush();
            if (key.type() == KeyDecoder.Type.QUIT) {
                return;
            }
        }
    }

    static Key decode(List<Integer> bytes) {
        List<Integer> remaining = new ArrayList<>(bytes);
        try {
            return new KeyDecoder(new KeyDecoder.Source() {
                @Override
                public int read() {
                    return remaining.isEmpty() ? KeyDecoder.EOF : remaining.removeFirst();
                }

                @Override
                public int peek(long timeoutMs) {
                    return remaining.isEmpty() ? KeyDecoder.TIMED_OUT : remaining.getFirst();
                }
            }).next();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String describe(List<Integer> bytes, Key key) {
        StringBuilder hex = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (int b : bytes) {
            hex.append(String.format("%02x ", b));
            text.append(b == 27 ? "ESC " : b < 32 ? "^" + (char) (b + 64) + " " : b == 127 ? "DEL " : (char) b + " ");
        }
        String meaning = key.type() == KeyDecoder.Type.UNKNOWN || key.type() == KeyDecoder.Type.ESCAPE
                ? key.type() + "  (ignored by the editor)"
                : key.type().toString();
        return String.format("%-28s %-26s -> %s", hex.toString().stripTrailing(), text.toString().stripTrailing(), meaning);
    }
}
