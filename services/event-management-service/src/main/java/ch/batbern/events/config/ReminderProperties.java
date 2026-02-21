package ch.batbern.events.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for speaker deadline reminders.
 * Story 6.5: Automated Deadline Reminders (AC1)
 *
 * Reads from application.yml under batbern.reminders prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "batbern.reminders")
@Data
public class ReminderProperties {

    /** Whether reminder scheduling is enabled */
    private boolean enabled = true;

    /** Cron expression for the scheduler (default: 8 AM daily) */
    private String cron = "0 0 8 * * *";

    /** Tier configurations defining when reminders are sent */
    private List<TierConfig> tiers = List.of(
            new TierConfig("TIER_1", 14),
            new TierConfig("TIER_2", 7),
            new TierConfig("TIER_3", 3)
    );

    /** Whether to send organizer escalation notification after Tier 3 */
    private boolean escalateAfterTier3 = true;

    @Data
    public static class TierConfig {
        private String tier;
        private int daysBeforeDeadline;

        public TierConfig() {
        }

        public TierConfig(String tier, int daysBeforeDeadline) {
            this.tier = tier;
            this.daysBeforeDeadline = daysBeforeDeadline;
        }
    }
}
