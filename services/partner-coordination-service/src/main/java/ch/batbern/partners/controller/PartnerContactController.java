package ch.batbern.partners.controller;

import ch.batbern.partners.dto.generated.PartnerContactResponse;
import ch.batbern.partners.service.PartnerContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for Partner Contacts.
 *
 * Contacts are derived automatically: any user with the PARTNER role and matching
 * companyId in the User Service is a contact of that partner company.
 * No explicit add/remove management is needed.
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
     * Returns all users from the User Service who have the PARTNER role
     * and belong to the given company.
     */
    @GetMapping
    public ResponseEntity<List<PartnerContactResponse>> getPartnerContacts(
            @PathVariable String companyName) {

        log.debug("GET /partners/{}/contacts", companyName);

        List<PartnerContactResponse> contacts = partnerContactService.getPartnerContacts(companyName);

        return ResponseEntity.ok(contacts);
    }
}
