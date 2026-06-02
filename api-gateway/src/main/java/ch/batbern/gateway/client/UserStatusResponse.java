package ch.batbern.gateway.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal projection of the CUMS {@code GET /api/v1/users/{username}} response — only the
 * {@code active} flag the {@link ch.batbern.gateway.security.AccountActiveFilter} is_active
 * gate needs (Story 12.2). The full {@code UserResponse} contract is large and owned by
 * company-user-management-service; deserialising only {@code active} (ignoring everything
 * else) keeps the gateway decoupled from the rest of that contract.
 *
 * <p>Server-side {@code active} is populated by {@code UserResponseMapper.active(user.isActive())}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserStatusResponse {

    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
