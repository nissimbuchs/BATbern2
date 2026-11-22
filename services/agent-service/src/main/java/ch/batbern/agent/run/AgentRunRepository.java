package ch.batbern.agent.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** The runs of the agent. */
public interface AgentRunRepository extends JpaRepository<AgentRunEntity, UUID> {
}
