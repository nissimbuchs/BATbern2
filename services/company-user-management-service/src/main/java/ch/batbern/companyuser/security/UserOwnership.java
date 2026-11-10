package ch.batbern.companyuser.security;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The ownership rule of the {@code /users/{username}} resources (Story 4.10): is the caller the
 * user named in the path? Used in {@code @PreAuthorize} next to the role rule, as PCS does with
 * {@code @partnerSecurityService.isCurrentUserCompany}:
 *
 * <pre>{@code @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN') or @userOwnership.isCurrentUser(#username)")}</pre>
 *
 * <p>The caller is the user when the JWT's {@code custom:username} names them. A token without
 * that claim (the first token after JIT provisioning, local-dev Pattern N) still identifies its
 * user by the Cognito subject: the user row whose {@code cognito_user_id} is the subject, the same
 * fallback {@code GET /users/me} uses.
 */
@Component("userOwnership")
@RequiredArgsConstructor
public class UserOwnership {

    private final SecurityContextHelper securityContextHelper;
    private final UserRepository userRepository;

    /**
     * @param username the username from the path
     * @return true if the authenticated caller is that user
     */
    public boolean isCurrentUser(String username) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (username == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String current;
        try {
            current = securityContextHelper.getCurrentUsername();
        } catch (SecurityException e) {
            return false;
        }
        if (username.equals(current)) {
            return true;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            return userRepository.findByUsername(username)
                    .map(User::getCognitoUserId)
                    .filter(jwt.getSubject()::equals)
                    .isPresent();
        }
        return false;
    }
}
