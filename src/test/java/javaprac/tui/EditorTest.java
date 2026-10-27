package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    class WordsAndLines {
        /** Puts the cursor at the {@code |} marker in {@code text}. */
        private static Editor at(String text) {
            int marker = text.indexOf('|');
            String clean = text.replace("|", "");
            Editor editor = new Editor(clean);
            editor.up();
            while (editor.row() > 0) {
                editor.up();
            }
            editor.home();
            int before = clean.substring(0, marker).split("\n", -1).length - 1;
            for (int i = 0; i < before; i++) {
                editor.down();
            }
            editor.home();
            int column = marker - (clean.substring(0, marker).lastIndexOf('\n') + 1);
            for (int i = 0; i < column; i++) {
                editor.right();
            }
            return editor;
        }

        private static String withCursor(Editor editor) {
            String text = editor.text();
            int offset = 0;
            for (int r = 0; r < editor.row(); r++) {
                offset += editor.line(r).length() + 1;
            }
            offset += editor.col();
            return text.substring(0, offset) + "|" + text.substring(offset);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = ';', textBlock = """
                foo bar|; foo |bar
                foo bar baz|; foo bar |baz
                foo.bar|; foo.|bar
                foo   |bar; |foo   bar
                |foo; |foo
                snake_case_name|; |snake_case_name
                a1 b2|; a1 |b2
                """)
        void wordLeftStopsAtTheStartOfWords(String start, String expected) {
            Editor editor = at(start);
            editor.wordLeft();
            assertEquals(expected, withCursor(editor));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = ';', textBlock = """
                |foo bar; foo| bar
                foo| bar; foo bar|
                '|  foo'; '  foo|'
                foo.|bar; foo.bar|
                |snake_case name; snake_case| name
                foo bar|; foo bar|
                """)
        void wordRightStopsAtTheEndOfWords(String start, String expected) {
            Editor editor = at(start);
            editor.wordRight();
            assertEquals(expected, withCursor(editor));
        }

        @Test
        void wordMovesCrossLineBoundariesOneStepAtATime() {
            Editor editor = at("ab\n|cd");
            editor.wordLeft();
            assertEquals("ab|\ncd", withCursor(editor));
            editor.wordRight();
            assertEquals("ab\n|cd", withCursor(editor));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = ';', textBlock = """
                foo bar|; foo |
                foo bar |; foo |
                foo.bar|; foo.|
                |foo; |foo
                """)
        void deleteWordBackRemovesTheWordBeforeTheCursor(String start, String expected) {
            Editor editor = at(start);
            editor.deleteWordBack();
            assertEquals(expected, withCursor(editor));
        }

        @Test
        void deleteWordBackAtLineStartJoinsLines() {
            Editor editor = at("ab\n|cd");
            editor.deleteWordBack();
            assertEquals("ab|cd", withCursor(editor));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = ';', textBlock = """
                |foo bar; | bar
                foo| bar; foo|
                foo.|bar; foo.|
                """)
        void deleteWordForwardRemovesTheWordAfterTheCursor(String start, String expected) {
            Editor editor = at(start);
            editor.deleteWordForward();
            assertEquals(expected, withCursor(editor));
        }

        @Test
        void ctrlWDeletesBackToWhitespaceIncludingPunctuation() {
            Editor editor = at("int x = foo.bar(1)|");
            editor.deleteBlankDelimitedWordBack();
            assertEquals("int x = |", withCursor(editor));
        }

        @Test
        void deleteToLineStartKeepsTheRestOfTheLineAndOtherLines() {
            Editor editor = at("first\nsecond| line\nthird");
            editor.deleteToLineStart();
            assertEquals("first\n| line\nthird", withCursor(editor));
        }

        @Test
        void deleteToLineEndKeepsTheStartOfTheLineAndOtherLines() {
            Editor editor = at("first\nsec|ond line\nthird");
            editor.deleteToLineEnd();
            assertEquals("first\nsec|\nthird", withCursor(editor));
        }

        @Test
        void deleteToLineEndAtTheEndOfALineJoinsTheNextOne() {
            Editor editor = at("first|\nsecond");
            editor.deleteToLineEnd();
            assertEquals("first|second", withCursor(editor));
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
