package ch.batbern.companyuser.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a company is verified
 * Published to EventBridge for downstream services to consume
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyVerifiedEvent {

    /**
     * Unique company identifier
     */
    private UUID companyId;

    /**
     * Company name
     */
    private String name;

    /**
     * Swiss UID (if provided)
     */
    private String swissUID;

    /**
     * Verification status
     */
    private boolean isVerified;

    /**
     * Timestamp when the company was verified
     */
    private Instant verifiedAt;

    /**
     * Event timestamp
     */
    private Instant eventTimestamp;
}
