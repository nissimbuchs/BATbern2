package ch.batbern.events.repository;

/**
 * Projection to enrich Q&A posts with the poster's display name + company logo (Story 7.5).
 *
 * <p>Same intentional, read-only cross-service DB join as {@link UserPortraitProjection}: the
 * Q&A GET is PUBLIC (anonymous-readable), so it cannot call the JWT-propagating
 * {@code UserApiClient}. Reading {@code user_profiles}/{@code companies}/{@code logos} directly
 * (all in the shared monorepo DB) resolves first/last name + company logo without auth and in a
 * single batched query. Ownership of those tables stays with company-user-management-service.
 */
public interface QnaAuthorProjection {

    String getUsername();

    String getFirstName();

    String getLastName();

    /** Whether the user opted to show their company (user_profiles.settings_show_company). */
    Boolean getShowCompany();

    /** Human-readable company name (companies.display_name → name → company_id). */
    String getCompanyDisplayName();

    /** Company logo CloudFront URL (logos.cloudfront_url for the COMPANY entity), or null. */
    String getCompanyLogoUrl();
}
