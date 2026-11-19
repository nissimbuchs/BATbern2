package ch.batbern.events.mailcenter.render;

import java.util.Locale;

/**
 * The one rule for a mail's language (#1222, CLAUDE.md "Localization"): email templates exist in
 * German and English only. Any {@code de*} or {@code gsw*} preference (Swiss German, owner
 * 2026-11-15) is German, no preference is German (the primary audience), every other language is
 * English. {@link MailPipeline} applies it to every template it loads; services that pick the
 * language from a user's preference use it too, so the two never disagree.
 */
public final class MailLocales {

    private MailLocales() {
    }

    /** {@link Locale#GERMAN} or {@link Locale#ENGLISH} for a language preference or tag. */
    public static Locale fromPreference(String preference) {
        if (preference == null || preference.isBlank()) {
            return Locale.GERMAN;
        }
        String lower = preference.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("de") || lower.startsWith("gsw") ? Locale.GERMAN : Locale.ENGLISH;
    }

    /** {@code "de"} or {@code "en"}: the template locale for a language preference or tag. */
    public static String languageOf(String preference) {
        return fromPreference(preference).getLanguage();
    }
}
