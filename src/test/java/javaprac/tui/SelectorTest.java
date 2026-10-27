package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javaprac.model.Challenge;
import javaprac.model.Challenge.Case;
import javaprac.model.Challenge.Kind;
import javaprac.model.Challenge.Structure;
import org.junit.jupiter.api.Test;

class SelectorTest {
    private static Challenge challenge(String id) {
        return new Challenge(id, id, List.of(), 1, Kind.EXPRESSION, "p", "", "", List.of(new Case(null, List.of("true"))),
                Structure.NONE, List.of("h"), "r", List.of());
    }

    @Test
    void dealsEveryChallengeOnceBeforeRepeating() {
        // given: five challenges
        List<Challenge> pool = List.of(challenge("a.1"), challenge("a.2"), challenge("a.3"), challenge("a.4"), challenge("a.5"));
        Selector selector = new Selector(pool, new Random(1));

        // when: five are dealt
        Set<Challenge> seen = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            seen.add(selector.next());
        }

        // then: no repeats
        assertEquals(5, seen.size());
    }

    @Test
    void neverDealsTheSameChallengeTwiceInARowAcrossReshuffles() {
        Selector selector = new Selector(List.of(challenge("a.1"), challenge("a.2")), new Random(7));
        Challenge previous = selector.next();
        for (int i = 0; i < 200; i++) {
            Challenge next = selector.next();
            assertNotSame(previous, next);
            previous = next;
        }
    }

    @Test
    void rejectsAnEmptyPool() {
        assertThrows(IllegalArgumentException.class, () -> new Selector(List.of(), new Random()));
    }
}
