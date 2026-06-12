package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request to post a Q&A question or answer (Story 7.5).
 *
 * <p>{@code parentPostId} = the question being answered (one-level threading); {@code null} for a
 * top-level question. Identity is taken from the JWT server-side, never the body.
 * {@code @JsonIgnoreProperties(ignoreUnknown = false)}: an unexpected field (e.g.
 * {@code postedByUsername}) is rejected with 400.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class QnaPostRequest {

    @NotBlank(message = "body is required")
    @Size(max = 5000, message = "body must be at most 5000 characters")
    private String body;

    private UUID parentPostId;

    public QnaPostRequest() {
    }

    public QnaPostRequest(String body, UUID parentPostId) {
        this.body = body;
        this.parentPostId = parentPostId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public UUID getParentPostId() {
        return parentPostId;
    }

    public void setParentPostId(UUID parentPostId) {
        this.parentPostId = parentPostId;
    }
}
