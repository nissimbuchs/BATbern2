package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The organizer agent's switch and delegate, the singleton row of {@code agent_settings} (V41,
 * {@code id = 1}). The allowlist is {@link AgentAllowlistEntry}. A missing row reads as off.
 */
@Entity
@Table(name = "agent_settings")
@Getter
@Setter
@NoArgsConstructor
public class AgentSettingsState {

    /** The only id the table allows ({@code CHECK (id = 1)}). */
    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id = SINGLETON_ID;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** The organizer delegate; {@code null} when there is none. */
    @Column(name = "delegate_username", length = 100)
    private String delegateUsername;

    /** {@code null} before the first change. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** The actor of the last change ({@code Actors}); {@code null} before the first change. */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
