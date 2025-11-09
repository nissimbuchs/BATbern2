package ch.batbern.partners.dto.generated;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request to add a new contact to a partner.
 *
 * Per ADR-003: Uses username (String), NOT userId (UUID).
 */
@Getter
@Setter
public class AddPartnerContactRequest {

    @NotNull
    @JsonProperty("username")
    private String username;

    @NotNull
    @JsonProperty("contactRole")
    private ContactRoleEnum contactRole;

    @NotNull
    @JsonProperty("isPrimary")
    private Boolean isPrimary;

    /**
     * Contact role enum matching the generated ContactRole
     */
    public enum ContactRoleEnum {
        PRIMARY,
        BILLING,
        TECHNICAL,
        MARKETING;

        @Override
        public String toString() {
            return name();
        }
    }
}
