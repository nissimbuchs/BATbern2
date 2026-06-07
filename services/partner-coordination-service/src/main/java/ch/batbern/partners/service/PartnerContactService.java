package ch.batbern.partners.service;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.dto.generated.PartnerContactResponse;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.exception.UserServiceException;
import ch.batbern.partners.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Partner Contact Management.
 *
 * Contacts are derived directly from the User Service: any user with the PARTNER role
 * and a matching companyId is automatically a contact of that partner company.
 * No explicit partner_contacts table is needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PartnerContactService {

    private final PartnerRepository partnerRepository;
    private final UserServiceClient userServiceClient;

    /**
     * Resolve the partner company name for the currently authenticated user.
     *
     * Looks up the user's own profile via the User Service and returns their companyId.
     * Any user with the PARTNER role is automatically a contact of their company.
     *
     * @return company name (ADR-003 identifier) from the user's own profile
     * @throws PartnerNotFoundException if the user has no company linked
     */
    @Transactional(readOnly = true)
    public String resolveCurrentUserCompanyName() {
        UserResponse user = userServiceClient.getCurrentUserProfile();
        String companyId = user != null ? user.getCompanyId() : null;

        if (companyId == null || companyId.isBlank()) {
            String username = (user != null && user.getId() != null) ? user.getId() : "unknown";
            throw new PartnerNotFoundException("User '" + username + "' is not linked to any partner company");
        }
        return companyId;
    }

    /**
     * Get all partner contacts for a company.
     *
     * Returns all users from the User Service who have companyId matching the given
     * company and hold the PARTNER role — they are automatically the contacts.
     *
     * @param companyName ADR-003 company identifier
     * @return list of partner users enriched with User Service data
     * @throws PartnerNotFoundException if no partner record exists for companyName
     */
    @Transactional(readOnly = true, noRollbackFor = UserServiceException.class)
    public List<PartnerContactResponse> getPartnerContacts(String companyName) {
        log.debug("Getting contacts for partner: {}", companyName);

        partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        List<UserResponse> users = userServiceClient.getUsersByCompanyAndRole(companyName, "PARTNER");

        return users.stream()
                .map(this::toContactResponse)
                .collect(Collectors.toList());
    }

    private PartnerContactResponse toContactResponse(UserResponse user) {
        PartnerContactResponse response = new PartnerContactResponse();
        response.setUsername(user.getId()); // id field contains username per ADR-003
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        if (user.getProfilePictureUrl() != null) {
            response.setProfilePictureUrl(user.getProfilePictureUrl().toString());
        }
        return response;
    }
}
