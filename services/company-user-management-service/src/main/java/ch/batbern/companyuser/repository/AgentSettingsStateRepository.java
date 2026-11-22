package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.AgentSettingsState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** The singleton {@code agent_settings} row (V41). */
public interface AgentSettingsStateRepository extends JpaRepository<AgentSettingsState, Integer> {

    /** The row, locked for the rest of the transaction, so concurrent changes run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AgentSettingsState s WHERE s.id = :id")
    Optional<AgentSettingsState> findByIdForUpdate(@Param("id") Integer id);
}
