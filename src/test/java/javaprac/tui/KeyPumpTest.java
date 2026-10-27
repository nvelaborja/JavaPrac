package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javaprac.tui.KeyDecoder.Key;
import javaprac.tui.KeyDecoder.Type;
import org.junit.jupiter.api.Test;

class KeyPumpTest {

    /** A terminal stand-in whose {@code read} blocks until the test supplies a byte, like a real tty. */
    private static final class FakeTty implements KeyDecoder.Source {
        private final BlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();

        void type(String s) {
            s.chars().forEach(bytes::add);
        }

        @Override
        public int read() throws IOException {
            try {
                return bytes.take();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int peek(long timeoutMs) {
            Integer next = bytes.peek();
            return next == null ? KeyDecoder.TIMED_OUT : next;
        }
    }

    @Test
    void deliversKeysInOrderAndReportsTimeoutsAsNull() {
        // given: a pump over a tty with nothing typed
        FakeTty tty = new FakeTty();
        KeyPump pump = new KeyPump(new KeyDecoder(tty));

        // when / then: an idle wait times out with null, exactly when asked, not rounded up
        long start = System.nanoTime();
        assertNull(pump.poll(30));
        long waitedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(waitedMs >= 25 && waitedMs < 90, "waited " + waitedMs + " ms for a 30 ms timeout");

        // and: typed keys come out in order, including a decoded escape sequence
        tty.type("a\u001b[D");
        assertEquals('a', pump.poll(1000).ch());
        assertEquals(Type.LEFT, pump.poll(1000).type());
    }

    @Test
    void wakesPromptlyWhenAKeyArrivesDuringAWait() {
        // given: a consumer already waiting
        FakeTty tty = new FakeTty();
        KeyPump pump = new KeyPump(new KeyDecoder(tty));
        new Thread(() -> {
            try {
                Thread.sleep(40);
            } catch (InterruptedException ignored) {
                return;
            }
            tty.type("x");
        }).start();

        // when: it waits far longer than the key takes to arrive
        long start = System.nanoTime();
        Key key = pump.poll(2000);
        long waitedMs = (System.nanoTime() - start) / 1_000_000;

        // then: it returns right after the key arrives, not at the timeout
        assertNotNull(key);
        assertTrue(waitedMs < 500, "took " + waitedMs + " ms");
    }

    @Test
    void reportsPendingKeysSoTheRendererCanCoalesceFrames() throws InterruptedException {
        // given: three keys typed at once (a paste or a held arrow)
        FakeTty tty = new FakeTty();
        KeyPump pump = new KeyPump(new KeyDecoder(tty));
        assertFalse(pump.hasPending());
        tty.type("abc");

        // when: the first is taken
        pump.take();
        Thread.sleep(50);

        // then: the rest are visibly waiting
        assertTrue(pump.hasPending());
    }
}
