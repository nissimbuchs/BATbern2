package ch.batbern.events.controller;

import ch.batbern.events.dto.TestFixtureCleanupRequest;
import ch.batbern.events.dto.TestFixtureCleanupResponse;
import ch.batbern.events.service.TestFixtureCleanupService;
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
 * Bruno test-fixture cleanup endpoint for EMS.
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
 *
 * <p><b>Why this controller is intentionally NOT {@code @Profile}-gated:</b>
 * BATbern's consolidated AWS layout (staging account 188701360969 serves
 * production traffic at api.batbern.ch) means a {@code @Profile("!production")}
 * guard would block exactly the environment Bruno needs to clean up after.
 * The safety properties come from the layered checks above (JWT + ORGANIZER
 * role + server-side regex prefix validation + audit log), not from a profile
 * switch.
 */
@RestController
@RequestMapping("/api/v1/admin/test-fixtures/ems")
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
            description = "Removes rows from EMS-owned tables (events, sessions, topics) whose "
                    + "identifier column starts with the canonical Bruno test prefix for the "
                    + "given entity type. Cascade-deletes via FK constraints handle dependent "
                    + "rows in event_tasks, speaker_pool, registrations, session_users, "
                    + "session_materials, topic_usage_history, etc. Bypasses the event workflow "
                    + "state machine — native DELETE is used regardless of current state. "
                    + "ORGANIZER role required."
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
