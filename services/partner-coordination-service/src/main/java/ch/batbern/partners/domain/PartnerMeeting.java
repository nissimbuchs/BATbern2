package ch.batbern.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Partner meeting record — Story 8.3: Partner Meeting Coordination.
 *
 * The meeting date matches the linked BATbern event date.
 * ADR-003: eventCode (String) links to event-management-service; no UUID FK.
 * ADR-003: createdBy stores organizer username; no UUID FK.
 *
 * Schema: partner_meetings (V2 baseline, updated in V5 migration).
 */
@Entity
@Table(name = "partner_meetings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * ADR-003: BATbern event code (e.g. "BATbern57"). No UUID FK.
     * The meeting always happens on the same day as the referenced event.
     */
    @Column(name = "event_code", nullable = false, length = 100)
    private String eventCode;

    /**
     * SPRING or AUTUMN — indicates which of the two annual partner meetings this is.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_type", nullable = false, length = 50)
    private MeetingType meetingType;

    /**
     * Meeting date — same as the BATbern event date.
     * Populated from event-management-service when the meeting is created.
     */
    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    /** Meeting start time (e.g. 12:00 — lunch start). */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** Meeting end time (e.g. 14:00 — before the BATbern event). */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Venue / location for the meeting. Usually same as the BATbern event venue. */
    @Column(name = "location", length = 500)
    private String location;

    /** Free-text agenda written by the organizer. Included in ICS description. */
    @Column(name = "agenda", columnDefinition = "TEXT")
    private String agenda;

    /** Post-meeting notes filled in by the organizer after the meeting. */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Timestamp when the calendar invite was last sent.
     * Null if no invite has been sent yet.
     */
    @Column(name = "invite_sent_at")
    private Instant inviteSentAt;

    /**
     * RFC 5545 SEQUENCE counter — incremented on every send-invite call so that
     * Outlook, Gmail, and macOS Mail treat the resend as an update of the existing
     * calendar entry rather than a new duplicate event.
     */
    @Column(name = "invite_sequence", nullable = false)
    private int inviteSequence;

    /**
     * ADR-003: organizer username who created this meeting record.
     * No UUID FK.
     */
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
