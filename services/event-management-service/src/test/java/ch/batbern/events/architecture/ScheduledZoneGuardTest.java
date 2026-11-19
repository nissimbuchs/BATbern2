package ch.batbern.events.architecture;

import ch.batbern.shared.test.ScheduledZoneGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every cron job runs on Swiss time (#1222): {@code zone = "Europe/Zurich"} on each
 * {@code @Scheduled(cron = ...)}, since the tasks run on UTC. See {@link ScheduledZoneGuard}.
 */
@DisplayName("Cron jobs state the Europe/Zurich zone")
class ScheduledZoneGuardTest {

    @Test
    @DisplayName("should_stateZurichZone_when_jobIsCron")
    void should_stateZurichZone_when_jobIsCron() {
        assertThat(ScheduledZoneGuard.cronsWithoutZurichZone("ch.batbern.events", "ch.batbern.shared"))
                .as("crons without zone = \"Europe/Zurich\" (they would run on UTC)")
                .isEmpty();
    }

    @Test
    @DisplayName("should_reportCron_when_zoneMissingOrWrong")
    void should_reportCron_when_zoneMissingOrWrong() {
        assertThat(ScheduledZoneGuard.cronsWithoutZurichZone("ch.batbern.fixture.cronzone"))
                .containsExactly(
                        "ch.batbern.fixture.cronzone.CronZoneFixture#inUtc cron='0 0 9 * * *' zone='UTC'",
                        "ch.batbern.fixture.cronzone.CronZoneFixture#multiLineWithoutZone"
                                + " cron='${fixture.cron:0 0 10 * * *}' zone=''",
                        "ch.batbern.fixture.cronzone.CronZoneFixture#repeatedOneWithoutZone"
                                + " cron='0 0 12 * * *' zone=''",
                        "ch.batbern.fixture.cronzone.CronZoneFixture#withoutZone cron='0 0 8 * * *' zone=''");
    }
}
