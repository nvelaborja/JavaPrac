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

    public void home() {
        col = 0;
        wantedCol = 0;
    }

    public void end() {
        col = lines.get(row).length();
        wantedCol = col;
    }
}
