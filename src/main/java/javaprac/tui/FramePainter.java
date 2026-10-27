package javaprac.tui;

import java.util.List;

/**
 * Turns a full frame into the smallest escape sequence that updates the terminal from the previous one.
 *
 * <p>Repainting everything on every tick made the caret flicker: hiding and re-showing the cursor restarts the terminal's
 * blink cycle. Writing only the changed rows, and nothing at all when nothing changed, lets the caret blink normally.
 */
final class FramePainter {
    /** Above this many changed rows the update is a near-full repaint, which is hidden from view to avoid tearing. */
    static final int FULL_REPAINT_ROWS = 8;

    private List<String> previousRows;
    private int previousCursorRow;
    private int previousCursorCol;

    /** Forget what is on screen, so the next frame repaints everything (after a resize, clear, or a different screen). */
    void reset() {
        previousRows = null;
    }

    /** The escape sequence to write for {@code frame}, or an empty string if the screen is already up to date. */
    String paint(Screen.Frame frame) {
        List<String> rows = frame.rows();
        StringBuilder changes = new StringBuilder();
        int changed = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (previousRows == null || i >= previousRows.size() || !rows.get(i).equals(previousRows.get(i))) {
                changes.append(Ansi.moveTo(i + 1, 1)).append(rows.get(i)).append(Ansi.CLEAR_TO_EOL);
                changed++;
            }
        }
        boolean cursorMoved = frame.cursorRow() != previousCursorRow || frame.cursorCol() != previousCursorCol;
        previousRows = rows;
        previousCursorRow = frame.cursorRow();
        previousCursorCol = frame.cursorCol();
        if (changed == 0 && !cursorMoved) {
            return "";
        }
        boolean hide = changed > FULL_REPAINT_ROWS;
        return (hide ? Ansi.HIDE_CURSOR : "") + changes + Ansi.moveTo(frame.cursorRow(), frame.cursorCol())
                + (hide ? Ansi.SHOW_CURSOR : "");
    }
}
