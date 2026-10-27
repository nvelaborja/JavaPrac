package javaprac.tui;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javaprac.tui.KeyDecoder.Key;

/**
 * Reads keys on a background thread and hands them over through a queue.
 *
 * <p>JLine's timed {@code peek} rounds every wait up to about 100 ms, so asking it "is more input pending?" after each
 * keystroke added a visible lag to typing, and during key-repeat the answer was always yes so the caret never redrew.
 * A queue gives exact timeouts and a cheap {@link #hasPending()}.
 */
final class KeyPump {
    private static final Key QUIT = Key.of(KeyDecoder.Type.QUIT);

    private final BlockingQueue<Key> keys = new LinkedBlockingQueue<>();

    KeyPump(KeyDecoder decoder) {
        Thread thread = new Thread(() -> pump(decoder), "javaprac-keys");
        thread.setDaemon(true);
        thread.start();
    }

    private void pump(KeyDecoder decoder) {
        try {
            while (true) {
                Key key = decoder.next();
                keys.add(key);
                if (key.type() == KeyDecoder.Type.QUIT) {
                    return;
                }
            }
        } catch (IOException e) {
            keys.add(QUIT);
        }
    }

    boolean hasPending() {
        return !keys.isEmpty();
    }

    /** The next key, or {@code null} if none arrives within the timeout. */
    Key poll(long timeoutMs) {
        try {
            return keys.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return QUIT;
        }
    }

    Key take() {
        try {
            return keys.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return QUIT;
        }
    }
}
