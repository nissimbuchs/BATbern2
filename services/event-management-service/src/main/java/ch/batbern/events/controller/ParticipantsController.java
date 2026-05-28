package ch.batbern.events.controller;

import ch.batbern.events.service.DistributionListService;
import ch.batbern.events.service.ParticipantsExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Controller for event-participant download + per-event distribution-list resolution.
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}.
 *
 * <ul>
 *   <li>{@code GET /events/{eventCode}/distribution-list/{kind}} — internal-facing (anonymous
 *       in-VPC + organizer JWT); same pattern as the existing
 *       {@code GET /events/{eventCode}/registrations} consumed by the SES forwarder Lambda.</li>
 *   <li>{@code GET /events/{eventCode}/participants/export.xlsx} — organizer-only; streams
 *       a name-badge XLSX produced by {@link ParticipantsExportService}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
public class ParticipantsController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final DistributionListService distributionListService;
    private final ParticipantsExportService participantsExportService;

    /**
     * Resolve the distribution list for an event/kind pair. Anonymous from the in-VPC
     * forwarder Lambda; organizer JWT works too. No {@code @PreAuthorize} so the anonymous
     * path is reachable — the path matchers in {@code SecurityConfig} grant access.
     */
    @GetMapping("/{eventCode}/distribution-list/{kind}")
    public ResponseEntity<Map<String, Object>> getDistributionList(
            @PathVariable String eventCode,
            @PathVariable String kind) {
        log.debug("GET /events/{}/distribution-list/{}", eventCode, kind);

        Set<String> emails = switch (kind) {
            case "speakers" -> distributionListService.resolveSpeakers(eventCode);
            case "moderator" -> distributionListService.resolveModerator(eventCode);
            default -> throw new ch.batbern.shared.exception.NotFoundException(
                    "Unknown distribution-list kind: " + kind);
        };

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventCode", eventCode);
        body.put("kind", kind);
        body.put("emails", emails);
        return ResponseEntity.ok(body);
    }

    /**
     * Stream the name-badge XLSX for the event's participants.
     */
    @GetMapping(value = "/{eventCode}/participants/export.xlsx", produces = XLSX_MIME)
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<byte[]> exportParticipantsXlsx(@PathVariable String eventCode) {
        log.debug("GET /events/{}/participants/export.xlsx", eventCode);

        byte[] xlsx = participantsExportService.generateNameBadgeXlsx(eventCode);

        String filename = eventCode + "-namensschilder.xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(XLSX_MIME));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(xlsx.length);
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}
