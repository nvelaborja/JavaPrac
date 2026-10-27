package javaprac.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** An append-only log of finished challenges, one JSON object per line (JSON is also valid YAML, so SnakeYAML reads it). */
public final class History {
    public record Entry(Instant at, String id, boolean passed, long seconds, int attempts, int hints) {
        /** The topic is the part of the id before the first dot, e.g. {@code streams}. */
        public String topic() {
            int dot = id.indexOf('.');
            return dot < 0 ? id : id.substring(0, dot);
        }

        String toJson() {
            return "{\"at\":\"" + at + "\",\"id\":\"" + id.replace("\"", "") + "\",\"passed\":" + passed
                    + ",\"seconds\":" + seconds + ",\"attempts\":" + attempts + ",\"hints\":" + hints + "}";
        }
    }

    private final Path file;

    public History(Path file) {
        this.file = file;
    }

    /** {@code $JAVAPRAC_HOME/history.jsonl}, defaulting to {@code ~/.javaprac/history.jsonl}. */
    public static Path defaultFile() {
        String home = System.getenv("JAVAPRAC_HOME");
        Path dir = home != null && !home.isBlank() ? Path.of(home) : Path.of(System.getProperty("user.home"), ".javaprac");
        return dir.resolve("history.jsonl");
    }

    public void append(Entry entry) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.toJson() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write history to " + file, e);
        }
    }

    public List<Entry> readAll() {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Object parsed = yaml.load(line);
                if (parsed instanceof Map<?, ?> m) {
                    entries.add(new Entry(
                            Instant.parse(m.get("at").toString()),
                            m.get("id").toString(),
                            Boolean.parseBoolean(m.get("passed").toString()),
                            ((Number) m.get("seconds")).longValue(),
                            ((Number) m.get("attempts")).intValue(),
                            ((Number) m.get("hints")).intValue()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read history from " + file, e);
        }
        return entries;
    }
}
