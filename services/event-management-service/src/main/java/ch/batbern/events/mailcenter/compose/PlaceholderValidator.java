package ch.batbern.events.mailcenter.compose;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Story 16.5 AC2: every {@code {{name}}}, {@code {{#name}}} and {@code {{/name}}} in own text must be
 * a variable of the audience. {@code EmailService.replaceVariables} only replaces the exact
 * {@code {{name}}} text and leaves anything else in the mail, so {@code {{ name }}} (spaces) or a
 * typo would reach the recipient literally.
 */
public final class PlaceholderValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([#/^]?)([^{}]*)}}");

    private PlaceholderValidator() {
    }

    /** The placeholder names in {@code texts} that {@code allowed} does not contain, in order of appearance. */
    public static List<String> unknown(Set<String> allowed, String... texts) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(2);
                if (!allowed.contains(name)) {
                    unknown.add(name);
                }
            }
        }
        return List.copyOf(unknown);
    }
}
