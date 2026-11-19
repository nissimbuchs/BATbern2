package ch.batbern.companyuser.domain;

import java.util.Locale;
import java.util.List;

/**
 * The profile language rules of CUMS (#1222 items 5 and 6), one place for what JIT provisioning,
 * the nightly reconciliation and every mail service did on their own.
 *
 * <ul>
 *   <li>{@link #SUPPORTED}: the 10 UI languages, the users-api {@code language} enum; Swiss German
 *       is {@code gsw-BE} ({@code pref_language} is {@code VARCHAR(10)} since V34).</li>
 *   <li>{@link #normalize}: a raw tag from the sign-up form or a federated login to a supported
 *       code, or null (the {@code @PrePersist} default applies).</li>
 *   <li>{@link #mailLanguage}: mails exist in German and English only. German and Swiss German get
 *       German, every other language English: the same rule as EMS {@code MailLocales}.</li>
 * </ul>
 */
public final class UserLanguages {

    public static final String SWISS_GERMAN = "gsw-BE";

    /** The one list; a test checks it against the generated users-api {@code LanguageEnum}. */
    public static final List<String> SUPPORTED =
            List.of("de", "en", "fr", "it", "rm", "es", "fi", "nl", "ja", SWISS_GERMAN);

    /** The default when a stored or sent value is not one of {@link #SUPPORTED}. */
    public static final String DEFAULT = "de";

    private UserLanguages() {
    }

    /**
     * A supported language code for a raw tag, or null. Any {@code gsw} tag is Swiss German
     * ({@code gsw-BE}); otherwise the primary subtag counts ({@code fr-CH} is {@code fr}).
     * Non-strings coerced to text ({@code "123"}), casing and whitespace are tolerated.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String primary = raw.trim().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        if ("gsw".equals(primary)) {
            return SWISS_GERMAN;
        }
        return SUPPORTED.contains(primary) ? primary : null;
    }

    /**
     * {@code "de"} or {@code "en"}, the template language of a mail to a user with this profile
     * language; {@code fallback} when there is none.
     */
    public static String mailLanguage(String preference, String fallback) {
        if (preference == null || preference.isBlank()) {
            return fallback;
        }
        String lower = preference.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("de") || lower.startsWith("gsw") ? "de" : "en";
    }
}
