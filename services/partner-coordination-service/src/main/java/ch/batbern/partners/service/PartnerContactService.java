package ch.batbern.partners.service;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.domain.ContactRole;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnerContact;
import ch.batbern.partners.dto.generated.AddPartnerContactRequest;
import ch.batbern.partners.dto.generated.PartnerContactResponse;
import ch.batbern.partners.events.PartnerContactAddedEvent;
import ch.batbern.partners.events.PartnerContactRemovedEvent;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerContactRepository;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Partner Contact Management.
 *
 * Handles business logic for partner contact operations with HTTP enrichment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PartnerContactService {

    private final PartnerRepository partnerRepository;
    private final PartnerContactRepository partnerContactRepository;
    private final UserServiceClient userServiceClient;
    private final DomainEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Get all contacts for a partner with User data enrichment.
     */
    public List<PartnerContactResponse> getPartnerContacts(String companyName) {
        log.debug("Getting contacts for partner: {}", companyName);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        List<PartnerContact> contacts = partnerContactRepository.findByPartnerId(partner.getId());

        // Enrich with User Service data
        return contacts.stream()
                .map(this::enrichContactWithUserData)
                .collect(Collectors.toList());
    }

    /**
     * Add a new contact to a partner.
     */
    public PartnerContactResponse addPartnerContact(String companyName, AddPartnerContactRequest request) {
        log.info("Adding contact {} to partner: {}", request.getUsername(), companyName);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        // Validate user exists via User Service
        UserResponse userProfile = userServiceClient.getUserProfile(request.getUsername());

        // Check for duplicate
        if (partnerContactRepository.existsByPartnerIdAndUsername(partner.getId(), request.getUsername())) {
            throw new ValidationException("Contact already exists for partner: " + request.getUsername());
        }

        // Create contact
        PartnerContact contact = new PartnerContact();
        contact.setPartnerId(partner.getId());
        contact.setUsername(request.getUsername());
        // Convert from generated ContactRole (lowercase JSON) to domain ContactRole (uppercase enum)
        contact.setContactRole(ContactRole.valueOf(request.getContactRole().name()));
        contact.setPrimary(request.getIsPrimary());

        contact = partnerContactRepository.save(contact);

        // Publish event
        publishContactAddedEvent(partner, contact);

        // Return enriched response
        return enrichContactWithUserData(contact);
    }

    /**
     * Remove a contact from a partner.
     */
    public void removePartnerContact(String companyName, String username) {
        log.info("Removing contact {} from partner: {}", username, companyName);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        PartnerContact contact = partnerContactRepository.findByPartnerIdAndUsername(partner.getId(), username)
                .orElseThrow(() -> new NotFoundException("Contact not found: " + username));

        // Business rule: Cannot remove last primary contact
        if (contact.isPrimary()) {
            long primaryCount = partnerContactRepository.countByPartnerIdAndIsPrimaryTrue(partner.getId());
            if (primaryCount <= 1) {
                throw new ValidationException("Cannot remove last primary contact. Partner must have at least one primary contact.");
            }
        }

        partnerContactRepository.delete(contact);

        // Publish event
        publishContactRemovedEvent(partner, contact);
    }

    /**
     * Enrich contact with User Service data.
     */
    private PartnerContactResponse enrichContactWithUserData(PartnerContact contact) {
        PartnerContactResponse response = new PartnerContactResponse();
        response.setUsername(contact.getUsername());
        // Map domain ContactRole (uppercase) to generated ContactRole (serializes to lowercase JSON)
        response.setContactRole(ch.batbern.partners.dto.generated.ContactRole.valueOf(contact.getContactRole().name()));
        response.setIsPrimary(contact.isPrimary());

        // Enrich with User Service data (using generated UserResponse from users-api.openapi.yml)
        try {
            UserResponse userProfile = userServiceClient.getUserProfile(contact.getUsername());
            response.setEmail(userProfile.getEmail());
            response.setFirstName(userProfile.getFirstName());
            response.setLastName(userProfile.getLastName());
            // Note: UserResponse.profilePictureUrl is URI, convert to String
            if (userProfile.getProfilePictureUrl() != null) {
                response.setProfilePictureUrl(userProfile.getProfilePictureUrl().toString());
            }
        } catch (Exception e) {
            log.warn("Failed to enrich contact {} with User data: {}", contact.getUsername(), e.getMessage());
            // Return response without enrichment - graceful degradation
        }

        return response;
    }

    private void publishContactAddedEvent(Partner partner, PartnerContact contact) {
        // Per ADR-003: Extract username (meaningful ID), NOT userId (UUID)
        String currentUsername = securityContextHelper.getCurrentUsername();

        PartnerContactAddedEvent event = new PartnerContactAddedEvent(
                partner.getId(),
                partner.getCompanyName(),
                contact.getUsername(),
                contact.getContactRole().toString(),
                currentUsername  // DomainEvent userId field contains username per ADR-003
        );
        eventPublisher.publish(event);
    }

    private void publishContactRemovedEvent(Partner partner, PartnerContact contact) {
        // Per ADR-003: Extract username (meaningful ID), NOT userId (UUID)
        String currentUsername = securityContextHelper.getCurrentUsername();

        PartnerContactRemovedEvent event = new PartnerContactRemovedEvent(
                partner.getId(),
                partner.getCompanyName(),
                contact.getUsername(),
                currentUsername  // DomainEvent userId field contains username per ADR-003
        );
        eventPublisher.publish(event);
    }
}
