package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Organizer request to adjust a Q&A window (Story 7.5, AC4).
 *
 * <p>Two independent levers (at least one required):
 * <ul>
 *   <li>{@code closesAt} — extend (or shorten) the open window to a new close time.</li>
 *   <li>{@code close = true} — close the window early (freeze immediately).</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class QnaWindowPatchRequest {

    private Instant closesAt;

    private Boolean close;

    public QnaWindowPatchRequest() {
    }

    public QnaWindowPatchRequest(Instant closesAt, Boolean close) {
        this.closesAt = closesAt;
        this.close = close;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(Instant closesAt) {
        this.closesAt = closesAt;
    }

    public Boolean getClose() {
        return close;
    }

    public void setClose(Boolean close) {
        this.close = close;
    }
}
