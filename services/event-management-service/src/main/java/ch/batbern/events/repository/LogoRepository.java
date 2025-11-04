package ch.batbern.events.repository;

import ch.batbern.events.domain.Logo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Logo entity
 * Story 2.5.3a: Event Theme Image Upload
 *
 * Accesses the same logos table as company-user-management-service
 */
@Repository
public interface LogoRepository extends JpaRepository<Logo, UUID> {

    /**
     * Find logo by upload ID
     * Used during association phase to link logo with event
     *
     * @param uploadId Upload identifier from presigned URL generation
     * @return Optional containing Logo if found
     */
    Optional<Logo> findByUploadId(String uploadId);
}
