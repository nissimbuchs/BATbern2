package ch.batbern.companyuser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for the test-fixture cleanup endpoint.
 *
 * <p>The {@code prefix} field is the literal string prefix to match against the entity's
 * identifier column. The server validates the prefix against a constant per-entity regex
 * before any DELETE is issued — the request body cannot supply the regex, only the value.
 *
 * <p>Used exclusively by Bruno test cleanup workflows. ROLE_ORGANIZER required.
 * See {@code bruno-tests/README.md} for the canonical patterns per entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to delete test fixture rows matching a canonical prefix")
public class TestFixtureCleanupRequest {

    @NotNull(message = "entityType is required")
    @Schema(
            description = "Which entity table to clean. Each entity has its own bound prefix regex.",
            example = "companies",
            allowableValues = {"companies", "users"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String entityType;

    @NotBlank(message = "prefix is required")
    @Schema(
            description = "Literal prefix to match. Must satisfy the bound regex for the given entityType.",
            example = "BRUNOTESTCO",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String prefix;
}
