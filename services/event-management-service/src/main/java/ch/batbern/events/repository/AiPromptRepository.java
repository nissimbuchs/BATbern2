package ch.batbern.events.repository;

import ch.batbern.events.domain.AiPrompt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPromptRepository extends JpaRepository<AiPrompt, String> {
}
