package javaprac.model;

import java.util.List;

/**
 * One practice challenge, loaded from YAML.
 *
 * @param given   code shown to the user and placed before their code; cases may override it with hidden inputs
 * @param starter text the editor starts with (may be empty)
 * @param cases   each case is compiled and run separately against the user's code
 */
public record Challenge(
        String id,
        String title,
        List<String> tags,
        int difficulty,
        Kind kind,
        String prompt,
        String given,
        String starter,
        List<Case> cases,
        Structure structure,
        List<String> hints,
        String reference,
        List<String> rejects) {

    /** How the user's text is spliced into the generated program. */
    public enum Kind {
        /** A single expression; its value is bound to {@code result}. */
        EXPRESSION,
        /** Statements run inside a method after {@code given}. */
        STATEMENTS,
        /** Top-level type declarations (record, enum, class, interface) next to the harness. */
        DECLARATIONS
    }

    /** A boolean Java expression list evaluated after the user's code; {@code given == null} inherits the shared one. */
    public record Case(String given, List<String> asserts) {}

    /** AST features the solution must use ({@code require}) or must avoid ({@code forbid}); {@code a|b} means either. */
    public record Structure(List<String> require, List<String> forbid) {
        public static final Structure NONE = new Structure(List.of(), List.of());

        public boolean isEmpty() {
            return require.isEmpty() && forbid.isEmpty();
        }
    }

    public String givenFor(Case c) {
        return c.given() != null ? c.given() : given;
    }
}
