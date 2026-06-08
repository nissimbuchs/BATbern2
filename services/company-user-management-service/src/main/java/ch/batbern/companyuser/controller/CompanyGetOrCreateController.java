package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.CompanyResponse;
import ch.batbern.companyuser.dto.GetOrCreateCompanyRequest;
import ch.batbern.companyuser.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dedicated controller for the {@code POST /companies:get-or-create} custom-method
 * endpoint (AIP-136 colon style).
 *
 * <p>It lives apart from {@link CompanyController} because that controller carries a
 * {@code @RequestMapping("/api/v1/companies")} class prefix, and Spring's path
 * combination always inserts a {@code /} between the class and method patterns —
 * which would produce {@code /api/v1/companies/:get-or-create} instead of the
 * spec's colon-adjacent {@code /api/v1/companies:get-or-create}. An unprefixed
 * controller with the absolute path mirrors the generated {@code CompaniesApi}
 * interface mapping exactly.
 *
 * <p>Lets the company picker materialise a brand-new company and receive its
 * canonical slug ({@code name}), so a profile stores a real company reference
 * rather than a free-typed string (ADR-003). Authenticated-only — the public
 * registration flow resolves companies server-side on submit, so no anonymous
 * surface is added here.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Company Management", description = "Company management operations")
@SecurityRequirement(name = "bearerAuth")
public class CompanyGetOrCreateController {

    private final CompanyService companyService;

    @PostMapping("/api/v1/companies:get-or-create")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get or create company by display name",
            description = "Idempotently resolves a display name to an existing company "
                    + "(matched by generated slug) or creates a new one."
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Company retrieved or created successfully",
                content = @Content(schema = @Schema(implementation = CompanyResponse.class))
            ),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request data (validation failed)"
            ),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - missing or invalid JWT token"
            )
    })
    public ResponseEntity<CompanyResponse> getOrCreateCompany(
            @Valid @RequestBody GetOrCreateCompanyRequest request) {
        log.info("Get-or-create company for display name: {}", request.getDisplayName());
        CompanyResponse response = companyService.getOrCreateCompanyResponse(request.getDisplayName());
        return ResponseEntity.ok(response);
    }
}
