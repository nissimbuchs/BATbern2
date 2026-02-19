package ch.batbern.events.watch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "speaker_arrivals",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_code", "speaker_username"})
)
public class SpeakerArrival {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_code", nullable = false, length = 50)
    private String eventCode;

    @Column(name = "speaker_username", nullable = false, length = 100)
    private String speakerUsername;

    @Column(name = "confirmed_by_username", nullable = false, length = 100)
    private String confirmedByUsername;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    public SpeakerArrival() {}

    public SpeakerArrival(String eventCode, String speakerUsername, String confirmedByUsername) {
        this.eventCode = eventCode;
        this.speakerUsername = speakerUsername;
        this.confirmedByUsername = confirmedByUsername;
        this.arrivedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventCode() {
        return eventCode;
    }

    public String getSpeakerUsername() {
        return speakerUsername;
    }

    public String getConfirmedByUsername() {
        return confirmedByUsername;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }
}
