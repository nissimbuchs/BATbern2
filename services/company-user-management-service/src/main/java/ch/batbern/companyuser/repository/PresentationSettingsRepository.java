package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.PresentationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PresentationSettings}.
 *
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * The table uses a single-row pattern (id always 1).
 * Use {@code findById(1)} to retrieve the row; use {@code save()} to upsert.
 */
@Repository
public interface PresentationSettingsRepository extends JpaRepository<PresentationSettings, Integer> {
}
