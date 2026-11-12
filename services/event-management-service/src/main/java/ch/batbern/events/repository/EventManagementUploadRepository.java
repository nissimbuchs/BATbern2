package ch.batbern.events.repository;

import ch.batbern.events.domain.EventManagementUpload;
import ch.batbern.shared.upload.UploadRecordRepository;
import org.springframework.stereotype.Repository;

/** The uploads of this service (table {@code event_management_uploads}). */
@Repository
public interface EventManagementUploadRepository extends UploadRecordRepository<EventManagementUpload> {
}
