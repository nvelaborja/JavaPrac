package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EditorTest {

    @Nested
    class Typing {
        @Test
        void startsWithTheCursorAtTheEndOfTheStarterText() {
            // given: starter text on two lines
            Editor editor = new Editor("int a;\nint b;");

            // then: the cursor is at the end of the last line
            assertEquals(1, editor.row());
            assertEquals(6, editor.col());
        }

        @Test
        void insertsTextAndSplitsLinesOnEnter() {
            // given: an empty editor
            Editor editor = new Editor("");

            // when: typing text with a newline in the middle
            editor.insert("ab");
            editor.left();
            editor.newline();

            // then: the line splits at the cursor
            assertEquals("a\nb", editor.text());
            assertEquals(1, editor.row());
            assertEquals(0, editor.col());
        }

        @Test
        void expandsTabsToFourSpacesAndIgnoresControlCharacters() {
            Editor editor = new Editor("");
            editor.insert("\tx\u0001");
            assertEquals("    x", editor.text());
        }

        @Test
        void pastedMultiLineTextKeepsItsLines() {
            Editor editor = new Editor("");
            editor.insert("a\nb\nc");
            assertEquals(3, editor.lineCount());
            assertEquals("a\nb\nc", editor.text());
        }
    }

    @Nested
    class Deleting {
        @Test
        void backspaceAtLineStartJoinsWithThePreviousLine() {
            // given: two lines with the cursor at the start of the second
            Editor editor = new Editor("ab\ncd");
            editor.home();

            // when: backspace
            editor.backspace();

            // then: lines are joined and the cursor sits at the seam
            assertEquals("abcd", editor.text());
            assertEquals(0, editor.row());
            assertEquals(2, editor.col());
        }

        @Test
        void backspaceAtTheVeryStartDoesNothing() {
            Editor editor = new Editor("x");
            editor.home();
            editor.backspace();
            assertEquals("x", editor.text());
        }

        @Test
        void deleteAtLineEndPullsUpTheNextLine() {
            Editor editor = new Editor("ab\ncd");
            editor.up();
            editor.end();
            editor.delete();
            assertEquals("abcd", editor.text());
        }
    }

    @Nested
    class Moving {
        @Test
        void verticalMovementRemembersTheWantedColumnAcrossShortLines() {
            // given: a long line, a short line, a long line, with the cursor at column 5 of the last
            Editor editor = new Editor("abcdefgh\nab\nabcdefgh");
            editor.home();
            for (int i = 0; i < 5; i++) {
                editor.right();
            }

            // when: moving up across the short line and back down
            editor.up();
            assertEquals(2, editor.col());
            editor.down();

            // then: the original column is restored
            assertEquals(5, editor.col());
        }

        @Test
        void leftAndRightWrapAcrossLines() {
            Editor editor = new Editor("a\nb");
            editor.home();
            editor.left();
            assertEquals(0, editor.row());
            assertEquals(1, editor.col());
            editor.right();
            assertEquals(1, editor.row());
            assertEquals(0, editor.col());
        }
    }
}
