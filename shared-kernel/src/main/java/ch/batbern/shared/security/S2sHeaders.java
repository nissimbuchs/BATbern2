package ch.batbern.shared.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

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
 * <p>The headers carry nothing else; {@code Content-Type} and {@code Accept} are up to the caller.
 */
public final class S2sHeaders {

    private S2sHeaders() {
    }

    /** New headers with the current user's JWT as bearer token, or empty headers without one. */
    public static HttpHeaders forCurrentUser() {
        HttpHeaders headers = new HttpHeaders();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            headers.setBearerAuth(jwt.getTokenValue());
        }
        return headers;
    }
}
