package ch.batbern.events.controller;

import ch.batbern.events.dto.QnaWindowPatchRequest;
import ch.batbern.events.service.SessionQnaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Event-level Q&A control (Story 7.5 rework). Replaces the old per-session window PATCH: an
 * organizer extends or closes ALL of an event's session Q&A windows at once. Window *config*
 * (enabled / open-trigger / duration) is set via the event PATCH (Settings tab); this endpoint is
 * the manual extend / close-now action.
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/qna")
@RequiredArgsConstructor
@Slf4j
public class EventQnaController {

    private final SessionQnaService qnaService;

    /**
     * Organizer Q&A control for all of an event's session windows:
     * <ul>
     *   <li>{@code open=true} — open/reopen, creating windows for sessions that lack one (works
     *       even when none exist yet — independent of the configured trigger).</li>
     *   <li>{@code closesAt} — extend/shorten the existing open windows.</li>
     *   <li>{@code close=true} — freeze the windows now.</li>
     * </ul>
     */
    @PatchMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Map<String, Object>> adjustWindows(
            @PathVariable String eventCode,
            @Valid @RequestBody QnaWindowPatchRequest request) {
        if (Boolean.TRUE.equals(request.getOpen())) {
            int opened = qnaService.openWindowsManually(eventCode, request.getClosesAt());
            return ResponseEntity.ok(Map.of(
                    "eventCode", eventCode,
                    "windowsAdjusted", opened,
                    "status", "OPEN"));
        }
        int adjusted = qnaService.adjustWindows(eventCode, request.getClosesAt(), request.getClose());
        boolean closed = Boolean.TRUE.equals(request.getClose());
        return ResponseEntity.ok(Map.of(
                "eventCode", eventCode,
                "windowsAdjusted", adjusted,
                "status", closed ? "FROZEN" : "OPEN"));
    }
}
