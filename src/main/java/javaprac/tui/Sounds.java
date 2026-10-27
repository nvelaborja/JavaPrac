package javaprac.tui;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/** The fail buzzer and pass chime: a terminal bell everywhere, plus a system sound on macOS. */
final class Sounds {
    private static final Path BUZZ = Path.of("/System/Library/Sounds/Basso.aiff");
    private static final Path CHIME = Path.of("/System/Library/Sounds/Glass.aiff");

    private final boolean enabled;
    private final PrintWriter terminal;

    Sounds(boolean enabled, PrintWriter terminal) {
        this.enabled = enabled;
        this.terminal = terminal;
    }

    void fail() {
        if (enabled) {
            terminal.print('\u0007');
            terminal.flush();
            play(BUZZ);
        }
    }

    void pass() {
        if (enabled) {
            play(CHIME);
        }
    }

    private static void play(Path sound) {
        if (!Files.exists(sound)) {
            return;
        }
        try {
            new ProcessBuilder("afplay", sound.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (java.io.IOException ignored) {
            // sound is a nicety; the terminal bell already fired
        }
    }
}
