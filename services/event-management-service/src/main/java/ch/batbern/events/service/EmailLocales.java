package ch.batbern.events.service;

import java.util.Locale;

/**
 * Locale for a mail from a user's language preference. Email templates exist in German and
 * English only (CLAUDE.md, Localization): any {@code de*} preference (incl. {@code gsw-BE}) is
 * German, no preference is German (the primary audience), every other language is English.
 */
final class EmailLocales {

    private EmailLocales() {
    }

    static Locale fromPreference(String preference) {
        if (preference == null || preference.isBlank()) {
            return Locale.GERMAN;
        }
        String lower = preference.toLowerCase(Locale.ROOT);
        return lower.startsWith("de") || lower.startsWith("gsw") ? Locale.GERMAN : Locale.ENGLISH;
    }
}
