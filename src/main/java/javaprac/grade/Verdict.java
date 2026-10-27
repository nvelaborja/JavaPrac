package javaprac.grade;

/** The result of grading one submission. */
public record Verdict(Outcome outcome, String message) {

    public enum Outcome {
        PASS,
        COMPILE_ERROR,
        /** Compiles, but a required construct is missing or a forbidden one is present. */
        STRUCTURE,
        /** Compiles and runs, but an assertion about the result was false. */
        CHECK_FAILED,
        /** The user's code threw, or the program ended before finishing. */
        RUNTIME_ERROR,
        TIMEOUT
    }

    public static Verdict pass() {
        return new Verdict(Outcome.PASS, "All checks passed.");
    }

    public boolean passed() {
        return outcome == Outcome.PASS;
    }
}
