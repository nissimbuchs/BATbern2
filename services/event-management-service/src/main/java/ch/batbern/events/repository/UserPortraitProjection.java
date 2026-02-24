package ch.batbern.events.repository;

/**
 * Projection for the intentional cross-service DB join:
 *   session_users (EMS) → user_profiles + logos (CUMS)
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  INTENTIONAL ARCHITECTURE BREAK — read carefully before changing   │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * WHY this exists
 * ───────────────
 * The archive list endpoint (GET /api/v1/events?include=sessions,speakers)
 * previously made one HTTP call to company-user-management-service (CUMS) per
 * speaker to fetch portrait URL + company logo. For a page of 20 events with
 * ~5 sessions and ~3 speakers each, that was ~300 sequential HTTP calls per
 * request — the main cause of the archive page's slow initial load time.
 *
 * WHY it is safe here
 * ───────────────────
 * This is a monorepo with a shared PostgreSQL instance. All microservices connect
 * to the same database (DATABASE_URL env var). Reading across service boundaries
 * at the DB level is therefore a deliberate deployment choice, not an accident.
 * The session_users.speaker_first_name / _last_name columns (V38 migration) set
 * the precedent: denormalized cross-service data is already accepted in this project.
 *
 * CONSTRAINTS to respect
 * ──────────────────────
 * 1. READ-ONLY: EMS never writes to user_profiles or logos. Ownership stays with CUMS.
 * 2. LIST ONLY: this projection is used exclusively in the list (paginated) endpoint.
 *    The single-event GET already uses the proper CUMS HTTP client (cached 15 min).
 * 3. NULLABLE: both profile_picture_url and company logo may be null — handle gracefully.
 * 4. IF SCHEMAS DIVERGE: if user_profiles or logos are renamed/migrated, update the
 *    native query in SessionUserRepository.findUserPortraitsByUsernames() immediately.
 *    The ArchiveBrowsingIntegrationTest will catch regressions.
 *
 * EFFECT
 * ──────
 * Archive list: ~300 HTTP calls → 0 HTTP calls, 1 additional DB query (IN clause).
 * Portrait and logo URLs are now embedded in the paginated response.
 */
public interface UserPortraitProjection {

    String getUsername();

    /** Profile picture CloudFront URL from user_profiles.profile_picture_url (CUMS). */
    String getProfilePictureUrl();

    /** Company name/ID from user_profiles.company_id (CUMS). */
    String getCompanyId();

    /** Company logo CloudFront URL from logos.cloudfront_url (CUMS). */
    String getCompanyLogoUrl();
}
