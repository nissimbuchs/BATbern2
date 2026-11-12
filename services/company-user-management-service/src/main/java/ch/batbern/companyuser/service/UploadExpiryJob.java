package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.CompanyUserUpload;
import ch.batbern.shared.upload.UploadService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly at 02:00: deletes uploads that were never confirmed (24 h) or never taken by an entity
 * (7 d), with their S3 object (ADR-002). A failed delete is counted and retried the next night.
 */
@Component
@RequiredArgsConstructor
public class UploadExpiryJob {

    private final UploadService<CompanyUserUpload> uploadService;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "CompanyUserUploadExpiry_purgeExpired", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public UploadService.ExpiryResult purgeExpired() {
        return uploadService.purgeExpired();
    }
}
