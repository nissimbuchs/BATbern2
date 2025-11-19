package ch.batbern.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Session entity representing event sessions (keynotes, workshops, talks, etc.)
 * Story 1.15a.1: Events API Consolidation - AC9-10
 * Story 1.16.2: Uses sessionSlug as public identifier instead of UUID
 */
@Entity
@Table(name = "sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"sessionUsers"})  // Prevent circular reference in bidirectional relationship
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @JsonIgnore // Story 1.16.2: Hide internal UUID from API responses
    private UUID id;

    @Column(name = "session_slug", nullable = false, unique = true, length = 200)
    private String sessionSlug; // Public identifier: URL-friendly slug from title

    @Column(nullable = false, columnDefinition = "UUID")
    @JsonIgnore // Story 1.16.2: Hide internal UUID from API responses
    private UUID eventId;

    @Transient
    private String eventCode; // Not persisted - populated from path parameter for API responses

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "session_type", nullable = false, length = 50)
    private String sessionType; // keynote, presentation, workshop, panel_discussion, networking, break, lunch

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(length = 100)
    private String room;

    @Column
    private Integer capacity;

    @Column(length = 10)
    private String language;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Many-to-many relationship with users (speakers)
     * Story 4.1.4: Sessions can have multiple speakers with different roles
     * ADR-004: References User entities via userId in SessionUser
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SessionUser> sessionUsers = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Helper method to add a speaker to this session
     */
    public void addSpeaker(SessionUser sessionUser) {
        sessionUsers.add(sessionUser);
        sessionUser.setSession(this);
    }

    /**
     * Helper method to remove a speaker from this session
     */
    public void removeSpeaker(SessionUser sessionUser) {
        sessionUsers.remove(sessionUser);
        sessionUser.setSession(null);
    }
}
