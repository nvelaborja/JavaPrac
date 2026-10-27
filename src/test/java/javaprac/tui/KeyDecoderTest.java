package javaprac.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;
import javaprac.tui.KeyDecoder.Key;
import javaprac.tui.KeyDecoder.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KeyDecoderTest {

    /** Feeds a fixed byte sequence; {@code peek} reports a timeout once the input is exhausted. */
    private static KeyDecoder decoderFor(String input) {
        Deque<Integer> queue = new ArrayDeque<>();
        input.chars().forEach(queue::add);
        return new KeyDecoder(new KeyDecoder.Source() {
            @Override
            public int read() {
                return queue.isEmpty() ? KeyDecoder.EOF : queue.removeFirst();
            }

            @Override
            public int peek(long timeoutMs) {
                return queue.isEmpty() ? KeyDecoder.TIMED_OUT : queue.peekFirst();
            }
        });
    }

    static Stream<Arguments> sequences() {
        return Stream.of(
                Arguments.of("\u001b[A", Type.UP),
                Arguments.of("\u001b[B", Type.DOWN),
                Arguments.of("\u001b[C", Type.RIGHT),
                Arguments.of("\u001b[D", Type.LEFT),
                Arguments.of("\u001b[H", Type.HOME),
                Arguments.of("\u001b[F", Type.END),
                Arguments.of("\u001bOH", Type.HOME),
                Arguments.of("\u001b[3~", Type.DELETE),
                Arguments.of("\u001b[1;5C", Type.WORD_RIGHT),   // Ctrl-Right
                Arguments.of("\u001b[1;5D", Type.WORD_LEFT),    // Ctrl-Left
                Arguments.of("\u001b[1;3D", Type.WORD_LEFT),    // Alt-Left
                Arguments.of("\u001b[1;3C", Type.WORD_RIGHT),   // Alt-Right
                Arguments.of("\u001b[1;9D", Type.HOME),         // Meta/Cmd-Left
                Arguments.of("\u001b[1;9C", Type.END),          // Meta/Cmd-Right
                Arguments.of("\u001b[1;2C", Type.RIGHT),        // Shift-Right is a plain arrow
                Arguments.of("\u001b[3;3~", Type.DELETE_WORD_FORWARD),
                Arguments.of("\u001b[3;5~", Type.DELETE_WORD_FORWARD),
                Arguments.of("\u001bb", Type.WORD_LEFT),        // Option-Left in Terminal.app and iTerm2 presets
                Arguments.of("\u001bf", Type.WORD_RIGHT),
                Arguments.of("\u001bd", Type.DELETE_WORD_FORWARD),
                Arguments.of("\u001b\u007f", Type.DELETE_WORD_BACK),  // Option-Backspace
                Arguments.of("\u0017", Type.DELETE_BLANK_WORD_BACK),   // Ctrl-W
                Arguments.of("\u0015", Type.DELETE_TO_LINE_START),     // Ctrl-U
                Arguments.of("\u0002", Type.LEFT),                     // Ctrl-B
                Arguments.of("\u0006", Type.RIGHT),                    // Ctrl-F
                Arguments.of("\u0007", Type.HINT),                     // Ctrl-G
                Arguments.of("\u001b", Type.ESCAPE),
                Arguments.of("\r", Type.ENTER),
                Arguments.of("\u007f", Type.BACKSPACE),
                Arguments.of("\t", Type.TAB),
                Arguments.of("\u0004", Type.SUBMIT),
                Arguments.of("\u000b", Type.DELETE_TO_LINE_END),   // Ctrl-K, as in a shell
                Arguments.of("\u0012", Type.REVEAL),
                Arguments.of("\u000e", Type.SKIP),
                Arguments.of("\u0011", Type.QUIT));
    }

    @ParameterizedTest
    @MethodSource("sequences")
    void decodesControlAndEscapeSequences(String input, Type expected) throws IOException {
        // given: raw terminal bytes
        // when: decoded
        Key key = decoderFor(input).next();

        // then: the right logical key
        assertEquals(expected, key.type());
    }

    @Test
    void consumesAWholeEscapeSequenceSoNoStrayCharactersLeakIntoTheEditor() throws IOException {
        // given: an arrow key followed by a letter
        KeyDecoder decoder = decoderFor("\u001b[Ax");

        // when: two keys are read
        Key first = decoder.next();
        Key second = decoder.next();

        // then: the arrow is one key, and 'x' is the only character
        assertEquals(Type.UP, first.type());
        assertEquals(Type.CHAR, second.type());
        assertEquals('x', second.ch());
    }

    @Test
    void decodesPrintableCharactersWithTheirCodePoint() throws IOException {
        Key key = decoderFor("x").next();
        assertEquals(Type.CHAR, key.type());
        assertEquals('x', key.ch());
    }

    @Test
    void endOfInputIsTreatedAsQuit() throws IOException {
        assertEquals(Type.QUIT, decoderFor("").next().type());
    }
}
