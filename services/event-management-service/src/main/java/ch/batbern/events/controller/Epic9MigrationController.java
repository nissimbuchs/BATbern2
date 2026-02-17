package ch.batbern.events.controller;

import ch.batbern.events.dto.MigrationReport;
import ch.batbern.events.service.Epic9MigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Story 9.4: Admin endpoint to trigger the Epic 9 migration.
 *
 * AC8: POST /api/v1/admin/migrations/epic9?dryRun=false
 * Secured: requires valid Cognito JWT with ORGANIZER role.
 * NOT added to SecurityConfig permitAll() — requires authentication.
 */
@RestController
@RequestMapping("/api/v1/admin/migrations")
@RequiredArgsConstructor
@Slf4j
public class Epic9MigrationController {

    private final Epic9MigrationService migrationService;

    /**
     * Trigger Epic 9 migration for all ACCEPTED speakers.
     *
     * @param dryRun if true, validates without creating accounts or sending emails
     * @return MigrationReport with per-speaker outcomes and aggregate counts
     */
    @PostMapping("/epic9")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MigrationReport> runMigration(
            @RequestParam(defaultValue = "false") boolean dryRun) {

        log.info("Epic 9 migration endpoint called (dryRun={})", dryRun);
        MigrationReport report = migrationService.migrate(dryRun);
        log.info("Epic 9 migration endpoint complete (dryRun={}): total={}, provisionedNew={}, "
                        + "extended={}, emailsSent={}, errors={}",
                dryRun, report.getTotal(), report.getProvisionedNew(),
                report.getExtended(), report.getEmailsSent(), report.getErrors());

        return ResponseEntity.ok(report);
    }
}
