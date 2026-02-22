package ch.batbern.partners.security;

import ch.batbern.partners.repository.PartnerContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Security service for partner-scoped authorization.
 * Story 8.1: AC6 — PARTNER users may only access their own company's analytics.
 *
 * Used in @PreAuthorize expressions:
 *   @PreAuthorize("hasRole('ORGANIZER') or @partnerSecurityService.isCurrentUserCompany(#companyName)")
 */
@Service("partnerSecurityService")
@RequiredArgsConstructor
@Slf4j
public class PartnerSecurityService {

    private final SecurityContextHelper securityContextHelper;
    private final PartnerContactRepository partnerContactRepository;

    /**
     * Returns true if the currently authenticated user is a contact of the given partner company.
     * Uses a single JOIN query (H1 fix: eliminates N+1 pattern).
     *
     * @param companyName company name (ADR-003 meaningful identifier) from path variable
     * @return true if the current user's username appears as a contact of that partner
     */
    public boolean isCurrentUserCompany(String companyName) {
        try {
            String username = securityContextHelper.getCurrentUsername();
            boolean isContact = partnerContactRepository
                    .countByUsernameAndCompanyName(username, companyName) > 0;

            if (!isContact) {
                log.debug("User '{}' is not a contact of company '{}'", username, companyName);
            }
            return isContact;
        } catch (SecurityException e) {
            log.debug("No authenticated user — isCurrentUserCompany returns false");
            return false;
        }
    }
}
