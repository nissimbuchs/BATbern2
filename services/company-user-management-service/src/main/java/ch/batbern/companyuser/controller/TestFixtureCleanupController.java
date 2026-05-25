package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.TestFixtureCleanupRequest;
import ch.batbern.companyuser.dto.TestFixtureCleanupResponse;
import ch.batbern.companyuser.service.TestFixtureCleanupService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bruno test-fixture cleanup endpoint for CUMS.
 *
 * <p>This endpoint exists because Bruno API contract tests run against staging
 * (which IS the production account, serving api.batbern.ch). Test data must be
 * cleanly removable so the tests stay idempotent across CI runs and so months of
 * accumulated leftovers don't pollute the production-account DB.
 *
 * <p>Cleanup matches a small set of canonical prefixes per entity type — the request
 * body cannot supply arbitrary patterns. See {@code bruno-tests/README.md} for the
 * canonical naming convention.
 *
 * <p>Security:
 * <ul>
 *   <li>JWT auth required (enforced by SecurityConfig at the .anyRequest().authenticated() level)</li>
 *   <li>ROLE_ORGANIZER required (enforced here by @PreAuthorize)</li>
 *   <li>Prefix must match a server-side regex bound to the entity type</li>
 *   <li>Every invocation is logged at WARN level with caller + entityType + prefix + counts</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/test-fixtures/cums")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Test Fixture Cleanup", description = "Bruno test-data cleanup — organizer-only")
@SecurityRequirement(name = "bearerAuth")
public class TestFixtureCleanupController {

    private final TestFixtureCleanupService cleanupService;

    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "Delete Bruno test fixture rows matching a canonical prefix",
            description = "Removes rows from CUMS-owned tables (companies, user_profiles, "
                    + "logos) whose identifier column starts with the canonical Bruno test "
                    + "prefix for the given entity type. Cascade-deletes via FK constraints "
                    + "for role_assignments and user_additional_emails. ORGANIZER role required."
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Cleanup completed (may have deleted 0 rows if nothing matched)",
                content = @Content(schema = @Schema(implementation = TestFixtureCleanupResponse.class))
            ),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid entityType, missing prefix, or prefix doesn't match the bound regex"
            ),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "Caller lacks ORGANIZER role")
    })
    public ResponseEntity<TestFixtureCleanupResponse> cleanup(
            @Valid @RequestBody TestFixtureCleanupRequest request
    ) {
        log.info(
                "TestFixtureCleanup request: entityType={} prefix={}",
                request.getEntityType(), request.getPrefix()
        );
        return ResponseEntity.ok(cleanupService.cleanup(request));
    }
}
