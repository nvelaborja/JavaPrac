package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeyInspectorTest {

    @Test
    void showsTheBytesAndHowTheKeyIsRead() {
        // given: the bytes of Option-Left in a terminal configured to send Alt-as-Meta
        List<Integer> bytes = List.of(0x1b, 0x62);

        // when: described
        String line = KeyInspector.describe(bytes, KeyInspector.decode(bytes));

        // then: hex, a readable form, and the meaning
        assertTrue(line.startsWith("1b 62"), line);
        assertTrue(line.contains("ESC b"), line);
        assertTrue(line.endsWith("WORD_LEFT"), line);
    }

    @Test
    void flagsKeysTheEditorIgnores() {
        List<Integer> bytes = List.of(0x1b, 0x5b, 0x35, 0x7e);   // PageUp

        String line = KeyInspector.describe(bytes, KeyInspector.decode(bytes));

        assertTrue(line.contains("UNKNOWN") && line.contains("ignored by the editor"), line);
    }
}
