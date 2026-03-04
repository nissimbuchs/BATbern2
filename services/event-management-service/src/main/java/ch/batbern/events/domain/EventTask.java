package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * EventTask entity for event-specific tasks assigned to organizers (Story 5.5 AC19-27).
 *
 * Represents concrete tasks created from templates for specific events. Tasks can be assigned
 * to organizers and tracked through completion. Supports optimistic locking to handle concurrent
 * updates when multiple organizers work on the same event.
 */
@Entity
@Table(name = "event_tasks")
public class EventTask {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    /**
     * Reference to the template this task was created from.
     * Null for ad-hoc tasks created directly (not from template).
     */
    @Column(name = "template_id", columnDefinition = "UUID")
    private UUID templateId;

    @Column(name = "task_name", nullable = false, length = 255)
    private String taskName;

    /**
     * Event workflow state that triggered this task's creation.
     * Values: 'topic_selection', 'agenda_published', 'agenda_finalized', etc.
     */
    @Column(name = "trigger_state", nullable = false, length = 50)
    private String triggerState;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "assigned_organizer_username", length = 100)
    private String assignedOrganizerUsername;

    /**
     * Task status: 'pending', 'todo', 'in_progress', 'completed', 'cancelled'
     * (V78 migration added 'cancelled' for archived-event cleanup, Story 10.18)
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "todo";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "completed_date")
    private Instant completedDate;

    @Column(name = "completed_by_username", length = 100)
    private String completedByUsername;

    @Column(name = "cancelled_reason", length = 255)
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * Version field for optimistic locking (AC35).
     * Prevents race conditions when multiple organizers update task status concurrently.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors

    public EventTask() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTriggerState() {
        return triggerState;
    }

    public void setTriggerState(String triggerState) {
        this.triggerState = triggerState;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public String getAssignedOrganizerUsername() {
        return assignedOrganizerUsername;
    }

    public void setAssignedOrganizerUsername(String assignedOrganizerUsername) {
        this.assignedOrganizerUsername = assignedOrganizerUsername;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Instant completedDate) {
        this.completedDate = completedDate;
    }

    public String getCompletedByUsername() {
        return completedByUsername;
    }

    public void setCompletedByUsername(String completedByUsername) {
        this.completedByUsername = completedByUsername;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }

    public void setCancelledReason(String cancelledReason) {
        this.cancelledReason = cancelledReason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
