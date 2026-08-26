package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnersApi;
import ch.batbern.partners.dto.generated.CreatePartnerRequest;
import ch.batbern.partners.dto.generated.MyPartnerCompanyResponse;
import ch.batbern.partners.dto.generated.PartnerListResponse;
import ch.batbern.partners.dto.generated.PartnerResponse;
import ch.batbern.partners.dto.generated.PartnerStatistics;
import ch.batbern.partners.dto.generated.UpdatePartnerRequest;
import ch.batbern.partners.security.PartnerSecurityService;
import ch.batbern.partners.service.PartnerContactService;
import ch.batbern.partners.service.PartnerService;
import ch.batbern.shared.api.PaginationMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * REST controller for Partner CRUD operations.
 *
 * Implements generated PartnersApi interface from OpenAPI spec.
 * All business logic delegated to PartnerService.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PartnerController implements PartnersApi {

    private final PartnerService partnerService;
    private final PartnerContactService partnerContactService;
    private final PartnerSecurityService partnerSecurityService;

    @Override
    public ResponseEntity<PartnerListResponse> listPartners(
            String filter,
            String include,
            Integer page,
            Integer size) {

        log.debug("GET /partners - filter: {}, include: {}, page: {}, size: {}", filter, include, page, size);

        // #961: the LIST spans companies, so only an organizer may expand contacts here.
        Set<String> includes = withContactsAuthorized(parseIncludes(include), null);
        List<PartnerResponse> partners = partnerService.listPartners(filter, null, page, size, includes);

        // Create pagination metadata
        PaginationMetadata metadata = new PaginationMetadata();
        metadata.setPage(page);
        metadata.setLimit(size);
        metadata.setTotalItems((long) partners.size());
        metadata.setTotalPages((int) Math.ceil((double) partners.size() / size));
        metadata.setHasNext(page + 1 < metadata.getTotalPages());
        metadata.setHasPrev(page > 0);

        PartnerListResponse response = new PartnerListResponse();
        response.setData(partners);
        response.setMetadata(metadata);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PartnerResponse> getPartnerByCompanyName(
            String companyName,
            String include) {

        log.debug("GET /partners/{} - include: {}", companyName, include);

        // #961: organizer, or the partner's own company.
        Set<String> includes = withContactsAuthorized(parseIncludes(include), companyName);
        PartnerResponse partner = partnerService.getPartnerByCompanyName(companyName, includes);

        return ResponseEntity.ok(partner);
    }

    @Override
    // #961: previously fell through to `.anyRequest().authenticated()` with no method-level
    // rule, so ANY authenticated principal could call this. Method security is what makes it
    // testable: the `test` profile filter chain is permitAll, so a matcher-only fix could not
    // be covered by an integration test at all.
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerResponse> createPartner(CreatePartnerRequest createPartnerRequest) {
        log.info("POST /partners - companyName: {}", createPartnerRequest.getCompanyName());

        PartnerResponse partner = partnerService.createPartner(createPartnerRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(partner);
    }

    @Override
    // #961: previously fell through to `.anyRequest().authenticated()` with no method-level
    // rule, so ANY authenticated principal could call this. Method security is what makes it
    // testable: the `test` profile filter chain is permitAll, so a matcher-only fix could not
    // be covered by an integration test at all.
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerResponse> updatePartner(
            String companyName,
            UpdatePartnerRequest updatePartnerRequest) {

        log.info("PATCH /partners/{}", companyName);

        PartnerResponse partner = partnerService.updatePartner(companyName, updatePartnerRequest);

        return ResponseEntity.ok(partner);
    }

    @Override
    // #961: previously fell through to `.anyRequest().authenticated()` with no method-level
    // rule, so ANY authenticated principal could call this. Method security is what makes it
    // testable: the `test` profile filter chain is permitAll, so a matcher-only fix could not
    // be covered by an integration test at all.
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deletePartner(String companyName) {
        log.info("DELETE /partners/{}", companyName);

        partnerService.deletePartner(companyName);

        return ResponseEntity.noContent().build();
    }

    @Override
    // #961: previously fell through to `.anyRequest().authenticated()` with no method-level
    // rule, so ANY authenticated principal could call this. Method security is what makes it
    // testable: the `test` profile filter chain is permitAll, so a matcher-only fix could not
    // be covered by an integration test at all.
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerResponse> reactivatePartner(String companyName) {
        log.info("POST /partners/{}/reactivate", companyName);

        PartnerResponse partner = partnerService.reactivatePartner(companyName);

        return ResponseEntity.ok(partner);
    }

    @Override
    // #961: previously fell through to `.anyRequest().authenticated()` with no method-level
    // rule, so ANY authenticated principal could call this. Method security is what makes it
    // testable: the `test` profile filter chain is permitAll, so a matcher-only fix could not
    // be covered by an integration test at all.
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerStatistics> getPartnerStatistics() {
        log.debug("GET /partners/statistics");

        PartnerStatistics statistics = partnerService.getPartnerStatistics();

        return ResponseEntity.ok(statistics);
    }

    /**
     * GET /api/v1/partners/me
     * Returns the partner company for the currently authenticated PARTNER user.
     * Resolves company via partner_contacts table (ADR-003: username-based lookup).
     * Used by the frontend to populate companyName when it is absent from the JWT.
     */
    @Override
    @PreAuthorize("hasRole('PARTNER')")
    public ResponseEntity<MyPartnerCompanyResponse> getMyPartnerCompany() {
        String companyName = partnerContactService.resolveCurrentUserCompanyName();
        return ResponseEntity.ok(new MyPartnerCompanyResponse().companyName(companyName));
    }

    // Helper methods

    private Set<String> parseIncludes(String include) {
        Set<String> includes = new HashSet<>();
        if (include != null && !include.isEmpty()) {
            String[] parts = include.split(",");
            for (String part : parts) {
                includes.add(part.trim());
            }
        }
        return includes;
    }

    /**
     * Drop {@code contacts} from the requested includes unless the caller is entitled to it.
     *
     * Issue #961. `GET /api/v1/partners` is permitAll on purpose (it backs the public partner
     * list on the homepage), but `include` reached contact enrichment unchecked, so
     * {@code ?include=contacts} returned email, firstName, lastName, username and
     * profilePictureUrl for every partner contact to ANONYMOUS callers. Verified against
     * production 2026-08-26: HTTP 200, 9 partners, 10 contact objects, 10 real addresses.
     *
     * Dropped rather than rejected with 403 on purpose: this sits on a public endpoint, so a
     * future public page that adds the parameter should lose the enrichment, not break the
     * homepage. The organizer UI (partnerApi.ts hardcodes {@code include=company,contacts})
     * is unaffected.
     *
     * @param includes    parsed include set, never null
     * @param companyName the single company being requested, or null for a cross-company list
     */
    private Set<String> withContactsAuthorized(Set<String> includes, String companyName) {
        if (!includes.contains("contacts")) {
            return includes;
        }
        if (partnerSecurityService.canViewContacts(companyName)) {
            return includes;
        }
        log.debug("Dropping 'contacts' include for unauthorized caller (company={})", companyName);
        Set<String> filtered = new HashSet<>(includes);
        filtered.remove("contacts");
        return filtered;
    }

}
