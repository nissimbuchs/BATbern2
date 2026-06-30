package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.EventQnaApi;
import ch.batbern.events.core.dto.generated.QnaWindowAdjustResponse;
import ch.batbern.events.core.dto.generated.QnaWindowPatchRequest;
import ch.batbern.events.service.SessionQnaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Event-level Q&A control (Story 7.5 rework). An organizer extends or closes ALL of an event's
 * session Q&A windows at once. Window *config* (enabled / open-trigger / duration) is set via the
 * event PATCH (Settings tab); this endpoint is the manual extend / close-now action.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link EventQnaApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventQnaController implements EventQnaApi {

    private final SessionQnaService qnaService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<QnaWindowAdjustResponse> adjustQnaWindows(
            String eventCode,
            QnaWindowPatchRequest request) {
        Instant closesAt = request.getClosesAt() != null ? request.getClosesAt().toInstant() : null;
        if (Boolean.TRUE.equals(request.getOpen())) {
            int opened = qnaService.openWindowsManually(eventCode, closesAt);
            return ResponseEntity.ok(QnaWindowAdjustResponse.builder()
                    .eventCode(eventCode)
                    .windowsAdjusted(opened)
                    .status("OPEN")
                    .build());
        }
        int adjusted = qnaService.adjustWindows(eventCode, closesAt, request.getClose());
        boolean closed = Boolean.TRUE.equals(request.getClose());
        return ResponseEntity.ok(QnaWindowAdjustResponse.builder()
                .eventCode(eventCode)
                .windowsAdjusted(adjusted)
                .status(closed ? "FROZEN" : "OPEN")
                .build());
    }
}
