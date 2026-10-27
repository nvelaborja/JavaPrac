package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import javaprac.model.Challenge;
import javaprac.model.Challenge.Case;
import javaprac.model.Challenge.Kind;
import javaprac.model.Challenge.Structure;
import javaprac.tui.Screen.Border;
import javaprac.tui.Screen.Play;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScreenTest {
    private static final Challenge CHALLENGE = new Challenge("streams.demo", "Demo", List.of("streams"), 2,
            Kind.EXPRESSION, "Return the first five elements.", "List<Integer> nums = List.of(1, 2, 3);", "",
            List.of(new Case(null, List.of("true"))), Structure.NONE, List.of("first hint", "second hint"), "nums", List.of());

    private static Play play(Editor editor, List<String> feedback, List<String> hints, boolean grading, Border border) {
        return new Play(CHALLENGE, editor, Duration.ofSeconds(75), true, 2, feedback, hints, grading, border, List.of());
    }

    private static String plain(String ansi) {
        return ansi.replaceAll("\u001b\\[[0-9;?]*[A-Za-z]", "");
    }

    @Nested
    class PlayScreen {
        @Test
        void showsPromptGivenCodeClockAndTries() {
            // given: a challenge in progress
            // when: rendered
            String text = plain(new Screen().renderPlay(play(new Editor(""), List.of(), List.of(), false, Border.NEUTRAL), 100, 30).ansi());

            // then: everything the player needs is on screen
            assertTrue(text.contains("Return the first five elements."));
            assertTrue(text.contains("List<Integer> nums = List.of(1, 2, 3);"));
            assertTrue(text.contains("01:15"));
            assertTrue(text.contains("tries 2"));
            assertTrue(text.contains("★★☆"));
        }

        @Test
        void showsFeedbackAndOnlyTheHintsRevealedSoFar() {
            String text = plain(new Screen().renderPlay(
                    play(new Editor(""), List.of("line 1: ';' expected"), List.of("first hint"), false, Border.FAIL), 100, 30).ansi());

            assertTrue(text.contains("✗ line 1: ';' expected"));
            assertTrue(text.contains("Hint 1: first hint"));
            assertFalse(text.contains("second hint"));
        }

        @Test
        void titlesTheEditorBoxWithTheExpectedAnswerShape() {
            String text = plain(new Screen().renderPlay(play(new Editor(""), List.of(), List.of(), false, Border.NEUTRAL), 100, 30).ansi());
            assertTrue(text.contains("Your expression (value is checked; no return)"), text);
            assertTrue(Screen.boxTitle(Kind.STATEMENTS).contains("statements"));
            assertTrue(Screen.boxTitle(Kind.DECLARATIONS).contains("top-level types"));
        }

        @Test
        void keepsPreformattedErrorLinesUnwrappedSoTheCaretStaysAligned() {
            List<String> feedback = List.of("line 1, col 5: cannot find symbol: class range", "  new IntStream.range(1, 5)", "      ^");
            String text = plain(new Screen().renderPlay(play(new Editor(""), feedback, List.of(), false, Border.FAIL), 100, 30).ansi());
            assertTrue(text.contains("  ✗ line 1, col 5: cannot find symbol: class range"), text);
            assertTrue(text.contains("    new IntStream.range(1, 5)"), text);
            assertTrue(text.contains("        ^"), text);
        }

        @Test
        void revealedSolutionIsShownAlongsideTheErrorAndHintsAndInFull() {
            // given: a failed attempt with a hint showing, and the solution is revealed on top of it
            Challenge multiLine = new Challenge("t.x", "T", List.of(), 1, Kind.STATEMENTS, "p", "", "",
                    List.of(new Case(null, List.of("true"))), Structure.NONE, List.of("h"), "int a = 1;\nint b = 2;", List.of());
            Play revealed = new Play(multiLine, new Editor(""), Duration.ZERO, true, 2, List.of("old error"), List.of("h"),
                    false, Border.NEUTRAL, List.of("int a = 1;", "int b = 2;"));

            // when: rendered
            String text = plain(new Screen().renderPlay(revealed, 100, 30).ansi());

            // then: the player still sees why they failed, and the whole solution, error first
            assertTrue(text.contains("Solution (type it in to finish; ^R hides it)"));
            assertTrue(text.contains("    int a = 1;"));
            assertTrue(text.contains("    int b = 2;"));
            assertTrue(text.contains("✗ old error"));
            assertTrue(text.contains("Hint 1: h"));
            assertTrue(text.indexOf("old error") < text.indexOf("Solution (type it in"), "error should precede the solution");
        }

        @Test
        void truncatesATallSolutionWithAPointerToSkipInsteadOfBreakingTheLayout() {
            List<String> tall = java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(i -> "line" + i).toList();
            Play revealed = new Play(CHALLENGE, new Editor(""), Duration.ZERO, true, 0, List.of(), List.of(), false,
                    Border.NEUTRAL, tall);

            Screen.Frame frame = new Screen().renderPlay(revealed, 100, 24);

            assertTrue(plain(frame.ansi()).contains("^N skip shows the full solution"));
            assertTrue(frame.cursorRow() <= 24);
        }

        @Test
        void keepsTheHintVisibleEvenWhenManyErrorLinesCompeteForSpace() {
            // given: a long error list and a hint, in a short terminal
            List<String> manyErrors = java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(i -> "error number " + i).toList();
            Play p = play(new Editor(""), manyErrors, List.of("the hint text"), false, Border.FAIL);

            // when: rendered
            String text = plain(new Screen().renderPlay(p, 100, 24).ansi());

            // then: the hint survives and the errors are what got trimmed
            assertTrue(text.contains("Hint 1: the hint text"));
            assertTrue(text.contains("error number 1"));
            assertFalse(text.contains("error number 12"));
        }

        @Test
        void footerAdvertisesTheSolutionKey() {
            String text = plain(new Screen().renderPlay(play(new Editor(""), List.of(), List.of(), false, Border.NEUTRAL), 100, 30).ansi());
            assertTrue(text.contains("^R solution"));
        }

        @Test
        void tintsTheBorderRedOnlyWhenFailing() {
            Screen.Frame failing = new Screen().renderPlay(play(new Editor(""), List.of(), List.of(), false, Border.FAIL), 100, 30);
            Screen.Frame neutral = new Screen().renderPlay(play(new Editor(""), List.of(), List.of(), false, Border.NEUTRAL), 100, 30);

            assertTrue(failing.ansi().contains(Ansi.BOLD + Ansi.RED + "┌"));
            assertFalse(neutral.ansi().contains(Ansi.BOLD + Ansi.RED + "┌"));
        }

        @Test
        void placesTheCursorInsideTheEditorAtTheEditPosition() {
            // given: the cursor at the end of the first line, and at the end of the second line
            Screen.Frame firstLine = new Screen().renderPlay(play(new Editor("ab"), List.of(), List.of(), false, Border.NEUTRAL), 100, 30);
            Screen.Frame secondLine = new Screen().renderPlay(play(new Editor("ab\ncde"), List.of(), List.of(), false, Border.NEUTRAL), 100, 30);

            // then: the second is exactly one row lower, and the column is border + gutter + characters typed
            assertEquals(firstLine.cursorRow() + 1, secondLine.cursorRow());
            assertEquals(2 + 4 + 2, firstLine.cursorCol());
            assertEquals(2 + 4 + 3, secondLine.cursorCol());
        }

        @Test
        void scrollsLongDocumentsSoTheCursorStaysVisible() {
            // given: 40 lines of code in a 24-row terminal
            String code = String.join("\n", java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(i -> "line" + i).toList());
            Editor editor = new Editor(code);

            // when: rendered with the cursor on the last line
            Screen.Frame frame = new Screen().renderPlay(play(editor, List.of(), List.of(), false, Border.NEUTRAL), 100, 24);

            // then: the last line is shown and the cursor is within the screen
            assertTrue(plain(frame.ansi()).contains("line40"));
            assertTrue(frame.cursorRow() <= 24);
        }

        @Test
        void refusesToDrawInATinyTerminal() {
            Screen.Frame frame = new Screen().renderPlay(play(new Editor(""), List.of(), List.of(), false, Border.NEUTRAL), 40, 10);
            assertTrue(plain(frame.ansi()).contains("Terminal too small"));
        }

        @Test
        void showsAGradingNoticeInsteadOfStaleFeedback() {
            String text = plain(new Screen().renderPlay(
                    play(new Editor(""), List.of("old error"), List.of(), true, Border.NEUTRAL), 100, 30).ansi());
            assertTrue(text.contains("compiling and running"));
            assertFalse(text.contains("old error"));
        }
    }

    @Nested
    class SummaryScreen {
        @Test
        void showsTheReferenceSolutionAndYoursWhenTheyDiffer() {
            String text = plain(new Screen().renderSummary(
                    new Screen.Summary(CHALLENGE, true, Duration.ofSeconds(42), 3, 1, false, "nums.stream().toList()", 4), 100, 30));

            assertTrue(text.contains("Passed: Demo"));
            assertTrue(text.contains("00:42"));
            assertTrue(text.contains("Reference solution"));
            assertTrue(text.contains("Your solution"));
        }

        @Test
        void labelsSkippedChallengesAndOmitsTheUsersCode() {
            String text = plain(new Screen().renderSummary(
                    new Screen.Summary(CHALLENGE, false, Duration.ofSeconds(5), 0, 0, true, "", 0), 100, 30));

            assertTrue(text.contains("Skipped: Demo"));
            assertTrue(text.contains("solution revealed"));
            assertFalse(text.contains("Your solution"));
        }
    }

    @Test
    void wrapsWordsWithoutExceedingTheWidthAndSplitsOverlongWords() {
        List<String> lines = Screen.wrap("alpha beta gamma delta aaaaaaaaaaaaaaaaaaaa", 10);
        assertTrue(lines.stream().allMatch(l -> l.length() <= 10), lines.toString());
        assertEquals("alpha beta", lines.getFirst());
    }
}
