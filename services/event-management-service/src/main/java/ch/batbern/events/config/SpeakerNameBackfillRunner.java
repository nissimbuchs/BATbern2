package ch.batbern.events.config;

import ch.batbern.events.service.SpeakerNameBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * One-time backfill runner for speaker names
 *
 * USAGE: Start the service with --spring.profiles.active=backfill-speakers
 *
 * This will run once on startup, backfill all speaker names, then exit normally.
 * After backfill completes, restart the service without the profile.
 *
 * Example:
 *   ./gradlew :services:event-management-service:bootRun --args='--spring.profiles.active=backfill-speakers'
 */
@Component
@Profile("backfill-speakers")
@RequiredArgsConstructor
@Slf4j
public class SpeakerNameBackfillRunner implements ApplicationRunner {

    private final SpeakerNameBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("====================================");
        log.info("Starting speaker name backfill...");
        log.info("====================================");

        try {
            int updatedCount = backfillService.backfillSpeakerNames();

            log.info("====================================");
            log.info("Backfill completed successfully!");
            log.info("Updated {} speaker records", updatedCount);
            log.info("====================================");

        } catch (Exception e) {
            log.error("====================================");
            log.error("Backfill failed with error:", e);
            log.error("====================================");
        }
    }
}
