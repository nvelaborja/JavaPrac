package javaprac;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import javaprac.grade.Grader;
import javaprac.grade.Verifier;
import javaprac.model.Challenge;
import javaprac.model.ChallengeLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BundledChallengesTest {
    private static final Verifier VERIFIER = new Verifier(new Grader());

    static Stream<Challenge> challenges() {
        return ChallengeLoader.loadBundled().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("challenges")
    void referenceSolutionPassesAndWrongAnswersFail(Challenge challenge) {
        // given: a bundled challenge with its reference solution and known-wrong answers
        // when: each is graded by the real compiler and sandbox
        Verifier.Result result = VERIFIER.verify(challenge);

        // then: the reference passes and every wrong answer is rejected
        assertTrue(result.ok(), () -> challenge.id() + ": " + List.copyOf(result.problems()));
    }
}
