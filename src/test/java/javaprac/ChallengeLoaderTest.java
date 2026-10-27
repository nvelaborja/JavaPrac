package javaprac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import javaprac.model.Challenge;
import javaprac.model.ChallengeLoader;
import org.junit.jupiter.api.Test;

class ChallengeLoaderTest {

    @Test
    void parsesTheSingleCaseShorthandAndDefaults() {
        // given: a minimal challenge using top-level asserts
        String yaml = """
                - id: demo.one
                  title: One
                  kind: expression
                  prompt: Do the thing.
                  asserts: ['result == 1']
                  hints: [try harder]
                  reference: 1
                """;

        // when: parsed
        Challenge c = ChallengeLoader.parse(yaml, "demo.yaml").getFirst();

        // then: the shorthand becomes one case and optional fields default sensibly
        assertEquals(1, c.cases().size());
        assertEquals(List.of("result == 1"), c.cases().getFirst().asserts());
        assertEquals(1, c.difficulty());
        assertEquals("", c.starter());
        assertTrue(c.structure().isEmpty());
        assertEquals(Challenge.Kind.EXPRESSION, c.kind());
    }

    @Test
    void rejectsChallengesWithoutHintsOrAsserts() {
        String noHints = "- {id: x.a, title: A, kind: expression, prompt: p, asserts: [true], reference: r}";
        String noAsserts = "- {id: x.b, title: B, kind: expression, prompt: p, hints: [h], reference: r}";

        assertTrue(assertThrows(IllegalArgumentException.class, () -> ChallengeLoader.parse(noHints, "t")).getMessage().contains("hint"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ChallengeLoader.parse(noAsserts, "t")).getMessage().contains("asserts"));
    }

    @Test
    void namesTheOffendingChallengeWhenARequiredFieldIsMissing() {
        String yaml = "- {id: x.c, kind: expression, prompt: p, asserts: [true], hints: [h], reference: r}";
        var e = assertThrows(IllegalArgumentException.class, () -> ChallengeLoader.parse(yaml, "t"));
        assertTrue(e.getMessage().contains("x.c") && e.getMessage().contains("title"), e.getMessage());
    }

    @Test
    void bundledChallengesHaveUniqueIdsTopicPrefixesAndAtLeastTwoHintsOrOne() {
        List<Challenge> all = ChallengeLoader.loadBundled();
        assertTrue(all.size() >= 40, "v1 target is about 40 challenges, found " + all.size());
        assertEquals(all.size(), new HashSet<>(all.stream().map(Challenge::id).toList()).size());
        assertTrue(all.stream().allMatch(c -> c.id().contains(".")), "ids must be topic.name");
        assertTrue(all.stream().allMatch(c -> c.difficulty() >= 1 && c.difficulty() <= 3));
    }
}
