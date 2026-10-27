package javaprac.tui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javaprac.model.Challenge;

/** Deals challenges from a shuffled bag so nothing repeats until everything has been seen. */
public final class Selector {
    private final List<Challenge> pool;
    private final Random random;
    private final List<Challenge> bag = new ArrayList<>();
    private Challenge last;

    public Selector(List<Challenge> pool, Random random) {
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("No challenges match");
        }
        this.pool = List.copyOf(pool);
        this.random = random;
    }

    public Challenge next() {
        if (bag.isEmpty()) {
            bag.addAll(pool);
            Collections.shuffle(bag, random);
            // don't repeat the last challenge across a reshuffle
            if (bag.size() > 1 && bag.getLast() == last) {
                Collections.swap(bag, 0, bag.size() - 1);
            }
        }
        last = bag.removeLast();
        return last;
    }
}
