package ch.batbern.shared.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds log calls that pass an e-mail address unmasked (#1184).
 *
 * <p>Production logs go to CloudWatch, where everyone with log access reads them, for as long as the
 * log group keeps them. An address of a participant, subscriber or speaker is personal data and must
 * be logged through {@code LoggingUtils.maskEmail(...)} ({@code a***@example.ch}).
 *
 * <p>A source scan, not a runtime check: every {@code log.trace/debug/info/warn/error(...)} call in
 * {@code src/main/java}; each argument after the message is flagged when its expression looks like
 * an address ({@code email}, {@code getEmail()}, {@code recipient}, {@code to}, {@code address},
 * {@code fromAddress} ...) and it is not wrapped in a masking call ({@code maskEmail}, {@code mask*},
 * {@code getMasked*}). Counts, flags, ids and keys are not addresses. A line that must log an address
 * on purpose (local dev only) carries the comment {@code log-address-ok} with the reason.
 */
public final class LogAddressGuard {

    private static final Pattern LOG_CALL =
            Pattern.compile("\\b(log|LOG|logger|LOGGER)\\.(trace|debug|info|warn|error)\\s*\\(");

    private static final Pattern ADDRESS = Pattern.compile(
            "(?i)(^|[^a-z])(e?mail|email\\w*|address(es)?|recipients?|attendeeEmail|fromAddress|replyTo|to)"
                    + "\\s*(\\(\\))?$"
                    // camel case: normalizedEmail, primaryEmail(), recipientAddress
                    + "|(?-i:[a-z0-9](Email|Emails|Address|Addresses))\\s*(\\(\\))?$"
                    + "|getEmail\\(\\)|getAttendeeEmail\\(\\)|Email\\(\\)$|getAddress\\(\\)|getTo\\(\\)"
                    + "|\\.to\\(\\)|\\.from\\(\\)|fromAddress\\(\\)|address\\(\\)");

    private static final Pattern NOT_AN_ADDRESS = Pattern.compile(
            "(?i)mask\\w*\\(|getMasked|count|size\\(\\)|verified|enabled|template|key\\b|id\\b|Id\\(\\)"
                    + "|subject|type|status|length|isEmpty|domain");

    private static final String OPT_OUT = "log-address-ok";

    private LogAddressGuard() {
    }

    /**
     * @param sourceRoot e.g. {@code src/main/java} of the module under test
     * @return one line per unmasked address argument, {@code "path:line: expression"}; empty if none
     */
    public static List<String> unmaskedAddressLogs(Path sourceRoot) {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                scan(sourceRoot.relativize(file).toString(), Files.readString(file), found);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    /**
     * Framework loggers at DEBUG or TRACE that production would run with: the base and the
     * {@code staging} document of {@code application.yml} (production runs the {@code staging}
     * profile), and every {@code <logger>} in {@code logback-spring.xml} (Spring Boot applies
     * {@code logging.level} after the XML, but only for the names it lists; a more specific XML
     * logger keeps its level). {@code ch.batbern} itself is set by {@code LOG_LEVEL} and allowed.
     * Seen 2026-11-03: Spring Security DEBUG logged every request URL, addresses included.
     *
     * @param resources e.g. {@code src/main/resources}
     */
    public static List<String> verboseProductionLevels(Path resources) {
        List<String> found = new ArrayList<>();
        try {
            Path yml = resources.resolve("application.yml");
            if (Files.exists(yml)) {
                for (Object doc : new org.yaml.snakeyaml.Yaml().loadAll(Files.readString(yml))) {
                    if (!(doc instanceof java.util.Map<?, ?> map)) {
                        continue;
                    }
                    String profile = profileOf(map);
                    if (!profile.equals("base") && !profile.equals("staging")) {
                        continue;
                    }
                    Object levels = path(map, "logging", "level");
                    if (levels instanceof java.util.Map<?, ?> lv) {
                        lv.forEach((k, v) -> {
                            if (verbose(String.valueOf(v)) && !String.valueOf(k).equals("ch.batbern")) {
                                found.add("application.yml [" + profile + "]: " + k + "=" + v);
                            }
                        });
                    }
                }
            }
            Path xml = resources.resolve("logback-spring.xml");
            if (Files.exists(xml)) {
                Matcher m = Pattern.compile("<logger\\s+name=\"([^\"]+)\"\\s+level=\"(\\w+)\"")
                        .matcher(Files.readString(xml));
                while (m.find()) {
                    if (verbose(m.group(2))) {
                        found.add("logback-spring.xml: " + m.group(1) + "=" + m.group(2));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static boolean verbose(String level) {
        return level.equalsIgnoreCase("DEBUG") || level.equalsIgnoreCase("TRACE");
    }

    private static String profileOf(java.util.Map<?, ?> doc) {
        Object p = path(doc, "spring", "config", "activate", "on-profile");
        return p == null ? "base" : String.valueOf(p);
    }

    private static Object path(java.util.Map<?, ?> map, String... keys) {
        Object cur = map;
        for (String k : keys) {
            if (!(cur instanceof java.util.Map<?, ?> m)) {
                return null;
            }
            cur = m.get(k);
        }
        return cur;
    }

    static void scan(String name, String source, List<String> found) {
        Matcher call = LOG_CALL.matcher(source);
        while (call.find()) {
            int open = call.end() - 1;
            List<String> args = arguments(source, open);
            int line = lineOf(source, call.start());
            String lineText = source.split("\n", -1)[line - 1];
            int end = endOfCall(source, open);
            String callText = source.substring(call.start(), Math.min(end + 1, source.length()));
            if (lineText.contains(OPT_OUT) || callText.contains(OPT_OUT)) {
                continue;
            }
            List<String> values = args.subList(Math.min(1, args.size()), args.size()).stream()
                    .map(String::strip).toList();
            // "from {} to {}" with arguments named from and to is a transition (states), not mail
            boolean transition = values.contains("from") && values.contains("to");
            for (String expression : values) {
                if (expression.matches("e|ex|exception") || NOT_AN_ADDRESS.matcher(expression).find()
                        || (transition && expression.equals("to"))) {
                    continue;
                }
                if (ADDRESS.matcher(expression).find()) {
                    found.add(name + ":" + line + ": " + expression);
                }
            }
        }
    }

    /** The top-level arguments of the call whose opening parenthesis is at {@code open}. */
    static List<String> arguments(String source, int open) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == '\\' && i + 1 < source.length()) {
                    current.append(source.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
                if (depth > 1) {
                    current.append(c);
                }
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    parts.add(current.toString());
                    return parts;
                }
                current.append(c);
            } else if (c == ',' && depth == 1) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        return parts;
    }

    private static int endOfCall(String source, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return source.length() - 1;
    }

    private static int lineOf(String source, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
