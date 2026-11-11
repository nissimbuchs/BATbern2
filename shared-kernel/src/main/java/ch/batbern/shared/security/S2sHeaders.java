package ch.batbern.shared.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The headers of every service-to-service call (ADR-016 decision 3, Story 17.6 / Epic 17 entry 6).
 *
 * <p>Forward the user's JWT whenever there is one, never require it: a call made while a JWT
 * principal is authenticated (a {@code JwtAuthenticationToken}, the user who entered through a
 * public endpoint) carries {@code Authorization: Bearer <token>}, so the receiver records who
 * acted and may check ownership. Any other caller (scheduler, SQS listener, anonymous flow, a
 * {@code @WithMockUser} test) sends no {@code Authorization} header, and the receiver records
 * {@code system:<job>}. Neither case logs a warning: a call without a user is normal.
 *
 * <p>A token that expires within {@link #EXPIRY_MARGIN} (or has no expiry) is not forwarded: the
 * receiver's resource server would answer 401 even on a {@code permitAll} {@code /internal} path,
 * and an {@code @Async} task that inherited the request's security context may run past the
 * token's lifetime. Such a call goes out without a user, as a system call.
 *
 * <p>The headers carry nothing else; {@code Content-Type} and {@code Accept} are up to the caller.
 */
public final class S2sHeaders {

    /** A token must stay valid at least this long after the call starts to be forwarded. */
    public static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private S2sHeaders() {
    }

    /** New headers with the current user's JWT as bearer token, or empty headers without one. */
    public static HttpHeaders forCurrentUser() {
        return forCurrentUser(Clock.systemUTC());
    }

    /** As {@link #forCurrentUser()}, with the clock that decides whether the token is still valid. */
    public static HttpHeaders forCurrentUser(Clock clock) {
        HttpHeaders headers = new HttpHeaders();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt != null && expiresAt.isAfter(clock.instant().plus(EXPIRY_MARGIN))) {
                headers.setBearerAuth(jwt.getTokenValue());
            }
        }
        return headers;
    }
}
