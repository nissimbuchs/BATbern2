package ch.batbern.partners.security;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Security service for partner-scoped authorization.
 * Story 8.1: AC6 — PARTNER users may only access their own company's analytics.
 *
 * Used in @PreAuthorize expressions:
 *   @PreAuthorize("hasRole('ORGANIZER') or @partnerSecurityService.isCurrentUserCompany(#companyName)")
 *
 * Company membership is derived from the user's own profile (companyId field) in the User Service.
 * No explicit partner_contacts table lookup is needed.
 */
@Service("partnerSecurityService")
@RequiredArgsConstructor
@Slf4j
public class PartnerSecurityService {

    private final SecurityContextHelper securityContextHelper;
    private final UserServiceClient userServiceClient;

    /**
     * Returns true if the currently authenticated user's companyId matches the given partner company.
     *
     * @param companyName company name (ADR-003 meaningful identifier) from path variable
     * @return true if the current user belongs to that partner company
     */
    public boolean isCurrentUserCompany(String companyName) {
        try {
            String username = securityContextHelper.getCurrentUsername();
            UserResponse user = userServiceClient.getUserByUsername(username);
            boolean isMatch = user != null && companyName.equals(user.getCompanyId());

            if (!isMatch) {
                log.debug("User '{}' company '{}' does not match requested company '{}'",
                        username, user != null ? user.getCompanyId() : "null", companyName);
            }
            return isMatch;
        } catch (SecurityException e) {
            log.debug("No authenticated user — isCurrentUserCompany returns false");
            return false;
        } catch (Exception e) {
            log.debug("Could not verify user company membership: {}", e.getMessage());
            return false;
        }
    }
}
