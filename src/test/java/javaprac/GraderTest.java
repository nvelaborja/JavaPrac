package javaprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javaprac.grade.Grader;
import javaprac.grade.Verdict;
import javaprac.grade.Verdict.Outcome;
import javaprac.model.Challenge;
import javaprac.model.Challenge.Case;
import javaprac.model.Challenge.Kind;
import javaprac.model.Challenge.Structure;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GraderTest {
    private final Grader grader = new Grader();

    private static Challenge challenge(Kind kind, String given, List<String> asserts, Structure structure) {
        return new Challenge("test.challenge", "Test", List.of("test"), 1, kind, "prompt", given, "",
                List.of(new Case(null, asserts)), structure, List.of("hint"), "unused", List.of());
    }

    private static Challenge expression(String given, String assertion) {
        return challenge(Kind.EXPRESSION, given, List.of(assertion), Structure.NONE);
    }

    @Nested
    class Behavior {
        @Test
        void passesAnyEquivalentSolution() {
            // given: a challenge that only cares about the resulting list
            Challenge c = expression("List<Integer> nums = List.of(5, 3, 8, 1, 8, 2);", "result.equals(List.of(5, 3, 8, 1, 8))");

            // when: three differently written solutions are graded
            // then: all pass
            assertTrue(grader.grade(c, "nums.stream().limit(5).toList();").passed());
            assertTrue(grader.grade(c, "nums.stream().limit(5).collect(Collectors.toList())").passed());
            assertTrue(grader.grade(c, "nums.subList(0, 5)").passed());
        }

        @Test
        void failsAWrongAnswerAndNamesTheFailedCheck() {
            // given: a challenge expecting five elements
            Challenge c = expression("List<Integer> nums = List.of(5, 3, 8, 1, 8, 2);", "result.equals(List.of(5, 3, 8, 1, 8))");

            // when: the user returns four
            Verdict v = grader.grade(c, "nums.stream().limit(4).toList();");

            // then: the check fails and says which assertion
            assertEquals(Outcome.CHECK_FAILED, v.outcome());
            assertTrue(v.message().contains("result.equals(List.of(5, 3, 8, 1, 8))"), v.message());
        }

        @Test
        void runsEveryCaseAndLabelsTheFailingOne() {
            // given: two cases where the second input breaks a naive solution
            Challenge c = new Challenge("t", "T", List.of(), 1, Kind.EXPRESSION, "p", "int a = 1;", "",
                    List.of(new Case(null, List.of("result == 1")), new Case("int a = 2;", List.of("result == 1"))),
                    Structure.NONE, List.of("h"), "a", List.of());

            // when: the solution is only right for the first input
            Verdict v = grader.grade(c, "a");

            // then: the failure points at case 2
            assertEquals(Outcome.CHECK_FAILED, v.outcome());
            assertTrue(v.message().contains("case 2"), v.message());
        }

        @Test
        void capturesPrintedOutputForAssertions() {
            // given: a challenge asserting on stdout
            Challenge c = challenge(Kind.STATEMENTS, "", List.of("out().equals(\"hi\\n\")"), Structure.NONE);

            // when: the user prints with println
            // then: it passes, and a different message fails
            assertTrue(grader.grade(c, "System.out.println(\"hi\");").passed());
            assertFalse(grader.grade(c, "System.out.println(\"bye\");").passed());
        }
    }

    @Nested
    class Failures {
        @Test
        void reportsCompileErrorsWithTheLineInTheUsersOwnCode() {
            // given: statements with an error on their second line
            Challenge c = challenge(Kind.STATEMENTS, "int a = 1;", List.of("true"), Structure.NONE);

            // when: graded
            Verdict v = grader.grade(c, "int ok = 1;\nint bad = \"text\";");

            // then: the line number is relative to what the user typed, not the generated file
            assertEquals(Outcome.COMPILE_ERROR, v.outcome());
            assertTrue(v.message().startsWith("line 2, col "), v.message());
        }

        @Test
        void showsTheSourceLineWithACaretUnderTheErrorColumnAndTheMissingSymbol() {
            // given: an expression that calls a method IntStream doesn't have
            Challenge c = challenge(Kind.EXPRESSION, "", List.of("true"), Structure.NONE);

            // when: graded
            Verdict v = grader.grade(c, "IntStream.range(1, 5).toList()");

            // then: the message names the symbol and where it was looked up, and a caret marks the column (javac points at the dot)
            String[] lines = v.message().split("\n");
            assertEquals(Outcome.COMPILE_ERROR, v.outcome());
            assertTrue(lines[0].startsWith("line 1, col 22: cannot find symbol: method toList()"), lines[0]);
            assertTrue(lines[0].contains("IntStream"), lines[0]);
            assertEquals("  IntStream.range(1, 5).toList()", lines[1]);
            assertEquals("  " + " ".repeat(21) + "^", lines[2]);
        }

        @Test
        void reportsExceptionsFromTheUsersCode() {
            // given: any challenge
            Challenge c = challenge(Kind.STATEMENTS, "", List.of("true"), Structure.NONE);

            // when: the code throws
            Verdict v = grader.grade(c, "Integer.parseInt(\"x\");");

            // then: it is a runtime error naming the exception
            assertEquals(Outcome.RUNTIME_ERROR, v.outcome());
            assertTrue(v.message().contains("NumberFormatException"), v.message());
        }

        @Test
        void stopsInfiniteLoops() {
            // given: any challenge
            Challenge c = challenge(Kind.STATEMENTS, "", List.of("true"), Structure.NONE);

            // when: the code never returns (a literal `while (true)` would be a compile error: the checks are unreachable)
            Verdict v = grader.grade(c, "while (System.nanoTime() > 0) { }");

            // then: the sandbox times it out instead of hanging the tool
            assertEquals(Outcome.TIMEOUT, v.outcome());
        }

        @Test
        void doesNotLetSystemExitFakeAPass() {
            // given: any challenge with an assertion that would fail
            Challenge c = challenge(Kind.STATEMENTS, "", List.of("false"), Structure.NONE);

            // when: the user exits before the checks run
            Verdict v = grader.grade(c, "System.exit(0);");

            // then: that is not a pass
            assertFalse(v.passed());
            assertEquals(Outcome.RUNTIME_ERROR, v.outcome());
        }

        @Test
        void rejectsBlankSubmissions() {
            // given: any challenge
            Challenge c = challenge(Kind.STATEMENTS, "", List.of("true"), Structure.NONE);

            // when / then
            assertEquals(Outcome.COMPILE_ERROR, grader.grade(c, "   \n").outcome());
        }
    }

    @Nested
    class Structure_ {
        private final Challenge noLoops = challenge(Kind.EXPRESSION, "List<Integer> nums = List.of(1, 2);",
                List.of("result.size() == 2"), new Structure(List.of("call:stream"), List.of("loop")));

        @Test
        void passesWhenRequiredCallsArePresent() {
            assertTrue(grader.grade(noLoops, "nums.stream().toList()").passed());
        }

        @Test
        void failsWhenARequiredCallIsMissing() {
            // given: a challenge requiring .stream()
            // when: the answer is correct but doesn't use it
            Verdict v = grader.grade(noLoops, "nums");

            // then: the structure check names what is missing
            assertEquals(Outcome.STRUCTURE, v.outcome());
            assertTrue(v.message().contains(".stream("), v.message());
        }

        @Test
        void failsWhenAForbiddenLoopIsPresent() {
            Challenge c = challenge(Kind.STATEMENTS, "", List.of("sum == 3"), new Structure(List.of(), List.of("loop")));
            Verdict v = grader.grade(c, "int sum = 0;\nfor (int i = 1; i <= 2; i++) { sum += i; }");
            assertEquals(Outcome.STRUCTURE, v.outcome());
        }

        @Test
        void ignoresKeywordsInCommentsAndStringLiterals() {
            // given: a no-loop challenge
            Challenge c = challenge(Kind.STATEMENTS, "", List.of("s.equals(\"for while\")"), new Structure(List.of(), List.of("loop")));

            // when: "for" and "while" only appear in a comment and a string
            Verdict v = grader.grade(c, "// no for loop here\nString s = \"for while\";");

            // then: the AST-based check does not trip on them
            assertTrue(v.passed(), v.message());
        }

        @Test
        void acceptsEitherAlternativeInARequirement() {
            Challenge c = challenge(Kind.EXPRESSION, "List<Integer> nums = List.of(1, 2);", List.of("result == 2"),
                    new Structure(List.of("call:size|call:count"), List.of()));
            assertTrue(grader.grade(c, "nums.size()").passed());
        }
    }

    @Nested
    class Declarations {
        @Test
        void acceptsPublicTypesEvenThoughTheFileIsNamedAttempt() {
            // given: a declarations challenge
            Challenge c = challenge(Kind.DECLARATIONS, "", List.of("new Point(1, 2).x() == 1"), Structure.NONE);

            // when: the user writes `public record`
            Verdict v = grader.grade(c, "public record Point(int x, int y) {}");

            // then: it still compiles and passes
            assertTrue(v.passed(), v.message());
        }

        @Test
        void collapsesTheSameMissingSymbolReportedByEveryAssertion() {
            // given: three asserts that each use the missing type
            Challenge c = challenge(Kind.DECLARATIONS, "",
                    List.of("new Point(1, 2).x() == 1", "new Point(3, 4).y() == 4", "new Point(0, 0).equals(new Point(0, 0))"),
                    Structure.NONE);

            // when: the user declares a differently named type
            Verdict v = grader.grade(c, "record Pt(int x, int y) {}");

            // then: javac's identical complaints appear once, not three times
            assertEquals(1, v.message().lines().count(), v.message());
        }

        @Test
        void reportsMissingTypesAsNotFittingTheChallenge() {
            Challenge c = challenge(Kind.DECLARATIONS, "", List.of("new Point(1, 2).x() == 1"), Structure.NONE);
            Verdict v = grader.grade(c, "record Pt(int x, int y) {}");
            assertEquals(Outcome.COMPILE_ERROR, v.outcome());
            assertTrue(v.message().contains("doesn't fit"), v.message());
        }
    }
}
