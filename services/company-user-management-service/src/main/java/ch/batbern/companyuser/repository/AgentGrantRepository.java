package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.AgentGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/** Agent run grants, {@code agent_grants} (V42). */
public interface AgentGrantRepository extends JpaRepository<AgentGrant, String> {

    /** The grant of {@code runId} while it has not expired at {@code now}. */
    Optional<AgentGrant> findByRunIdAndExpiresAtAfter(String runId, Instant now);
}
