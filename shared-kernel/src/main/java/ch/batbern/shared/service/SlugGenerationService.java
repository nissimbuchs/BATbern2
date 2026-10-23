package ch.batbern.shared.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Service for generating URL-friendly slugs and usernames.
 *
 * Handles:
 * - Username generation from first/last names
 * - German character conversion (ä→ae, ö→oe, ü→ue, ß→ss)
 * - Collision detection and resolution with numeric suffixes
 * - Session title slugification
 */
@Service
public class SlugGenerationService {

    /**
     * The only shape a username may have. Identical to the DB constraint {@code chk_username_format}
     * (CUMS V4) and the {@code username} pattern in the OpenAPI specs.
     */
    public static final String USERNAME_PATTERN = "^[a-z]+\\.[a-z]+(\\.[0-9]+)?$";

    /** Longest name part kept in a username; two parts plus the dot is the longest base username. */
    public static final int MAX_USERNAME_PART_LENGTH = 40;
    public static final int MAX_BASE_USERNAME_LENGTH = 2 * MAX_USERNAME_PART_LENGTH + 1;

    private static final Map<Integer, String> LATIN_WITHOUT_DECOMPOSITION = Map.ofEntries(
        Map.entry((int) 'ß', "ss"), Map.entry((int) 'æ', "ae"), Map.entry((int) 'œ', "oe"),
        Map.entry((int) 'ø', "o"), Map.entry((int) 'ł', "l"), Map.entry((int) 'đ', "d"),
        Map.entry((int) 'ð', "d"), Map.entry((int) 'þ', "th"), Map.entry((int) 'ı', "i"),
        Map.entry((int) 'ħ', "h"), Map.entry((int) 'ŋ', "n"), Map.entry((int) 'ŧ', "t"),
        Map.entry((int) 'ĸ', "k"), Map.entry((int) 'ſ', "s"));

    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Generates a username from first and last names.
     *
     * <p>Total for any non-blank name (#1102): the result always satisfies
     * {@link #USERNAME_PATTERN}. Names whose letters cannot be spelled in ASCII (Cyrillic, CJK, ...)
     * fall back to {@code user.<other part>} or {@code user.unknown}; use
     * {@link #generateUsername(String, String, String)} to fall back to the email instead.
     *
     * Example: "Müller" + "Özdemir" → "mueller.oezdemir", "Jan" + "van der Berg" → "jan.vanderberg"
     *
     * @param firstName The user's first name
     * @param lastName The user's last name
     * @return A username matching {@link #USERNAME_PATTERN}
     * @throws IllegalArgumentException if firstName or lastName is null/empty
     */
    public String generateUsername(String firstName, String lastName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name cannot be null or empty");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name cannot be null or empty");
        }
        return generateUsername(firstName, lastName, null);
    }

    /**
     * Generates a username from first and last names, with email fallback.
     * ADR-005: Used for anonymous user registration where names might be unavailable.
     *
     * <p>Names are free text and may be anything a person is called (#1102). The username is the
     * public meaningful ID (ADR-003), so it stays an ASCII slug: {@link #USERNAME_PATTERN}, which
     * is also the DB constraint {@code chk_username_format}. This method never returns anything
     * else, whatever the input:
     * <ol>
     *   <li>both name parts spell something in ASCII → {@code first.last}
     *       ("Hông Ân" + "Meier" → {@code hongan.meier}; inner spaces are dropped, not dotted)</li>
     *   <li>otherwise the email local part, without a {@code +tag}
     *       ({@code taro.yamada@…} → {@code taro.yamada}, {@code tyamada@…} → {@code user.tyamada})</li>
     *   <li>otherwise {@code user.<the part that did spell>}, finally {@code user.unknown}</li>
     * </ol>
     * Each part is capped at {@value #MAX_USERNAME_PART_LENGTH} characters so the collision
     * suffix still fits {@code username VARCHAR(100)}.
     *
     * @param firstName The user's first name (can be null/empty)
     * @param lastName The user's last name (can be null/empty)
     * @param email The user's email address (fallback if names do not spell in ASCII; can be null)
     * @return A username matching {@link #USERNAME_PATTERN}
     */
    public String generateUsername(String firstName, String lastName, String email) {
        String first = slugifyName(firstName);
        String last = slugifyName(lastName);
        if (!first.isEmpty() && !last.isEmpty()) {
            return first + "." + last;
        }

        String fromEmail = usernameFromEmail(email);
        if (fromEmail != null) {
            return fromEmail;
        }

        String onePart = !first.isEmpty() ? first : last;
        return "user." + (onePart.isEmpty() ? "unknown" : onePart);
    }

    /** Derives {@code a.bc} from local part {@code a.b.c+tag}; null if it has no ASCII letters. */
    private String usernameFromEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        String local = at < 0 ? email : email.substring(0, at);
        int plus = local.indexOf('+');
        if (plus >= 0) {
            local = local.substring(0, plus);
        }
        // Digits are dropped, not treated as separators: e2e-test -> ee.test, john.doe2 -> john.doe
        String[] tokens = toAsciiLetters(local.replaceAll("[0-9]", ""), " ").trim().split(" +");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return null;
        }
        String head = cap(tokens[0]);
        if (tokens.length == 1) {
            return "user." + head;
        }
        return head + "." + cap(String.join("", Arrays.copyOfRange(tokens, 1, tokens.length)));
    }

    /**
     * Ensures a username is unique by appending numeric suffixes if needed.
     *
     * If baseUsername exists, tries baseUsername.2, baseUsername.3, etc.
     *
     * @param baseUsername The base username to check
     * @param existsChecker Function that returns true if a username exists
     * @return A unique username
     * @throws IllegalArgumentException if baseUsername or existsChecker is null
     */
    public String ensureUniqueUsername(String baseUsername, Function<String, Boolean> existsChecker) {
        if (baseUsername == null || baseUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Base username cannot be null or empty");
        }
        if (existsChecker == null) {
            throw new IllegalArgumentException("Exists checker function cannot be null");
        }

        if (!existsChecker.apply(baseUsername)) {
            return baseUsername;
        }

        int suffix = 2;
        String candidate;
        do {
            candidate = baseUsername + "." + suffix;
            suffix++;
        } while (existsChecker.apply(candidate));

        return candidate;
    }

    /**
     * Ensures a slug is unique by appending numeric suffixes if needed.
     * Uses hyphen separator (e.g., "slug-2", "slug-3") instead of dot.
     *
     * @param baseSlug The base slug to check
     * @param existsChecker Function that returns true if a slug exists
     * @return A unique slug
     * @throws IllegalArgumentException if baseSlug or existsChecker is null
     */
    public String ensureUniqueSlug(String baseSlug, Function<String, Boolean> existsChecker) {
        if (baseSlug == null || baseSlug.trim().isEmpty()) {
            throw new IllegalArgumentException("Base slug cannot be null or empty");
        }
        if (existsChecker == null) {
            throw new IllegalArgumentException("Exists checker function cannot be null");
        }

        if (!existsChecker.apply(baseSlug)) {
            return baseSlug;
        }

        int suffix = 2;
        String candidate;
        do {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        } while (existsChecker.apply(candidate));

        return candidate;
    }

    /**
     * Generates a URL-friendly slug from a session title.
     *
     * Rules:
     * - Converts to lowercase
     * - Replaces spaces with hyphens
     * - Converts German characters
     * - Removes special characters
     * - Removes leading/trailing hyphens
     * - Collapses multiple hyphens to single
     *
     * Example: "Introduction to Spring Boot & Microservices" → "introduction-to-spring-boot-microservices"
     *
     * @param title The session title
     * @return A URL-friendly slug
     * @throws IllegalArgumentException if title is null/empty
     */
    public String generateSessionSlug(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Session title cannot be null or empty");
        }

        // Convert to lowercase
        String slug = title.toLowerCase().trim();

        // Replace German characters
        slug = replaceGermanCharacters(slug);

        // Normalize to remove accents (backup for any remaining diacritics)
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD);
        slug = slug.replaceAll("\\p{M}", "");

        // Replace whitespace with hyphens
        slug = WHITESPACE.matcher(slug).replaceAll("-");

        // Remove non-alphanumeric characters (except hyphens)
        slug = NON_ALPHANUMERIC.matcher(slug).replaceAll("");

        // Collapse multiple hyphens
        slug = MULTIPLE_HYPHENS.matcher(slug).replaceAll("-");

        // Remove leading/trailing hyphens
        slug = slug.replaceAll("^-+|-+$", "");

        return slug;
    }

    /**
     * Slugifies a name part (first or last name) into {@code [a-z]*}, at most
     * {@value #MAX_USERNAME_PART_LENGTH} characters. Empty when nothing spells in ASCII.
     */
    private String slugifyName(String name) {
        return name == null ? "" : cap(toAsciiLetters(name, ""));
    }

    /**
     * Spells {@code text} with {@code [a-z]} only: lower-cases, transliterates German and other
     * Latin letters that Unicode does not decompose (ß, æ, ø, ł, đ, þ, ...), strips accents via
     * NFKD (which also unfolds ligatures like ﬁ and ǆ), and replaces every other character run
     * with {@code separator}.
     */
    private String toAsciiLetters(String text, String separator) {
        // NFC first: decomposed input (u + U+0308, as macOS can produce) must transliterate like ü.
        String slug = Normalizer.normalize(text, Normalizer.Form.NFC);
        slug = replaceGermanCharacters(slug.toLowerCase(Locale.ROOT));
        slug = replaceLatinLettersWithoutDecomposition(slug);
        slug = Normalizer.normalize(slug, Normalizer.Form.NFKD);
        slug = slug.replaceAll("\\p{M}", "");
        return slug.replaceAll("[^a-z]+", separator);
    }

    private String replaceLatinLettersWithoutDecomposition(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp ->
            out.append(LATIN_WITHOUT_DECOMPOSITION.getOrDefault(cp, Character.toString(cp))));
        return out.toString();
    }

    private static String cap(String part) {
        return part.length() > MAX_USERNAME_PART_LENGTH ? part.substring(0, MAX_USERNAME_PART_LENGTH) : part;
    }

    /**
     * Replaces German special characters with their ASCII equivalents.
     *
     * @param text The text to process
     * @return Text with German characters replaced
     */
    /**
     * Generates a company name (slug) from display name.
     * ADR-003: Meaningful IDs - company name serves as ID (max 12 chars, alphanumeric only)
     *
     * Rules:
     * - Converts to lowercase
     * - Removes spaces and special characters
     * - Converts German characters
     * - Truncates to 12 characters max
     *
     * Example: "Test Co" → "testco", "Müller & Partners AG" → "muellerpart"
     *
     * @param displayName The full company display name
     * @return A short alphanumeric slug (1-12 chars)
     * @throws IllegalArgumentException if displayName is null/empty
     */
    public String generateCompanyName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Display name cannot be null or empty");
        }

        // Convert to lowercase and trim
        String slug = displayName.toLowerCase().trim();

        // Replace German characters
        slug = replaceGermanCharacters(slug);

        // Normalize to remove accents
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD);
        slug = slug.replaceAll("\\p{M}", "");

        // Remove all non-alphanumeric characters (including spaces)
        slug = slug.replaceAll("[^a-z0-9]", "");

        // Truncate to 12 characters (database constraint)
        if (slug.length() > 12) {
            slug = slug.substring(0, 12);
        }

        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Display name must contain at least one alphanumeric character");
        }

        return slug;
    }

    private String replaceGermanCharacters(String text) {
        return text
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace("Ä", "ae")
            .replace("Ö", "oe")
            .replace("Ü", "ue");
    }
}
