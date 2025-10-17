package ch.batbern.companyuser.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a company is deleted
 * Published to EventBridge for downstream services to consume
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDeletedEvent {

    /**
     * Unique company identifier
     */
    private UUID companyId;

    /**
     * Company name at time of deletion
     */
    private String name;

    /**
     * Timestamp when the company was deleted
     */
    private Instant deletedAt;

    /**
     * Event timestamp
     */
    private Instant eventTimestamp;
}
