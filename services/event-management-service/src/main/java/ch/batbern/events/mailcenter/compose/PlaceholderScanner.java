package ch.batbern.events.mailcenter.compose;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Mustache placeholders left in a rendered mail (Story 16.8): {@code {{name}}},
 * {@code {{#name}}}, {@code {{/name}}}. A leftover means the template uses a variable this audience
 * does not provide. A warning for templates; Story 16.5 makes it blocking for own text.
 */
public final class PlaceholderScanner {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[#/]?[A-Za-z0-9_.-]+}}");

    private PlaceholderScanner() {
    }

    /** Distinct leftovers in order of first appearance; null parts are skipped. */
    public static List<String> scan(String... parts) {
        Set<String> found = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            Matcher matcher = PLACEHOLDER.matcher(part);
            while (matcher.find()) {
                found.add(matcher.group());
            }
        }
        return List.copyOf(found);
    }
}
