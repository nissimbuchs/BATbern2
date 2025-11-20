package ch.batbern.partners.controller;

import ch.batbern.partners.dto.generated.AddPartnerContactRequest;
import ch.batbern.partners.dto.generated.PartnerContactResponse;
import ch.batbern.partners.service.PartnerContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for Partner Contact Management.
 *
 * Handles contact CRUD operations with User Service enrichment.
 * Per ADR-003: stores username (String), enriches with HTTP calls to User Service.
 */
@RestController
@RequestMapping("/api/v1/partners/{companyName}/contacts")
@RequiredArgsConstructor
@Slf4j
public class PartnerContactController {

    private final PartnerContactService partnerContactService;

    /**
     * List all contacts for a partner.
     *
     * Returns contacts enriched with User Service data (email, firstName, lastName, profilePictureUrl).
     */
    @GetMapping
    public ResponseEntity<List<PartnerContactResponse>> getPartnerContacts(
            @PathVariable String companyName) {

        log.debug("GET /partners/{}/contacts", companyName);

        List<PartnerContactResponse> contacts = partnerContactService.getPartnerContacts(companyName);

        return ResponseEntity.ok(contacts);
    }

    /**
     * Add a new contact to a partner.
     *
     * Validates user exists via User Service before adding.
     * Enforces uniqueness constraint (partner + username).
     */
    @PostMapping
    public ResponseEntity<PartnerContactResponse> addPartnerContact(
            @PathVariable String companyName,
            @RequestBody AddPartnerContactRequest request) {

        log.info("POST /partners/{}/contacts - username: {}", companyName, request.getUsername());

        PartnerContactResponse contact = partnerContactService.addPartnerContact(companyName, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(contact);
    }

    /**
     * Remove a contact from a partner.
     *
     * Business rule: Cannot remove last primary contact.
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<Void> removePartnerContact(
            @PathVariable String companyName,
            @PathVariable String username) {

        log.info("DELETE /partners/{}/contacts/{}", companyName, username);

        partnerContactService.removePartnerContact(companyName, username);

        return ResponseEntity.noContent().build();
    }
}
