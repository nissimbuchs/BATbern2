package ch.batbern.migration.repository;

import ch.batbern.migration.model.target.MigrationError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Migration Error Repository
 *
 * Tracks migration errors for manual review and retry.
 * Story: 3.2.1 - Migration Tool Implementation
 */
@Repository
public interface MigrationErrorRepository extends JpaRepository<MigrationError, Long> {

    /**
     * Find all errors for a specific job execution
     */
    List<MigrationError> findByJobExecutionId(Long jobExecutionId);

    /**
     * Find all unresolved errors
     */
    List<MigrationError> findByResolvedFalse();

    /**
     * Find errors by entity type
     */
    List<MigrationError> findByEntityType(String entityType);

    /**
     * Count unresolved errors
     * Used for migration validation (should be 0 for successful migration)
     */
    long countByResolvedFalse();
}
