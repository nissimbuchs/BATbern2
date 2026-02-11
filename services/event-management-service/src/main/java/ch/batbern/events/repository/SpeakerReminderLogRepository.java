package ch.batbern.events.repository;

import ch.batbern.events.domain.SpeakerReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for SpeakerReminderLog entity.
 * Story 6.5: Automated Deadline Reminders (AC3, AC5)
 */
@Repository
public interface SpeakerReminderLogRepository extends JpaRepository<SpeakerReminderLog, UUID> {

    /**
     * Check if a SYSTEM-triggered reminder of the same tier has already been sent
     * for a given speaker, reminder type, and deadline date.
     * Used for deduplication in automated reminders (AC3).
     */
    boolean existsBySpeakerPoolIdAndReminderTypeAndTierAndDeadlineDateAndTriggeredBy(
            UUID speakerPoolId, String reminderType, String tier, LocalDate deadlineDate, String triggeredBy);

    /**
     * Find all reminders for a specific speaker pool entry.
     * Used for audit trail and history display.
     */
    List<SpeakerReminderLog> findBySpeakerPoolIdOrderBySentAtDesc(UUID speakerPoolId);

    /**
     * Find all reminders for a specific event.
     * Used for event-level reminder reporting.
     */
    List<SpeakerReminderLog> findByEventIdOrderBySentAtDesc(UUID eventId);
}
