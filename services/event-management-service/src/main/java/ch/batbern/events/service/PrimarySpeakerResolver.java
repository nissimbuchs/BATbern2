package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.repository.SessionUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the canonical primary-speaker identity for a {@link SpeakerPool} row.
 *
 * <p>Post-Epic-11 cleanup (Phase B of {@code docs/plans/speaker-workflow-refactor.md}
 * follow-up). The {@code speaker_pool.username} and {@code speaker_pool.email} columns
 * are duplicates of {@code session_users.username} + {@code UserApiClient}-resolved
 * profile data — they go stale when an organizer reassigns the session's primary
 * speaker on the Sessions tab. All outbound communication (invitation / acceptance /
 * reminder / quality-review emails, magic-link JWT claims) flows through this resolver
 * so that recipient routing always tracks the live primary speaker.
 *
 * <p>Pre-session pool rows (status {@code IDENTIFIED} / {@code CONTACTED}) return
 * {@link Optional#empty()} — there is no User to communicate with yet. Callers should
 * treat that as "skip / log + return early".
 *
 * <p>CUMS failures degrade gracefully: the username is still available from
 * session_users, but email/firstName/lastName/company fall back to the SessionUser's
 * cached values (set at session assignment time). Email may be {@code null} in that
 * case — better to skip the send than to email the wrong person.
 */
@Slf4j
@Service
public class PrimarySpeakerResolver {

    private final SessionUserRepository sessionUserRepository;
    private final UserApiClient userApiClient;

    public PrimarySpeakerResolver(SessionUserRepository sessionUserRepository,
                                  UserApiClient userApiClient) {
        this.sessionUserRepository = sessionUserRepository;
        this.userApiClient = userApiClient;
    }

    /**
     * Resolve the primary speaker profile for the given pool row.
     *
     * @param pool the speaker pool entity
     * @return profile when a session and PRIMARY_SPEAKER session_user exist;
     *         {@link Optional#empty()} otherwise
     */
    public Optional<PrimarySpeakerProfile> resolve(SpeakerPool pool) {
        if (pool == null || pool.getSessionId() == null) {
            return Optional.empty();
        }
        Optional<SessionUser> primary = sessionUserRepository
                .findBySessionIdAndSpeakerRole(pool.getSessionId(),
                        SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        if (primary.isEmpty()) {
            log.debug("No PRIMARY_SPEAKER session_user for session {} (pool row {})",
                    pool.getSessionId(), pool.getId());
            return Optional.empty();
        }
        SessionUser su = primary.get();
        String username = su.getUsername();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        try {
            UserResponse user = userApiClient.getUserByUsername(username);
            return Optional.of(new PrimarySpeakerProfile(
                    username,
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getCompanyId()));
        } catch (UserNotFoundException ex) {
            log.warn("PrimarySpeakerResolver: user {} not found in CUMS for pool row {} — "
                    + "falling back to SessionUser cached identity",
                    username, pool.getId());
        } catch (UserServiceException ex) {
            log.warn("PrimarySpeakerResolver: CUMS unavailable for {} (pool row {}): {} — "
                    + "falling back to SessionUser cached identity",
                    username, pool.getId(), ex.getMessage());
        }
        // Degraded fallback: username is canonical, identity from SessionUser cache, no email.
        return Optional.of(new PrimarySpeakerProfile(
                username,
                null,
                su.getSpeakerFirstName(),
                su.getSpeakerLastName(),
                null));
    }

    /**
     * Convenience for callers that only care about the recipient email address.
     *
     * @param pool the speaker pool entity
     * @return resolved email when available; {@link Optional#empty()} when the speaker
     *         has no session, no PRIMARY_SPEAKER, no User record, or CUMS is degraded
     */
    public Optional<String> resolveEmail(SpeakerPool pool) {
        return resolve(pool)
                .map(PrimarySpeakerProfile::email)
                .filter(e -> e != null && !e.isBlank());
    }

    /**
     * Resolved primary-speaker identity. All fields are nullable; callers must handle
     * the degraded-from-CUMS case where everything but {@code username} may be null.
     *
     * @param username    canonical username (always non-null when this record exists)
     * @param email       primary email address from CUMS (may be null on degrade)
     * @param firstName   first name from CUMS or SessionUser cache (may be null)
     * @param lastName    last name from CUMS or SessionUser cache (may be null)
     * @param companyName company display name (may be null pre-CUMS or pre-Company assignment)
     */
    public record PrimarySpeakerProfile(
            String username,
            String email,
            String firstName,
            String lastName,
            String companyName) {

        /**
         * Convenience: joined "FirstName LastName" (trimmed; empty string if both null).
         */
        public String fullName() {
            String f = firstName == null ? "" : firstName;
            String l = lastName == null ? "" : lastName;
            return (f + " " + l).trim();
        }
    }
}
