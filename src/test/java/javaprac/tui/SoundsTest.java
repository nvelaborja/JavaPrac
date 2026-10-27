package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javaprac.tui.Sounds.Cue;
import org.junit.jupiter.api.Test;

class SoundsTest {
    private final StringWriter terminal = new StringWriter();
    private final List<Cue> played = new ArrayList<>();

    private Sounds sounds(boolean enabled) {
        return new Sounds(enabled, new PrintWriter(terminal), played::add);
    }

    @Test
    void aFailureRingsTheTerminalBellAndPlaysTheBuzzer() {
        // given: sound on
        // when: an attempt fails
        sounds(true).fail();

        // then: the bell is written immediately and the buzzer cue is played
        assertEquals("\u0007", terminal.toString());
        assertEquals(List.of(Cue.BUZZ), played);
    }

    @Test
    void aPassPlaysTheChimeWithoutTheBell() {
        sounds(true).pass();

        assertEquals("", terminal.toString());
        assertEquals(List.of(Cue.CHIME), played);
    }

    @Test
    void mutedMeansNothingIsWrittenOrPlayed() {
        Sounds muted = sounds(false);
        muted.fail();
        muted.pass();

        assertEquals("", terminal.toString());
        assertEquals(List.of(), played);
    }
}
