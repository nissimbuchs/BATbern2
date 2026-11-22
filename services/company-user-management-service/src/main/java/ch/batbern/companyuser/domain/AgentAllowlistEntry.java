package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An organizer the agent may act for, a row of {@code agent_allowlist} (V41). The username is a
 * profile: the row goes with a deleted profile.
 */
@Entity
@Table(name = "agent_allowlist")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AgentAllowlistEntry {

    @Id
    @Column(name = "username", nullable = false, updatable = false, length = 100)
    private String username;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    /** The actor who put the username on the list ({@code Actors}). */
    @Column(name = "added_by", nullable = false, updatable = false, length = 255)
    private String addedBy;
}
