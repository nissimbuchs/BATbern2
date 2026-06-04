package ch.batbern.companyuser.interceptor;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.service.FederatedAvatarImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Story 12.12: trigger the ONE-TIME Google avatar import when an authenticated request
 * carries an ID-token {@code picture} claim (mapped by the Cognito Google IdP, AC1).
 *
 * <p>Sibling of {@link JITUserProvisioningInterceptor} (kept single-purpose) and registered
 * AFTER it in {@code WebMvcConfig}, so on the very first federated request the JIT-created
 * row already exists when this hook runs.
 *
 * <p>Same non-blocking contract as JIT: any failure here is logged and swallowed — a broken
 * avatar fetch must NEVER fail the user's API request. Native sign-ins carry no
 * {@code picture} claim and exit immediately (zero DB cost, AC5).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FederatedAvatarImportInterceptor implements HandlerInterceptor {

    private final FederatedAvatarImportService federatedAvatarImportService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
                return true;
            }

            Jwt jwt = jwtAuth.getToken();

            // Native users have no `picture` claim — this is the cheap common-path exit.
            String pictureClaim = jwt.getClaimAsString("picture");
            if (pictureClaim == null || pictureClaim.isBlank()) {
                return true;
            }

            String cognitoUserId = jwt.getSubject();
            if (cognitoUserId == null || cognitoUserId.isEmpty()) {
                return true;
            }

            // 12.12 review (finding #6): JIT (registered immediately before this
            // interceptor) stashes the user it resolved for this principal — reuse it
            // instead of re-running the identical findByCognitoUserId SELECT on every
            // request. Fall back to the lookup only when JIT didn't resolve one.
            Object resolved = request.getAttribute(JITUserProvisioningInterceptor.RESOLVED_USER_ATTRIBUTE);
            if (resolved instanceof User user) {
                federatedAvatarImportService.importIfNeeded(user, pictureClaim);
            } else {
                federatedAvatarImportService.importIfNeeded(cognitoUserId, pictureClaim);
            }

        } catch (Exception e) {
            // Non-blocking requirement (same contract as JITUserProvisioningInterceptor).
            log.error("Federated avatar import hook failed, allowing request to continue", e);
        }

        return true;
    }
}
