package javaprac.tui;

import java.io.PrintWriter;

/** The fail buzzer and pass chime: a terminal bell for failures, plus a system sound where one is available. */
final class Sounds {
    enum Cue { BUZZ, CHIME }

    /** Where the system sounds go; replaced in tests so they stay silent. */
    interface Output {
        void play(Cue cue);
    }

    private final boolean enabled;
    private final PrintWriter terminal;
    private final Output output;

    Sounds(boolean enabled, PrintWriter terminal) {
        this(enabled, terminal, enabled ? new SystemAudio() : cue -> { });
    }

    Sounds(boolean enabled, PrintWriter terminal, Output output) {
        this.enabled = enabled;
        this.terminal = terminal;
        this.output = output;
    }

    void fail() {
        if (enabled) {
            terminal.print('\u0007');
            terminal.flush();
            output.play(Cue.BUZZ);
        }
    }

    void pass() {
        if (enabled) {
            output.play(Cue.CHIME);
        }
    }
}
