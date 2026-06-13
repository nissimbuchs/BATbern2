package ch.batbern.events.dto;

/**
 * One featured thank-you note for the PUBLIC marquee (Story 7.7).
 *
 * <p>Returned by {@code GET /api/v1/thanks/featured} — organizer-curated, logged-in-only notes,
 * enriched with the author's name + company logo. The raw username/email is NEVER exposed; only
 * the first name reaches the public surface. {@code thankedByCompanyName}/{@code …CompanyLogoUrl}
 * are {@code null} when the author opted out of showing their company (settings_show_company).
 */
public record FeaturedThanksResponse(
        String note,
        String eventCode,
        String thankedByFirstName,
        String thankedByLastName,
        String thankedByCompanyName,
        String thankedByCompanyLogoUrl) {
}
