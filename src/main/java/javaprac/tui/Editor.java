package javaprac.tui;

import java.util.ArrayList;
import java.util.List;

/** A minimal multi-line text buffer with a cursor. Deliberately has no completion, indentation or bracket help. */
public final class Editor {
    private final List<StringBuilder> lines = new ArrayList<>();
    private int row;
    private int col;
    /** Column to return to when moving vertically across shorter lines. */
    private int wantedCol;

    public Editor(String initial) {
        for (String line : initial.split("\n", -1)) {
            lines.add(new StringBuilder(line));
        }
        row = lines.size() - 1;
        col = lines.get(row).length();
        wantedCol = col;
    }

    public String text() {
        return String.join("\n", lines.stream().map(StringBuilder::toString).toList());
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    public int lineCount() {
        return lines.size();
    }

    public String line(int index) {
        return lines.get(index).toString();
    }

    public void insert(String s) {
        for (char ch : s.toCharArray()) {
            if (ch == '\n') {
                newline();
            } else if (ch == '\t') {
                insertText("    ");
            } else if (ch >= ' ') {
                insertText(String.valueOf(ch));
            }
        }
    }

    private void insertText(String s) {
        lines.get(row).insert(col, s);
        col += s.length();
        wantedCol = col;
    }

    public void newline() {
        StringBuilder current = lines.get(row);
        String tail = current.substring(col);
        current.setLength(col);
        lines.add(++row, new StringBuilder(tail));
        col = 0;
        wantedCol = 0;
    }

    public void backspace() {
        if (col > 0) {
            lines.get(row).deleteCharAt(--col);
        } else if (row > 0) {
            StringBuilder removed = lines.remove(row--);
            col = lines.get(row).length();
            lines.get(row).append(removed);
        }
        wantedCol = col;
    }

    public void delete() {
        StringBuilder current = lines.get(row);
        if (col < current.length()) {
            current.deleteCharAt(col);
        } else if (row < lines.size() - 1) {
            current.append(lines.remove(row + 1));
        }
    }

    public void left() {
        if (col > 0) {
            col--;
        } else if (row > 0) {
            col = lines.get(--row).length();
        }
        wantedCol = col;
    }

    public void right() {
        if (col < lines.get(row).length()) {
            col++;
        } else if (row < lines.size() - 1) {
            row++;
            col = 0;
        }
        wantedCol = col;
    }

    public void up() {
        if (row > 0) {
            row--;
            col = Math.min(wantedCol, lines.get(row).length());
        }
    }

    public void down() {
        if (row < lines.size() - 1) {
            row++;
            col = Math.min(wantedCol, lines.get(row).length());
        }
    }

    /** Readline's backward-word (Option-Left, Ctrl-Left, Alt-b): to the start of the previous word. */
    public void wordLeft() {
        if (col == 0) {
            left();
            return;
        }
        col = wordStartBefore(lines.get(row), col);
        wantedCol = col;
    }

    /** Readline's forward-word (Option-Right, Ctrl-Right, Alt-f): to the end of the next word. */
    public void wordRight() {
        StringBuilder current = lines.get(row);
        if (col == current.length()) {
            right();
            return;
        }
        col = wordEndAfter(current, col);
        wantedCol = col;
    }

    /** Readline's backward-kill-word (Option-Backspace): deletes back to the start of the word. */
    public void deleteWordBack() {
        if (col == 0) {
            backspace();
            return;
        }
        deleteRange(wordStartBefore(lines.get(row), col), col);
    }

    /** Readline's kill-word (Alt-d, Option-Delete): deletes forward to the end of the word. */
    public void deleteWordForward() {
        StringBuilder current = lines.get(row);
        if (col == current.length()) {
            delete();
            return;
        }
        deleteRange(col, wordEndAfter(current, col));
    }

    /** Bash's unix-word-rubout (Ctrl-W): deletes back to the previous whitespace, punctuation included. */
    public void deleteBlankDelimitedWordBack() {
        if (col == 0) {
            backspace();
            return;
        }
        StringBuilder current = lines.get(row);
        int start = col;
        while (start > 0 && Character.isWhitespace(current.charAt(start - 1))) {
            start--;
        }
        while (start > 0 && !Character.isWhitespace(current.charAt(start - 1))) {
            start--;
        }
        deleteRange(start, col);
    }

    /** Ctrl-U: deletes everything before the cursor on this line. */
    public void deleteToLineStart() {
        deleteRange(0, col);
    }

    /** Ctrl-K: deletes everything after the cursor on this line, or joins the next line when already at the end. */
    public void deleteToLineEnd() {
        StringBuilder current = lines.get(row);
        if (col < current.length()) {
            current.setLength(col);
        } else {
            delete();
        }
    }

    private void deleteRange(int from, int to) {
        lines.get(row).delete(from, to);
        col = from;
        wantedCol = col;
    }

    private static int wordStartBefore(CharSequence line, int from) {
        int i = from;
        while (i > 0 && !isWordChar(line.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && isWordChar(line.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private static int wordEndAfter(CharSequence line, int from) {
        int i = from;
        while (i < line.length() && !isWordChar(line.charAt(i))) {
            i++;
        }
        while (i < line.length() && isWordChar(line.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public void home() {
        col = 0;
        wantedCol = 0;
    }

    public void end() {
        col = lines.get(row).length();
        wantedCol = col;
    }
}
