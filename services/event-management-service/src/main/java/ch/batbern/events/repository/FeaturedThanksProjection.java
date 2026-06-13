package ch.batbern.events.repository;

/**
 * Projection for the PUBLIC featured-thanks marquee (Story 7.7).
 *
 * <p>One fully-enriched featured thank-you: the note text + its event code, plus the author's
 * first/last name and company display name + logo, resolved in a single cross-service join
 * (organizer_thanks → events → user_profiles → companies). The {@code INNER JOIN user_profiles}
 * means a note whose author no longer has a profile row is silently excluded (AC3), so the random
 * {@code LIMIT} fills with live authors. The raw username is never projected — only first name
 * reaches the public surface.
 */
public interface FeaturedThanksProjection {

    String getNote();

    String getEventCode();

    String getFirstName();

    String getLastName();

    /** Whether the user opted to show their company (user_profiles.settings_show_company). */
    Boolean getShowCompany();

    /** Human-readable company name (companies.display_name → name → company_id). */
    String getCompanyDisplayName();

    /** Company logo CloudFront URL (companies.logo_url), or null. */
    String getCompanyLogoUrl();
}
