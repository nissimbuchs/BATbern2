package ch.batbern.events.dto;

/**
 * Filter request for topic queries (Story 5.2).
 *
 * Used for JSON deserial

ization of filter parameter.
 * Supports Jackson ObjectMapper for type-safe parsing.
 */
public class TopicFilterRequest {

    private String category;
    private String status;

    public TopicFilterRequest() {
    }

    public TopicFilterRequest(String category, String status) {
        this.category = category;
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
