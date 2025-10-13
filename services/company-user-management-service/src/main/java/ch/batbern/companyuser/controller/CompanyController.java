package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.CompanyResponse;
import ch.batbern.companyuser.dto.CompanySearchResponse;
import ch.batbern.companyuser.dto.CreateCompanyRequest;
import ch.batbern.companyuser.dto.UpdateCompanyRequest;
import ch.batbern.companyuser.service.CompanySearchService;
import ch.batbern.companyuser.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API controller for company management operations
 * AC4: REST API implementation with OpenAPI documentation
 * AC5: Company search with autocomplete functionality
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Company Management", description = "Company management operations")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanySearchService searchService;

    /**
     * Create a new company
     * Requires ORGANIZER, SPEAKER, or PARTNER role
     * AC4: Company creation endpoint
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'SPEAKER', 'PARTNER')")
    @Operation(summary = "Create a new company")
    public ResponseEntity<CompanyResponse> createCompany(
            @Valid @RequestBody CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getName());
        CompanyResponse response = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get company by ID
     * Requires authentication
     * AC4: Company retrieval endpoint
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get company by ID")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable UUID id) {
        log.debug("Fetching company: {}", id);
        CompanyResponse response = companyService.getCompanyById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * List all companies
     * Requires authentication
     * AC4: Company list endpoint
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all companies")
    public ResponseEntity<List<CompanyResponse>> getAllCompanies() {
        log.debug("Fetching all companies");
        List<CompanyResponse> companies = companyService.getAllCompanies();
        return ResponseEntity.ok(companies);
    }

    /**
     * Search companies with autocomplete
     * Requires authentication
     * AC5: Company search endpoint with Caffeine caching
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search companies with autocomplete")
    public ResponseEntity<List<CompanySearchResponse>> searchCompanies(
            @RequestParam String query) {
        log.debug("Searching companies with query: {}", query);
        List<CompanySearchResponse> results = searchService.searchCompanies(query);
        return ResponseEntity.ok(results);
    }

    /**
     * Update company
     * Requires ORGANIZER role
     * AC4: Company update endpoint
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Update company")
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        log.info("Updating company: {}", id);
        CompanyResponse response = companyService.updateCompany(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete company
     * Requires ORGANIZER role
     * AC4: Company deletion endpoint
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Delete company")
    public ResponseEntity<Void> deleteCompany(@PathVariable UUID id) {
        log.info("Deleting company: {}", id);
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }
}
