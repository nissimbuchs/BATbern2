package ch.batbern.shared.upload;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The queries {@link UploadService} needs; each service declares one for its own table. */
@NoRepositoryBean
public interface UploadRecordRepository<T extends UploadRecord> extends JpaRepository<T, UUID> {

    Optional<T> findByUploadId(String uploadId);

    List<T> findByStatusInAndExpiresAtBefore(List<UploadStatus> statuses, Instant before);
}
