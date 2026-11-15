package ch.batbern.events.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The EMS module of a class by its simple name, as the architecture review's scripts assign it. The
 * rules are not copied here: they live in {@code scripts/architecture/ems-modules.json}, which
 * {@code ems-module-deps.py} (ordered keyword rules and overrides on the top-level class name, the
 * package only for watch, usermerge, notification and the mail center) and {@code ems-module-target.py}
 * (target regexes, renames) read as well. This class only matches them, the same way
 * {@code scripts/architecture/ems_modules.py} does; the file's {@code examples} are asserted on both sides
 * ({@code EmsModuleNamesSharedRulesTest}, {@code test_ems_modules.py}).
 */
final class EmsModuleNames {

    static final String EVENT_CORE = "event_core";
    static final String PLATFORM = "platform";
    static final String RULES_FILE = "scripts/architecture/ems-modules.json";

    private static final JsonNode RULES = load(rulesFile());

    private EmsModuleNames() {
    }

    /** {@code scripts/architecture/ems-modules.json}, found from {@code user.dir} upwards. */
    static Path rulesFile() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path d = dir; d != null; d = d.getParent()) {
            Path candidate = d.resolve(RULES_FILE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(RULES_FILE + " not found from " + dir + " upwards");
    }

    static JsonNode load(Path file) {
        try {
            return new ObjectMapper().readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /**
     * The module of a top-level EMS class.
     *
     * @param firstPackage the first package segment below the EMS root ("" for a class in the root)
     * @param simpleName   the top-level class's simple name
     */
    static String moduleOf(String firstPackage, String simpleName) {
        return moduleOf(RULES, firstPackage, simpleName);
    }

    /** As {@link #moduleOf(String, String)}, with the given rules (the content of an ems-modules.json). */
    static String moduleOf(JsonNode rules, String firstPackage, String simpleName) {
        String module = PLATFORM;
        for (JsonNode rule : rules.get("rules")) {
            if (anyMatch(rule.get("packages"), firstPackage::startsWith)
                    || anyMatch(rule.get("startsWith"), simpleName::startsWith)
                    || anyMatch(rule.get("contains"), simpleName::contains)) {
                module = rule.get("module").asText();
                break;
            }
        }
        JsonNode override = rules.get("overrides").get(simpleName);
        if (override != null) {
            module = override.asText();
        }
        for (JsonNode target : rules.get("target")) {
            if (anyMatch(target.get("patterns"), regex -> Pattern.matches(regex, simpleName))) {
                module = target.get("module").asText();
            }
        }
        JsonNode renamed = rules.get("renames").get(module);
        return renamed == null ? module : renamed.asText();
    }

    private static boolean anyMatch(JsonNode values, Predicate<String> test) {
        for (JsonNode value : values) {
            if (test.test(value.asText())) {
                return true;
            }
        }
        return false;
    }
}
