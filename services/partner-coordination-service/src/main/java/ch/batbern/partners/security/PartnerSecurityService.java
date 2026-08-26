package ch.batbern.partners.security;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * True when the current principal holds ROLE_ORGANIZER.
     *
     * Read straight off the SecurityContext rather than via a User Service call: this is
     * consulted on the PUBLIC partner-list path (issue #961), which must stay cheap and must
     * work for an anonymous caller without a round trip.
     */
    public boolean isOrganizer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ORGANIZER".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * May the current caller see partner CONTACT details (name, email, username)?
     *
     * Issue #961: `GET /api/v1/partners` is deliberately permitAll — it backs the public
     * partner list on the homepage — but its `include` parameter was passed straight into
     * contact enrichment, so `?include=contacts` returned email, firstName, lastName,
     * username and profilePictureUrl for every partner contact to ANONYMOUS callers.
     * Confirmed against production on 2026-08-26: HTTP 200, 9 partners, 10 contact objects,
     * 10 real addresses. The list itself is public; the contacts are not.
     *
     * Organizers see any company's contacts. A partner sees only their own — the same rule
     * PartnerAnalyticsController already applies to attendance data.
     *
     * @param companyName company whose contacts are being requested, or null when the caller
     *                    is asking for a LIST spanning companies (organizer-only in that case)
     */
    public boolean canViewContacts(String companyName) {
        if (isOrganizer()) {
            return true;
        }
        return companyName != null && isCurrentUserCompany(companyName);
    }

}
