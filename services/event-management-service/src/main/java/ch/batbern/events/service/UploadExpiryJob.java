package ch.batbern.events.service;

import ch.batbern.events.domain.EventManagementUpload;
import ch.batbern.shared.upload.UploadService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly at 02:15: deletes uploads that were never confirmed (24 h) or never taken by an event
 * (7 d), with their S3 object (ADR-002). A failed delete is counted and retried the next night.
 */
@Component
@RequiredArgsConstructor
public class UploadExpiryJob {

    private final UploadService<EventManagementUpload> uploadService;

    @Scheduled(cron = "0 15 2 * * *")
    @SchedulerLock(name = "EventManagementUploadExpiry_purgeExpired", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public UploadService.ExpiryResult purgeExpired() {
        return uploadService.purgeExpired();
    }
}
