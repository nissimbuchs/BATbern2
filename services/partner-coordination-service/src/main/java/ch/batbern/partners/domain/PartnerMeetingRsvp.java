package ch.batbern.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * iCal RSVP record for partner meeting calendar invites — Story 10.27 (AC5).
 *
 * Stored in partner_meeting_rsvps.
 * UNIQUE(meeting_id, attendee_email) enables upsert: re-responses update in-place.
 */
@Entity
@Table(
    name = "partner_meeting_rsvps",
    uniqueConstraints = @UniqueConstraint(columnNames = {"meeting_id", "attendee_email"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerMeetingRsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(name = "attendee_email", nullable = false, length = 255)
    private String attendeeEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RsvpStatus status;

    @Column(name = "responded_at", nullable = false)
    private Instant respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
