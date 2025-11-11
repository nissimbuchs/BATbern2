package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnersApi;
import ch.batbern.partners.dto.generated.*;
import ch.batbern.partners.service.PartnerService;
import ch.batbern.shared.api.PaginationMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Override
    public ResponseEntity<PartnerListResponse> listPartners(
            String filter,
            String include,
            Integer page,
            Integer size) {

        log.debug("GET /partners - filter: {}, include: {}, page: {}, size: {}", filter, include, page, size);

        List<PartnerResponse> partners = partnerService.listPartners(filter, null, page, size);

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

        Set<String> includes = parseIncludes(include);
        PartnerResponse partner = partnerService.getPartnerByCompanyName(companyName, includes);

        return ResponseEntity.ok(partner);
    }

    @Override
    public ResponseEntity<PartnerResponse> createPartner(CreatePartnerRequest createPartnerRequest) {
        log.info("POST /partners - companyName: {}", createPartnerRequest.getCompanyName());

        PartnerResponse partner = partnerService.createPartner(createPartnerRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(partner);
    }

    @Override
    public ResponseEntity<PartnerResponse> updatePartner(
            String companyName,
            UpdatePartnerRequest updatePartnerRequest) {

        log.info("PATCH /partners/{}", companyName);

        PartnerResponse partner = partnerService.updatePartner(companyName, updatePartnerRequest);

        return ResponseEntity.ok(partner);
    }

    @Override
    public ResponseEntity<Void> deletePartner(String companyName) {
        log.info("DELETE /partners/{}", companyName);

        partnerService.deletePartner(companyName);

        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PartnerStatistics> getPartnerStatistics() {
        log.debug("GET /partners/statistics");

        PartnerStatistics statistics = partnerService.getPartnerStatistics();

        return ResponseEntity.ok(statistics);
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
}
