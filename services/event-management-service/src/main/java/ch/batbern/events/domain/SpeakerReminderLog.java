package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Speaker Reminder Log entity for tracking sent reminders.
 * Story 6.5: Automated Deadline Reminders (AC3, AC5)
 *
 * Used for:
 * - Deduplication: prevent sending same tier reminder twice for same deadline
 * - Audit trail: track all automated and manual reminders sent
 */
@Entity
@Table(name = "speaker_reminder_log")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "speaker_pool_id", nullable = false, columnDefinition = "UUID")
    private UUID speakerPoolId;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    /** Type of deadline: RESPONSE or CONTENT */
    @Column(name = "reminder_type", nullable = false, length = 20)
    private String reminderType;

    /** Escalation tier: TIER_1, TIER_2, TIER_3 */
    @Column(name = "tier", nullable = false, length = 10)
    private String tier;

    @Column(name = "email_address", nullable = false, length = 255)
    private String emailAddress;

    @Column(name = "deadline_date", nullable = false)
    private LocalDate deadlineDate;

    /** SYSTEM for automated, organizer username for manual triggers */
    @Column(name = "triggered_by", nullable = false, length = 100)
    @Builder.Default
    private String triggeredBy = "SYSTEM";

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
