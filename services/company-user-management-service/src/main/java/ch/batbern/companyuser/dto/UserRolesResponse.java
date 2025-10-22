package ch.batbern.companyuser.dto;

import ch.batbern.companyuser.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Response DTO for user roles
 *
 * Story 1.14-2 AC8: Role Management endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesResponse {

    /**
     * Username (Story 1.16.2: meaningful identifier)
     */
    private String username;

    /**
     * User's roles
     */
    private Set<Role> roles;
}
