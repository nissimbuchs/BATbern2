package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Organizer-editable OpenAI prompt configuration (Story 10.x - AI Prompt Management).
 *
 * Three rows are seeded by migration V82 (event_description, theme_image, abstract_quality).
 * The default_text column preserves the original prompt to support "Reset to default".
 *
 * Database table: ai_prompts
 */
@Entity
@Table(name = "ai_prompts")
public class AiPrompt {

    @Id
    @Column(name = "prompt_key", length = 50)
    private String promptKey;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "default_text", nullable = false, columnDefinition = "TEXT")
    private String defaultText;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getPromptKey() {
        return promptKey;
    }

    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getDefaultText() {
        return defaultText;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
