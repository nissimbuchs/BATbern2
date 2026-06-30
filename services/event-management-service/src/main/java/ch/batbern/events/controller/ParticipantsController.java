package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.registrations.api.generated.ParticipantsApi;
import ch.batbern.events.registrations.dto.generated.AddParticipant201Response;
import ch.batbern.events.registrations.dto.generated.AddParticipantRequest;
import ch.batbern.events.registrations.dto.generated.DistributionListResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.DistributionListService;
import ch.batbern.events.service.ParticipantsDocxExportService;
import ch.batbern.events.service.ParticipantsExportService;
import ch.batbern.events.service.RegistrationService;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Set;

/**
 * Controller for event-participant download + per-event distribution-list resolution.
 *
 * <p>{@code implements} the generated {@link ParticipantsApi} interface (event-registrations
 * spec, ADR-006 contract-first). The interface supplies verb/path/param mapping and generated
 * DTO types; the class-level {@code @RequestMapping("/api/v1")} supplies the version prefix.
 *
 * <ul>
 *   <li>{@code GET /events/{eventCode}/distribution-list/{kind}} — internal-facing (anonymous
 *       in-VPC + organizer JWT); no {@code @PreAuthorize} so the anonymous forwarder-Lambda path
 *       is reachable (SecurityConfig path matchers grant access).</li>
 *   <li>{@code GET /events/{eventCode}/participants/export.xlsx|.docx} — organizer-only; streams
 *       the name-badge file produced by the export services.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ParticipantsController implements ParticipantsApi {

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
    @Override
    public ResponseEntity<DistributionListResponse> getEventDistributionList(
            String eventCode,
            String kind) {
        log.debug("GET /events/{}/distribution-list/{}", eventCode, kind);

        Set<String> emails = switch (kind) {
            case "speakers" -> distributionListService.resolveSpeakers(eventCode);
            case "moderator" -> distributionListService.resolveModerator(eventCode);
            case "participants" -> distributionListService.resolveParticipants(eventCode);
            default -> throw new NotFoundException("Unknown distribution-list kind: " + kind);
        };

        DistributionListResponse body = new DistributionListResponse(
                eventCode, DistributionListResponse.KindEnum.fromValue(kind), new ArrayList<>(emails));
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
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AddParticipant201Response> addParticipant(
            String eventCode,
            AddParticipantRequest request) {
        log.debug("POST /events/{}/participants username={}", eventCode, request.getUsername());

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        // Generated DTO booleans are nullable; preserve the spec defaults (force=false, notify=true).
        boolean force = Boolean.TRUE.equals(request.getForce());
        boolean notify = request.getNotify() == null || request.getNotify();

        String addedBy = securityContextHelper.getCurrentUsername();
        Registration registration = registrationService.addParticipant(
                event, request.getUsername(), force, notify, addedBy);

        AddParticipant201Response body = new AddParticipant201Response()
                .registrationCode(registration.getRegistrationCode())
                .status(registration.getStatus())
                .attendeeUsername(registration.getAttendeeUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Stream the name-badge XLSX for the event's participants.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Resource> exportParticipantsXlsx(String eventCode) {
        log.debug("GET /events/{}/participants/export.xlsx", eventCode);
        byte[] xlsx = participantsExportService.generateNameBadgeXlsx(eventCode);
        return badgeFileResponse(xlsx, eventCode + "-namensschilder.xlsx", XLSX_MIME);
    }

    /**
     * Stream the name-badge DOCX (Avery L4784 + BAT logo) for the event's participants.
     * Same participant set / order as the XLSX export; printable as physical badges.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Resource> exportParticipantsDocx(String eventCode) {
        log.debug("GET /events/{}/participants/export.docx", eventCode);
        byte[] docx = participantsDocxExportService.generateNameBadgeDocx(eventCode);
        return badgeFileResponse(docx, eventCode + "-namensschilder.docx", DOCX_MIME);
    }

    private static ResponseEntity<Resource> badgeFileResponse(byte[] content, String filename, String mime) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mime));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
    }
}
