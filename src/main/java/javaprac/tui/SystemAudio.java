package javaprac.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javaprac.tui.Sounds.Cue;

/**
 * Plays the macOS system sounds through one audio line that stays open for the whole session.
 *
 * <p>Launching {@code afplay} per sound cost 0.6 to 1 s of audio-device startup every time, so the buzzer arrived long
 * after the red flash. Writing already-decoded samples into an open line starts in a few milliseconds. If the files or
 * an audio device aren't available this silently does nothing; the terminal bell still fires.
 */
final class SystemAudio implements Sounds.Output {
    private static final AudioFormat FORMAT =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48_000f, 16, 2, 4, 48_000f, false);
    /** About 50 ms of audio: small enough that playback starts promptly, big enough not to stutter. */
    private static final int BUFFER_BYTES = 48_000 / 20 * 4;
    private static final Map<Cue, Path> FILES = Map.of(
            Cue.BUZZ, Path.of("/System/Library/Sounds/Basso.aiff"),
            Cue.CHIME, Path.of("/System/Library/Sounds/Glass.aiff"));

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "javaprac-audio");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Map<Cue, byte[]> samples = Map.of();
    private volatile SourceDataLine line;

    SystemAudio() {
        // decoding and opening the device takes a few hundred ms, which must not delay the first screen
        worker.execute(this::init);
    }

    private void init() {
        try {
            Map<Cue, byte[]> loaded = new EnumMap<>(Cue.class);
            for (Map.Entry<Cue, Path> file : FILES.entrySet()) {
                if (Files.exists(file.getValue())) {
                    loaded.put(file.getKey(), decode(file.getValue()));
                }
            }
            if (loaded.isEmpty()) {
                return;
            }
            SourceDataLine opened = AudioSystem.getSourceDataLine(FORMAT);
            opened.open(FORMAT, BUFFER_BYTES);
            opened.start();
            samples = loaded;
            line = opened; // published last: a non-null line implies the samples are ready
        } catch (Exception | LinkageError e) {
            // no audio device or an unsupported format: stay silent
        }
    }

    private static byte[] decode(Path file) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile());
             AudioInputStream converted = AudioSystem.getAudioInputStream(FORMAT, source)) {
            return converted.readAllBytes();
        } catch (IOException e) {
            throw new IOException("Cannot decode " + file, e);
        }
    }

    @Override
    public void play(Cue cue) {
        SourceDataLine open = line;
        byte[] data = open == null ? null : samples.get(cue);
        if (data == null) {
            return;
        }
        open.flush(); // cut off a sound that is still playing instead of queueing behind it
        worker.execute(() -> open.write(data, 0, data.length));
    }
}
