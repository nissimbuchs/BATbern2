package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.*;
import ch.batbern.companyuser.service.CompanyQueryService;
import ch.batbern.companyuser.service.CompanySearchService;
import ch.batbern.companyuser.service.CompanyService;
import ch.batbern.companyuser.service.SwissUIDValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final SwissUIDValidationService uidValidationService;
    private final CompanyQueryService queryService;

    /**
     * Create a new company
     * Requires ORGANIZER, SPEAKER, or PARTNER role
     * AC4: Company creation endpoint
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'SPEAKER', 'PARTNER')")
    @Operation(
            summary = "Create a new company",
            description = "Creates a new company in the system. Requires ORGANIZER, SPEAKER, or PARTNER role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Company created successfully",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data (validation failed)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - insufficient permissions"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - company with this name already exists"
            )
    })
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
    @Operation(
            summary = "Get company by ID",
            description = "Retrieves a company by its unique identifier. Requires authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Company found",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company not found"
            )
    })
    public ResponseEntity<CompanyResponse> getCompany(
            @Parameter(description = "Company UUID", required = true)
            @PathVariable UUID id) {
        log.debug("Fetching company: {}", id);
        CompanyResponse response = companyService.getCompanyById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * List all companies with advanced query support
     * Requires authentication
     * AC4: Company list endpoint
     * AC14: Advanced query patterns (filter, sort, pagination, field selection)
     * AC15: Resource expansion (include=statistics,logo)
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List all companies with advanced query support",
            description = "Retrieves a paginated list of companies with support for filtering, sorting, field selection, and resource expansion. " +
                    "Supports MongoDB-style JSON filters, multi-field sorting, pagination, sparse fieldsets, and resource includes. " +
                    "Examples: ?filter={\"industry\":\"Technology\"}&sort=-name&page=1&limit=20&fields=id,name&include=statistics,logo"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Companies retrieved successfully with pagination metadata",
                    content = @Content(schema = @Schema(implementation = PaginatedCompanyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameters (invalid JSON, negative page, etc.)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            )
    })
    public ResponseEntity<PaginatedCompanyResponse> getAllCompanies(
            @Parameter(description = "MongoDB-style JSON filter (e.g., {\"industry\":\"Technology\"})", example = "{\"industry\":\"Technology\"}")
            @RequestParam(required = false) String filter,
            @Parameter(description = "Sort fields (comma-separated, prefix with - for DESC)", example = "-name,createdAt")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Page number (1-indexed, default: 1)", example = "1")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Items per page (default: 20, max: 100)", example = "20")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Comma-separated field names to return (sparse fieldsets)", example = "id,name,industry")
            @RequestParam(required = false) String fields,
            @Parameter(description = "Comma-separated relation names to include (resource expansion)", example = "statistics,logo")
            @RequestParam(required = false) String include) {
        log.debug("Querying companies with filter: {}, sort: {}, page: {}, limit: {}, fields: {}, include: {}",
                filter, sort, page, limit, fields, include);
        PaginatedCompanyResponse response = queryService.queryCompanies(filter, sort, page, limit, fields, include);
        return ResponseEntity.ok(response);
    }

    /**
     * Search companies with autocomplete
     * Requires authentication
     * AC5 & AC11: Company search endpoint with Caffeine caching and configurable limit
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Search companies with autocomplete",
            description = "Search companies by name with autocomplete functionality. Results are cached using Caffeine for 15 minutes. P95 latency < 100ms with cache."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Search results returned successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query or limit parameter"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            )
    })
    public ResponseEntity<List<CompanySearchResponse>> searchCompanies(
            @Parameter(description = "Search query (minimum 1 character)", required = true)
            @RequestParam String query,
            @Parameter(description = "Maximum number of results (default: 20)")
            @RequestParam(required = false, defaultValue = "20") int limit) {
        log.debug("Searching companies with query: {} and limit: {}", query, limit);
        List<CompanySearchResponse> results = searchService.searchCompanies(query, limit);
        return ResponseEntity.ok(results);
    }

    /**
     * Update company (full replacement)
     * Requires ORGANIZER role
     * AC4: Company update endpoint
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "Update company (full replacement)",
            description = "Fully replaces an existing company. Requires ORGANIZER role. Publishes CompanyUpdated event."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Company updated successfully",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data (validation failed)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires ORGANIZER role"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - company name already exists"
            )
    })
    public ResponseEntity<CompanyResponse> updateCompany(
            @Parameter(description = "Company UUID", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        log.info("Updating company: {}", id);
        CompanyResponse response = companyService.updateCompany(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Partially update company
     * Requires ORGANIZER role
     * AC4: Company partial update endpoint
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "Partially update company",
            description = "Updates specific fields of an existing company. Requires ORGANIZER role. Publishes CompanyUpdated event."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Company updated successfully",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data (validation failed)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires ORGANIZER role"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - company name already exists"
            )
    })
    public ResponseEntity<CompanyResponse> patchCompany(
            @Parameter(description = "Company UUID", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        log.info("Patching company: {}", id);
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
    @Operation(
            summary = "Delete company",
            description = "Deletes a company from the system. Requires ORGANIZER role. Publishes CompanyDeleted event."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Company deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires ORGANIZER role"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company not found"
            )
    })
    public ResponseEntity<Void> deleteCompany(
            @Parameter(description = "Company UUID", required = true)
            @PathVariable UUID id) {
        log.info("Deleting company: {}", id);
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Validate Swiss UID format
     * Requires authentication
     * AC12: Swiss UID validation endpoint
     */
    @GetMapping("/validate-uid")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Validate Swiss UID format",
            description = "Validates Swiss company UID (Unternehmens-Identifikationsnummer) format. Expected format: CHE-XXX.XXX.XXX"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Validation result returned",
                    content = @Content(schema = @Schema(implementation = UIDValidationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Missing UID parameter"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            )
    })
    public ResponseEntity<UIDValidationResponse> validateUID(
            @Parameter(description = "Swiss UID to validate", required = true)
            @RequestParam(required = true) String uid) {
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
     * Verify company
     * Requires ORGANIZER role
     * AC13: Company verification workflow
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "Verify company",
            description = "Marks a company as verified by an ORGANIZER. Publishes CompanyVerified event. Idempotent operation."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Company verified successfully",
                    content = @Content(schema = @Schema(implementation = CompanyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT token"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires ORGANIZER role"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company not found"
            )
    })
    public ResponseEntity<CompanyResponse> verifyCompany(
            @Parameter(description = "Company UUID", required = true)
            @PathVariable UUID id) {
        log.info("Verifying company: {}", id);
        CompanyResponse response = companyService.verifyCompany(id);
        return ResponseEntity.ok(response);
    }
}
