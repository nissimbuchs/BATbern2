package ch.batbern.events.repository;

/**
 * Projection to enrich organizer thank-you notes with the author's display name + company logo
 * (Story 7.7).
 *
 * <p>Same intentional, read-only cross-service DB join as {@link QnaAuthorProjection}: the public
 * featured-thanks surface and the organizer Appreciation panel both need first/last name + company
 * logo resolved from {@code user_profiles}/{@code companies} (CUMS-owned tables in the shared
 * monorepo DB) in a single batched query — without a JWT-propagating {@code UserApiClient}.
 * Ownership of those tables stays with company-user-management-service.
 */
public interface ThanksAuthorProjection {

    String getUsername();

    String getFirstName();

    String getLastName();

    /** Whether the user opted to show their company (user_profiles.settings_show_company). */
    Boolean getShowCompany();

    /** Human-readable company name (companies.display_name → name → company_id). */
    String getCompanyDisplayName();

    /** Company logo CloudFront URL (companies.logo_url), or null. */
    String getCompanyLogoUrl();
}
