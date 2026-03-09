package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * TaskTemplate entity for reusable event planning task templates (Story 5.5 AC19-27).
 *
 * Represents configurable task templates that can be applied to events. Templates define
 * when tasks should be created (trigger_state) and when they're due (due_date_type + offset).
 *
 * Default templates are system-provided, custom templates are created by organizers.
 */
@Entity
@Table(name = "task_templates")
public class TaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Event workflow state that triggers task creation.
     * Values: 'topic_selection', 'agenda_published', 'event_live', etc.
     */
    @Column(name = "trigger_state", nullable = false, length = 50)
    private String triggerState;

    /**
     * Type of due date calculation.
     * - 'immediate': Task due immediately when created
     * - 'relative_to_event': Due X days before/after event date
     * - 'absolute': Specific date (stored separately)
     */
    @Column(name = "due_date_type", nullable = false, length = 20)
    private String dueDateType;

    /**
     * Offset in days for relative due dates.
     * Negative values = before event, positive = after event.
     * Example: -90 means 90 days before event
     */
    @Column(name = "due_date_offset_days")
    private Integer dueDateOffsetDays;

    /**
     * Whether this is a system-provided default template (read-only).
     * Default templates cannot be edited or deleted by organizers.
     */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "created_by_username", length = 100)
    private String createdByUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors

    public TaskTemplate() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTriggerState() {
        return triggerState;
    }

    public void setTriggerState(String triggerState) {
        this.triggerState = triggerState;
    }

    public String getDueDateType() {
        return dueDateType;
    }

    public void setDueDateType(String dueDateType) {
        this.dueDateType = dueDateType;
    }

    public Integer getDueDateOffsetDays() {
        return dueDateOffsetDays;
    }

    public void setDueDateOffsetDays(Integer dueDateOffsetDays) {
        this.dueDateOffsetDays = dueDateOffsetDays;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
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
