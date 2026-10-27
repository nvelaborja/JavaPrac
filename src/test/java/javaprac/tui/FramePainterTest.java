package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FramePainterTest {

    private static Screen.Frame frame(int rows, int cursorRow, int cursorCol, String... overrides) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            lines.add("row" + i);
        }
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            lines.set(Integer.parseInt(overrides[i]), overrides[i + 1]);
        }
        return new Screen.Frame(lines, cursorRow, cursorCol);
    }

    @Test
    void theFirstFrameRepaintsEverythingWithTheCursorHiddenWhileDrawing() {
        // given: nothing on screen yet
        FramePainter painter = new FramePainter();

        // when: the first frame is painted
        String out = painter.paint(frame(26, 9, 6));

        // then: all rows are written, wrapped in hide/show so the repaint isn't seen half-done
        assertTrue(out.startsWith(Ansi.HIDE_CURSOR));
        assertTrue(out.endsWith(Ansi.moveTo(9, 6) + Ansi.SHOW_CURSOR));
        assertTrue(out.contains("row25"));
    }

    @Test
    void anIdenticalFrameWritesNothingSoTheCaretKeepsBlinkingNormally() {
        FramePainter painter = new FramePainter();
        painter.paint(frame(26, 9, 6));

        assertEquals("", painter.paint(frame(26, 9, 6)));
    }

    @Test
    void typingRewritesOnlyTheChangedRowAndNeverTouchesCursorVisibility() {
        // given: a painted screen
        FramePainter painter = new FramePainter();
        painter.paint(frame(26, 9, 6));

        // when: one editor row changes and the cursor advances
        String out = painter.paint(frame(26, 9, 7, "8", "row8 + typed"));

        // then: just that row and the new cursor position are written
        assertTrue(out.contains("row8 + typed"));
        assertFalse(out.contains("row7"));
        assertFalse(out.contains("row9"));
        assertFalse(out.contains(Ansi.HIDE_CURSOR));
        assertFalse(out.contains(Ansi.SHOW_CURSOR));
        assertTrue(out.endsWith(Ansi.moveTo(9, 7)));
    }

    @Test
    void aClockTickChangesOnlyTheHeaderRow() {
        FramePainter painter = new FramePainter();
        painter.paint(frame(26, 9, 6));

        String out = painter.paint(frame(26, 9, 6, "0", "header 00:01"));

        assertTrue(out.contains("header 00:01"));
        assertEquals(1, out.split("\u001b\\[K", -1).length - 1, "exactly one row rewritten: " + out);
        assertFalse(out.contains(Ansi.HIDE_CURSOR));
    }

    @Test
    void movingOnlyTheCursorWritesJustTheMove() {
        FramePainter painter = new FramePainter();
        painter.paint(frame(26, 9, 6));

        assertEquals(Ansi.moveTo(9, 5), painter.paint(frame(26, 9, 5)));
    }

    @Test
    void aNearFullRepaintHidesTheCursorAgain() {
        FramePainter painter = new FramePainter();
        painter.paint(frame(26, 9, 6));

        // when: more than the threshold of rows change at once (scrolling, a new feedback block)
        String[] changes = new String[(FramePainter.FULL_REPAINT_ROWS + 2) * 2];
        for (int i = 0; i < changes.length; i += 2) {
            changes[i] = String.valueOf(i / 2);
            changes[i + 1] = "new" + i;
        }
        String out = painter.paint(frame(26, 9, 6, changes));

        // then: the update is wrapped in hide/show
        assertTrue(out.startsWith(Ansi.HIDE_CURSOR));
        assertTrue(out.endsWith(Ansi.SHOW_CURSOR));
    }

    @Test
    void resetForcesTheNextFrameToRepaintEverything() {
        FramePainter painter = new FramePainter();
        painter.paint(frame(26, 9, 6));
        painter.reset();

        assertTrue(painter.paint(frame(26, 9, 6)).contains("row25"));
    }
}
