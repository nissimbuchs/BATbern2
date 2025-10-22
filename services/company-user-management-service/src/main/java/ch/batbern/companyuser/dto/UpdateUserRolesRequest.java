package ch.batbern.companyuser.dto;

import ch.batbern.companyuser.domain.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request DTO for updating user roles
 *
 * Story 1.14-2 AC8: Role Management endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRolesRequest {

    @NotEmpty(message = "Roles cannot be empty")
    private Set<Role> roles;
}
