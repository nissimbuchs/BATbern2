package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Organizer request to adjust a Q&A window (Story 7.5, AC4).
 *
 * <p>Levers (at least one required), event-level (Story 7.5 rework):
 * <ul>
 *   <li>{@code open = true} — open/reopen the Q&A: create a window for every session that lacks
 *       one and (re)open the rest. Works even when no windows exist yet (manual open, independent
 *       of the configured trigger). Optional {@code closesAt} overrides the default close time.</li>
 *   <li>{@code closesAt} (without {@code open}) — extend (or shorten) the EXISTING open windows.</li>
 *   <li>{@code close = true} — close the windows early (freeze immediately).</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class QnaWindowPatchRequest {

    private Instant closesAt;

    private Boolean close;

    private Boolean open;

    public QnaWindowPatchRequest() {
    }

    public QnaWindowPatchRequest(Instant closesAt, Boolean close) {
        this.closesAt = closesAt;
        this.close = close;
    }

    public Boolean getOpen() {
        return open;
    }

    public void setOpen(Boolean open) {
        this.open = open;
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
