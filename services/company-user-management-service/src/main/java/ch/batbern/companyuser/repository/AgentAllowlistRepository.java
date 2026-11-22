package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.AgentAllowlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** The agent allowlist, {@code agent_allowlist} (V41). */
public interface AgentAllowlistRepository extends JpaRepository<AgentAllowlistEntry, String> {

    /** Every entry, sorted by username. */
    List<AgentAllowlistEntry> findAllByOrderByUsernameAsc();
}
