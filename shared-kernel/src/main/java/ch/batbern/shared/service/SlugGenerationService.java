package ch.batbern.shared.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
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

    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Generates a username from first and last names.
     *
     * Rules:
     * - Converts to lowercase
     * - Replaces spaces with dots
     * - Converts German characters: ä→ae, ö→oe, ü→ue, ß→ss
     * - Removes special characters
     *
     * Example: "Müller" + "Özdemir" → "mueller.oezdemir"
     *
     * @param firstName The user's first name
     * @param lastName The user's last name
     * @return A username string
     * @throws IllegalArgumentException if firstName or lastName is null/empty
     */
    public String generateUsername(String firstName, String lastName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name cannot be null or empty");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name cannot be null or empty");
        }

        String slugifiedFirst = slugifyName(firstName);
        String slugifiedLast = slugifyName(lastName);

        return slugifiedFirst + "." + slugifiedLast;
    }

    /**
     * Generates a username from first and last names, with email fallback.
     * ADR-005: Used for anonymous user registration where names might be unavailable.
     *
     * Rules:
     * - If both firstName and lastName are provided, uses name-based username
     * - If names are missing/empty, falls back to email prefix (before @)
     * - Converts to lowercase
     * - Replaces spaces with dots
     * - Converts German characters: ä→ae, ö→oe, ü→ue, ß→ss
     * - Removes special characters
     *
     * Example: "Müller" + "Özdemir" + "email@example.com" → "mueller.oezdemir"
     * Example: null + null + "john.doe@example.com" → "john.doe"
     *
     * @param firstName The user's first name (can be null/empty)
     * @param lastName The user's last name (can be null/empty)
     * @param email The user's email address (fallback if names unavailable)
     * @return A username string
     * @throws IllegalArgumentException if all parameters are null/empty or email is invalid
     */
    public String generateUsername(String firstName, String lastName, String email) {
        // Try name-based username first
        boolean hasFirstName = firstName != null && !firstName.trim().isEmpty();
        boolean hasLastName = lastName != null && !lastName.trim().isEmpty();

        if (hasFirstName && hasLastName) {
            return generateUsername(firstName, lastName);
        }

        // Fallback to email prefix
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty when names are unavailable");
        }

        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }

        String emailPrefix = email.substring(0, email.indexOf("@"));
        String slugified = slugifyName(emailPrefix);

        // Ensure username has proper format (may need to replace hyphens with dots)
        slugified = slugified.replace("-", ".");

        return slugified;
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
     * Slugifies a name part (first or last name).
     *
     * Handles German characters and converts spaces to dots.
     *
     * @param name The name to slugify
     * @return Slugified name
     */
    private String slugifyName(String name) {
        // Convert to lowercase
        String slug = name.toLowerCase().trim();

        // Replace German characters
        slug = replaceGermanCharacters(slug);

        // Normalize to remove remaining accents
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD);
        slug = slug.replaceAll("\\p{M}", "");

        // Replace whitespace with dots
        slug = WHITESPACE.matcher(slug).replaceAll(".");

        // Remove any remaining special characters except dots
        slug = slug.replaceAll("[^a-z0-9.]", "");

        return slug;
    }

    /**
     * Replaces German special characters with their ASCII equivalents.
     *
     * @param text The text to process
     * @return Text with German characters replaced
     */
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
