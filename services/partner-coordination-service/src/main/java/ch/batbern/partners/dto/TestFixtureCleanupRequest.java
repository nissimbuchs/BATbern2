package ch.batbern.partners.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for the PCS test-fixture cleanup endpoint.
 *
 * <p>Two cleanup strategies, selected by {@code entityType}:
 * <ul>
 *   <li><b>Prefix-based</b> ({@code partners}): the {@code prefix} field is a literal string
 *       matched against the entity's identifier column. The server validates the prefix against
 *       a constant per-entity regex before any DELETE — the request body cannot supply the regex,
 *       only the value.</li>
 *   <li><b>Id-allowlist</b> ({@code meetings}): {@code partner_meetings} has no Bruno-identifying
 *       column (meetings are standalone, not prefixable), so cleanup is by an explicit
 *       {@code meetingIds} allowlist that Bruno collects from its create-meeting tests and posts
 *       back. Tighter than a prefix and safe on the shared production account (only IDs the caller
 *       created are ever supplied). {@code prefix} is ignored for this entityType.</li>
 * </ul>
 *
 * <p>Used exclusively by Bruno test cleanup workflows. ROLE_ORGANIZER required.
 * See {@code bruno-tests/README.md} for the canonical patterns per entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to delete PCS test fixture rows by canonical prefix or explicit id allowlist")
public class TestFixtureCleanupRequest {

    @NotNull(message = "entityType is required")
    @Schema(
            description = "Which PCS-owned entity table to clean. Each entity has its own bound prefix regex.",
            example = "partners",
            allowableValues = {"partners", "meetings"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String entityType;

    @Schema(
            description = "Literal prefix to match (entityType=partners). Must satisfy the bound regex "
                    + "for the entityType. Ignored for entityType=meetings.",
            example = "brtest"
    )
    private String prefix;

    @Schema(
            description = "Explicit allowlist of partner_meeting UUIDs to delete (entityType=meetings). "
                    + "partner_meetings has no Bruno-identifying column, so cleanup is by exact ID. "
                    + "Ignored for entityType=partners.",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]"
    )
    private List<UUID> meetingIds;
}
