package javaprac.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads the bundled challenge files listed in {@code /challenges/manifest.txt}. */
public final class ChallengeLoader {
    private static final String MANIFEST = "/challenges/manifest.txt";

    private ChallengeLoader() {}

    public static List<Challenge> loadBundled() {
        List<Challenge> all = new ArrayList<>();
        for (String file : readResource(MANIFEST).lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList()) {
            all.addAll(parse(readResource("/challenges/" + file), file));
        }
        Set<String> seen = new HashSet<>();
        for (Challenge c : all) {
            if (!seen.add(c.id())) {
                throw new IllegalArgumentException("Duplicate challenge id: " + c.id());
            }
        }
        return List.copyOf(all);
    }

    public static List<Challenge> parse(String yaml, String source) {
        Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (!(root instanceof List<?> items)) {
            throw new IllegalArgumentException(source + ": expected a top-level list of challenges");
        }
        List<Challenge> out = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(source + ": every challenge must be a mapping");
            }
            out.add(toChallenge(map));
        }
        return out;
    }

    private static Challenge toChallenge(Map<?, ?> m) {
        String id = text(m, "id", null, "?");
        Challenge.Kind kind = Challenge.Kind.valueOf(text(m, "kind", null, id).toUpperCase(Locale.ROOT).replace('-', '_'));
        List<Challenge.Case> cases = cases(m, id);
        List<String> hints = strings(m, "hints");
        String reference = text(m, "reference", null, id);

        if (hints.isEmpty()) {
            throw new IllegalArgumentException(id + ": at least one hint is required");
        }
        if (cases.isEmpty() || cases.stream().anyMatch(c -> c.asserts().isEmpty())) {
            throw new IllegalArgumentException(id + ": every challenge needs at least one case with asserts");
        }
        return new Challenge(
                id,
                text(m, "title", null, id),
                strings(m, "tags"),
                m.get("difficulty") instanceof Integer d ? d : 1,
                kind,
                text(m, "prompt", null, id).strip(),
                text(m, "given", "", id).stripTrailing(),
                text(m, "starter", "", id),
                cases,
                structure(m),
                hints,
                reference.stripTrailing(),
                strings(m, "rejects"));
    }

    private static List<Challenge.Case> cases(Map<?, ?> m, String id) {
        List<Challenge.Case> cases = new ArrayList<>();
        if (m.get("cases") instanceof List<?> list) {
            for (Object o : list) {
                Map<?, ?> cm = (Map<?, ?>) o;
                String given = cm.get("given") == null ? null : cm.get("given").toString().stripTrailing();
                cases.add(new Challenge.Case(given, strings(cm, "asserts")));
            }
        } else if (m.get("asserts") != null) {
            cases.add(new Challenge.Case(null, strings(m, "asserts")));
        }
        return cases;
    }

    private static Challenge.Structure structure(Map<?, ?> m) {
        if (!(m.get("structure") instanceof Map<?, ?> sm)) {
            return Challenge.Structure.NONE;
        }
        return new Challenge.Structure(strings(sm, "require"), strings(sm, "forbid"));
    }

    private static String text(Map<?, ?> m, String key, String fallback, String id) {
        Object v = m.get(key);
        if (v == null) {
            if (fallback == null) {
                throw new IllegalArgumentException(id + ": missing required field '" + key + "'");
            }
            return fallback;
        }
        return v.toString();
    }

    private static List<String> strings(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of(v.toString());
    }

    private static String readResource(String path) {
        try (InputStream in = ChallengeLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}
