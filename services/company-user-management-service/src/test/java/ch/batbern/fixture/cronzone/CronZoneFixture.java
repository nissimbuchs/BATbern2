package ch.batbern.fixture.cronzone;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

/**
 * Positive control for {@code ScheduledZoneGuard}: the cron shapes the guard must report, and the
 * ones it must not. Outside the service's packages, so the real scan never sees it. Not a bean.
 */
public class CronZoneFixture {

    @Scheduled(cron = "0 0 8 * * *")
    public void withoutZone() {
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    public void inUtc() {
    }

    @Scheduled(
            cron = "${fixture.cron:0 0 10 * * *}"
    )
    public void multiLineWithoutZone() {
    }

    @Schedules({
        @Scheduled(cron = "0 0 11 * * *", zone = "Europe/Zurich"),
        @Scheduled(cron = "0 0 12 * * *")
    })
    public void repeatedOneWithoutZone() {
    }

    @Scheduled(cron = "0 0 13 * * *", zone = "Europe/Zurich")
    public void inZurich() {
    }

    @Scheduled(fixedRate = 60_000)
    public void fixedRate() {
    }
}
