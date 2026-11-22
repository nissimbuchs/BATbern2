package ch.batbern.agent.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** The runs of the agent. */
public interface AgentRunRepository extends JpaRepository<AgentRunEntity, UUID> {

    /** Whether a run already acts under the CUMS grant {@code grantRunId}. */
    boolean existsByGrantRunId(UUID grantRunId);
}
