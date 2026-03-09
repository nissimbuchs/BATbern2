package ch.batbern.events.repository;

import ch.batbern.events.domain.AiGenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, UUID> {
}
