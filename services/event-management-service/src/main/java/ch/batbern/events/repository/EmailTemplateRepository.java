package ch.batbern.events.repository;

import ch.batbern.events.domain.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EmailTemplate entity (Story 10.2).
 *
 * Provides lookup by (templateKey, locale) composite key — the ADR-003 public identifier.
 */
@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    Optional<EmailTemplate> findByTemplateKeyAndLocale(String templateKey, String locale);

    List<EmailTemplate> findByCategory(String category);

    List<EmailTemplate> findByLayoutTrue();

    boolean existsByTemplateKeyAndLocale(String templateKey, String locale);
}
