package javaprac.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import javaprac.store.History.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryTest {

    @Test
    void roundTripsEntriesInOrderAndCreatesTheDirectory(@TempDir Path dir) {
        // given: a history file in a directory that doesn't exist yet
        History history = new History(dir.resolve("nested").resolve("history.jsonl"));
        Instant at = Instant.parse("2026-10-27T12:00:00Z");

        // when: two entries are appended and read back
        history.append(new Entry(at, "streams.limit-first-n", true, 42, 3, 1, true));
        history.append(new Entry(at.plusSeconds(60), "types.record-point", false, 7, 0, 0, false));

        // then: both come back intact and in order
        var entries = history.readAll();
        assertEquals(2, entries.size());
        assertEquals(new Entry(at, "streams.limit-first-n", true, 42, 3, 1, true), entries.get(0));
        assertEquals("types", entries.get(1).topic());
    }

    @Test
    void readsEntriesWrittenBeforeTheRevealFieldExisted(@TempDir Path dir) throws Exception {
        // given: a v1 history line with no "revealed" field
        Path file = dir.resolve("history.jsonl");
        java.nio.file.Files.writeString(file,
                "{\"at\":\"2026-10-27T12:00:00Z\",\"id\":\"a.b\",\"passed\":true,\"seconds\":5,\"attempts\":1,\"hints\":0}\n");

        // when / then: it reads as not revealed
        assertEquals(false, new History(file).readAll().getFirst().revealed());
    }

    @Test
    void anAbsentFileMeansNoHistory(@TempDir Path dir) {
        assertTrue(new History(dir.resolve("missing.jsonl")).readAll().isEmpty());
    }
}
