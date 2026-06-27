package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnerContactsApi;
import ch.batbern.partners.dto.generated.PartnerContactResponse;
import ch.batbern.partners.service.PartnerContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for Partner Contacts.
 *
 * Implements the OpenAPI-generated {@link PartnerContactsApi} interface (ADR-006
 * contract-first); the interface carries the GET /partners/{companyName}/contacts mapping.
 *
 * Contacts are derived automatically: any user with the PARTNER role and matching
 * companyId in the User Service is a contact of that partner company.
 * No explicit add/remove management is needed.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PartnerContactController implements PartnerContactsApi {

    private final PartnerContactService partnerContactService;

    /**
     * List all contacts for a partner.
     *
     * Returns all users from the User Service who have the PARTNER role
     * and belong to the given company.
     */
    @Override
    public ResponseEntity<List<PartnerContactResponse>> getPartnerContacts(String companyName) {

        log.debug("GET /partners/{}/contacts", companyName);

        List<PartnerContactResponse> contacts = partnerContactService.getPartnerContacts(companyName);

        return ResponseEntity.ok(contacts);
    }
}
