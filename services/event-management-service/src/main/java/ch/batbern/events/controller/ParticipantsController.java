package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.AddParticipantRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.DistributionListService;
import ch.batbern.events.service.ParticipantsDocxExportService;
import ch.batbern.events.service.ParticipantsExportService;
import ch.batbern.events.service.RegistrationService;
import ch.batbern.shared.exception.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DistributionListService distributionListService;
    private final ParticipantsExportService participantsExportService;
    private final ParticipantsDocxExportService participantsDocxExportService;
    private final RegistrationService registrationService;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

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
            case "participants" -> distributionListService.resolveParticipants(eventCode);
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
     * Organizer "Add participant": add an existing user onto the event as a confirmed
     * participant (no self-registration / email-confirmation step). Organizer-only.
     *
     * @return 201 with the created registration's code/status; 404 unknown event/user;
     *         409 already-registered (generic) or capacity-full ({@code details.code =
     *         "capacity_exceeded"} unless {@code force=true}).
     */
    @PostMapping("/{eventCode}/participants")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Map<String, Object>> addParticipant(
            @PathVariable String eventCode,
            @Valid @RequestBody AddParticipantRequest request) {
        log.debug("POST /events/{}/participants username={}", eventCode, request.getUsername());

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        String addedBy = securityContextHelper.getCurrentUsername();
        Registration registration = registrationService.addParticipant(
                event, request.getUsername(), request.isForce(), request.isNotify(), addedBy);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registrationCode", registration.getRegistrationCode());
        body.put("status", registration.getStatus());
        body.put("attendeeUsername", registration.getAttendeeUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
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

    /**
     * Stream the name-badge DOCX (Avery L4784 + BAT logo) for the event's participants.
     * Same participant set / order as the XLSX export; printable as physical badges.
     */
    @GetMapping(value = "/{eventCode}/participants/export.docx", produces = DOCX_MIME)
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<byte[]> exportParticipantsDocx(@PathVariable String eventCode) {
        log.debug("GET /events/{}/participants/export.docx", eventCode);

        byte[] docx = participantsDocxExportService.generateNameBadgeDocx(eventCode);

        String filename = eventCode + "-namensschilder.docx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(DOCX_MIME));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(docx.length);
        return ResponseEntity.ok().headers(headers).body(docx);
    }
}
