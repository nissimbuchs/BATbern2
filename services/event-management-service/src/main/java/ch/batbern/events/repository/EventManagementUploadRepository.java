package ch.batbern.events.repository;

import ch.batbern.events.domain.EventManagementUpload;
import ch.batbern.shared.upload.UploadRecordRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** The uploads of this service (table {@code event_management_uploads}). */
@Repository
public interface EventManagementUploadRepository extends UploadRecordRepository<EventManagementUpload> {

    /** The upload stored at this key (a teaser image's, Story 17.11c), if any. */
    Optional<EventManagementUpload> findByPurposeAndS3Key(String purpose, String s3Key);
}
