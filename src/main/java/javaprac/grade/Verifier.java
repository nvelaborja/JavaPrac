package javaprac.grade;

import java.util.ArrayList;
import java.util.List;
import javaprac.model.Challenge;

/** Checks that a challenge is solvable (its reference passes) and discriminating (its known-wrong answers fail). */
public final class Verifier {
    public record Result(Challenge challenge, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private final Grader grader;

    public Verifier(Grader grader) {
        this.grader = grader;
    }

    public Result verify(Challenge challenge) {
        List<String> problems = new ArrayList<>();
        Verdict reference = grader.grade(challenge, challenge.reference());
        if (!reference.passed()) {
            problems.add("reference solution failed: " + reference.outcome() + " " + reference.message());
        }
        for (String wrong : challenge.rejects()) {
            if (grader.grade(challenge, wrong).passed()) {
                problems.add("should have been rejected but passed: " + wrong);
            }
        }
        return new Result(challenge, problems);
    }
}
