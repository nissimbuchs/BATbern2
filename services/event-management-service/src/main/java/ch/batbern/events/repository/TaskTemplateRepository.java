package ch.batbern.events.repository;

import ch.batbern.events.domain.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for TaskTemplate entity (Story 5.5 AC19-27).
 *
 * Provides data access for task template management. Templates can be default (system-provided)
 * or custom (created by organizers).
 */
@Repository
public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, UUID> {

    /**
     * Find all default task templates (system-provided).
     *
     * @return list of default templates
     */
    List<TaskTemplate> findByIsDefaultTrue();

    /**
     * Find all custom task templates (organizer-created).
     *
     * @return list of custom templates
     */
    List<TaskTemplate> findByIsDefaultFalse();

    /**
     * Find task templates by trigger state.
     * Used when event transitions to a workflow state to find matching tasks to create.
     *
     * @param triggerState the workflow state that triggers tasks
     * @return list of templates triggered by this state
     */
    List<TaskTemplate> findByTriggerState(String triggerState);

    /**
     * Find templates created by a specific organizer.
     *
     * @param username the organizer's username
     * @return list of templates created by this organizer
     */
    List<TaskTemplate> findByCreatedByUsername(String username);
}
