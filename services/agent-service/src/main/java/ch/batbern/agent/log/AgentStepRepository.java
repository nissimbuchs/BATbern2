package ch.batbern.agent.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Rows of the decision log; written only through {@link DecisionLog}. */
public interface AgentStepRepository extends JpaRepository<AgentStepEntity, UUID> {
}
