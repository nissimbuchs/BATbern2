package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SessionMaterial entity
 * Story 5.9: Session Materials Upload - Task 1b (GREEN Phase)
 *
 * Provides CRUD operations and custom queries for session materials.
 * All queries use PostgreSQL indexes defined in V41 migration.
 */
@Repository
public interface SessionMaterialsRepository extends JpaRepository<SessionMaterial, UUID> {

    /**
     * Find all materials for a session
     * Uses idx_session_materials_session_id index
     *
     * @param sessionId The session UUID
     * @return List of materials for the session
     */
    List<SessionMaterial> findBySessionId(UUID sessionId);

    /**
     * Find material by unique upload ID (from Generic Upload Service)
     * Uses idx_session_materials_upload_id index and UNIQUE constraint
     *
     * @param uploadId The unique upload ID
     * @return Optional containing the material if found
     */
    Optional<SessionMaterial> findByUploadId(String uploadId);

    /**
     * Find materials by extraction status (for RAG pipeline - Story 5.10)
     * Uses idx_session_materials_extraction_status index
     *
     * @param extractionStatus The extraction status (PENDING, IN_PROGRESS, COMPLETED, FAILED, NOT_APPLICABLE)
     * @return List of materials with the specified extraction status
     */
    List<SessionMaterial> findByExtractionStatus(String extractionStatus);

    /**
     * Count materials for a session
     * Uses idx_session_materials_session_id index
     *
     * @param sessionId The session UUID
     * @return Number of materials for the session
     */
    long countBySessionId(UUID sessionId);

    /**
     * Check if session has material of specific type
     * Uses idx_session_materials_session_id and idx_session_materials_material_type indexes
     *
     * @param sessionId The session UUID
     * @param materialType The material type (PRESENTATION, DOCUMENT, VIDEO, OTHER)
     * @return true if session has at least one material of the specified type
     */
    boolean existsBySessionIdAndMaterialType(UUID sessionId, String materialType);
}
