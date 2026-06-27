package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.CompaniesApi;
import ch.batbern.companyuser.api.generated.CompanySearchApi;
import ch.batbern.companyuser.api.generated.CompanyVerificationApi;
import ch.batbern.companyuser.dto.generated.CompanyResponse;
import ch.batbern.companyuser.dto.generated.CompanySearchResponse;
import ch.batbern.companyuser.dto.generated.CreateCompanyRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateCompanyRequest;
import ch.batbern.companyuser.dto.generated.PaginatedCompanyResponse;
import ch.batbern.companyuser.dto.generated.UIDValidationResponse;
import ch.batbern.companyuser.dto.generated.UpdateCompanyRequest;
import ch.batbern.companyuser.service.CompanyQueryService;
import ch.batbern.companyuser.service.CompanySearchService;
import ch.batbern.companyuser.service.CompanyService;
import ch.batbern.companyuser.service.SwissUIDValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API controller for company management operations.
 *
 * <p>Phase 7 (ADR-006 / api-consolidation): contract-first — this controller
 * {@code implements} the generated {@code CompaniesApi}, {@code CompanySearchApi}, and
 * {@code CompanyVerificationApi} interfaces, which carry the HTTP method/path mappings and
 * request/response DTO types from {@code companies-api.openapi.yml}. The class-level
 * {@code @RequestMapping("/api/v1")} supplies the version prefix the interface paths omit
 * (e.g. interface {@code /companies} → {@code /api/v1/companies}). This also folds in the
 * former {@code CompanyGetOrCreateController}: the generated {@code getOrCreateCompany}
 * mapping ({@code /companies:get-or-create}) combines with the {@code /api/v1} prefix to the
 * correct colon-adjacent path, so no separate controller is needed.
 *
 * <p>Method-level {@code @PreAuthorize} stays on the implementation (the generated interface
 * carries no security); behaviour matches the pre-wiring controller exactly.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class CompanyController implements CompaniesApi, CompanySearchApi, CompanyVerificationApi {

    private final CompanyService companyService;
    private final CompanySearchService searchService;
    private final SwissUIDValidationService uidValidationService;
    private final CompanyQueryService queryService;

    /** Create a new company. Requires ORGANIZER, SPEAKER, or PARTNER role. */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER', 'SPEAKER', 'PARTNER')")
    public ResponseEntity<CompanyResponse> createCompany(CreateCompanyRequest createCompanyRequest) {
        log.info("Creating company: {}", createCompanyRequest.getName());
        CompanyResponse response = companyService.createCompany(createCompanyRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get company by name. Public endpoint for partner showcase logo/website enrichment.
     * Story 1.16.2: uses company name instead of UUID.
     */
    @Override
    public ResponseEntity<CompanyResponse> getCompany(String name) {
        log.debug("Fetching company: {}", name);
        return ResponseEntity.ok(companyService.getCompanyByName(name));
    }

    /**
     * List companies with advanced query support (filter/sort/pagination/fields/include).
     * AC14/AC15. Requires authentication.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaginatedCompanyResponse> listCompanies(
            String filter, String sort, Integer page, Integer limit, String fields, String include) {
        log.debug("Querying companies with filter: {}, sort: {}, page: {}, limit: {}, fields: {}, include: {}",
                filter, sort, page, limit, fields, include);
        PaginatedCompanyResponse response = queryService.queryCompanies(filter, sort, page, limit, fields, include);
        return ResponseEntity.ok(response);
    }

    /**
     * Idempotently resolve a display name to an existing company (matched by generated slug)
     * or create a new one. Authenticated-only. (Former CompanyGetOrCreateController.)
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CompanyResponse> getOrCreateCompany(GetOrCreateCompanyRequest getOrCreateCompanyRequest) {
        String displayName = getOrCreateCompanyRequest.getDisplayName();
        log.info("Get-or-create company for display name: {}", displayName);
        return ResponseEntity.ok(companyService.getOrCreateCompanyResponse(displayName));
    }

    /**
     * Search companies with autocomplete. Public endpoint for registration autocomplete
     * (Caffeine-cached). Supports resource expansion via {@code include} (e.g. {@code logo}).
     */
    @Override
    public ResponseEntity<List<CompanySearchResponse>> searchCompanies(String query, Integer limit, String include) {
        int effectiveLimit = limit != null ? limit : 20;
        log.debug("Searching companies with query: {}, limit: {}, include: {}", query, effectiveLimit, include);

        List<CompanySearchResponse> results = (include != null && !include.isEmpty())
                ? searchService.searchCompanies(query, effectiveLimit, include)
                : searchService.searchCompanies(query, effectiveLimit);

        return ResponseEntity.ok(results);
    }

    /**
     * Partially update a company. Requires ORGANIZER role. Publishes CompanyUpdated event.
     * Story 1.16.2: uses company name instead of UUID.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CompanyResponse> patchCompany(String name, UpdateCompanyRequest updateCompanyRequest) {
        log.info("Patching company: {}", name);
        return ResponseEntity.ok(companyService.updateCompany(name, updateCompanyRequest));
    }

    /**
     * Delete a company. Requires ORGANIZER role. Publishes CompanyDeleted event.
     * Story 1.16.2: uses company name instead of UUID.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteCompany(String name) {
        log.info("Deleting company: {}", name);
        companyService.deleteCompany(name);
        return ResponseEntity.noContent().build();
    }

    /** Validate Swiss UID format (CHE-XXX.XXX.XXX). Requires authentication. AC12. */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UIDValidationResponse> validateUID(String uid) {
        log.debug("Validating Swiss UID: {}", uid);

        boolean isValid = uidValidationService.isValidUID(uid);
        String message = isValid
                ? "Valid Swiss UID format"
                : "Invalid Swiss UID format. Expected: CHE-XXX.XXX.XXX";

        UIDValidationResponse response = UIDValidationResponse.builder()
                .valid(isValid)
                .uid(uid)
                .message(message)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Mark a company as verified. Requires ORGANIZER role. Idempotent. Publishes
     * CompanyVerified event. AC13. Story 1.16.2: uses company name instead of UUID.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CompanyResponse> verifyCompany(String name) {
        log.info("Verifying company: {}", name);
        return ResponseEntity.ok(companyService.verifyCompany(name));
    }

    // Story 1.16.3: Old company-specific logo endpoints removed.
    // Use generic endpoints instead:
    // - POST /api/v1/logos/presigned-url (generate upload URL)
    // - POST /api/v1/logos/{uploadId}/confirm (confirm upload)
    // - POST /api/v1/companies with logoUploadId (associate during creation)
}
