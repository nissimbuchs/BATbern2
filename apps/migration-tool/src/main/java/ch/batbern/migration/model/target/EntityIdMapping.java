package ch.batbern.migration.model.target;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity ID Mapping
 *
 * Maps legacy IDs to new UUIDs for foreign key resolution across migration jobs.
 * Example: Legacy company name "mobiliar" → new UUID for User.companyId lookup
 *
 * Story: 3.2.1 - Migration Tool Implementation
 */
@Entity
@Table(name = "entity_id_mapping",
    uniqueConstraints = @UniqueConstraint(columnNames = {"entity_type", "legacy_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityIdMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "legacy_id", nullable = false)
    private String legacyId;

    @Column(name = "new_id", nullable = false, columnDefinition = "UUID")
    private UUID newId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating new mappings
     */
    public EntityIdMapping(String entityType, String legacyId, UUID newId) {
        this.entityType = entityType;
        this.legacyId = legacyId;
        this.newId = newId;
    }
}
