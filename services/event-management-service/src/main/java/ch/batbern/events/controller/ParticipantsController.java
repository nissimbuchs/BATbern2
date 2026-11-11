package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.registrations.api.generated.ParticipantsApi;
import ch.batbern.events.registrations.dto.generated.AddParticipantsRequest;
import ch.batbern.events.registrations.dto.generated.AddParticipantsResponse;
import ch.batbern.events.registrations.dto.generated.DistributionListResponse;
import ch.batbern.events.registrations.dto.generated.RegistrationPage;
import ch.batbern.events.dto.RegistrationResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.DistributionListService;
import ch.batbern.events.service.ParticipantsDocxExportService;
import ch.batbern.events.service.ParticipantsExportService;
import ch.batbern.events.service.RegistrationService;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import ch.batbern.shared.api.FilterParser;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * Controller for event participants: create, badge downloads and per-event distribution lists.
 *
 * <p>{@code implements} the generated {@link ParticipantsApi} interface (event-registrations
 * spec, ADR-006 contract-first). The interface supplies verb/path/param mapping and generated
 * DTO types; the class-level {@code @RequestMapping("/api/v1")} supplies the version prefix.
 *
 * <ul>
 *   <li>{@code POST /events/{eventCode}/participants} — organizer-only; the one create of
 *       participants: a single user, a historical import list or the stakeholders (4.10c).</li>
 *   <li>{@code GET /events/{eventCode}/distribution-list/{kind}} — organizer-only (email
 *       addresses). Alias forwarding resolves the same lists in-process via
 *       {@code DistributionListService}, not over HTTP.</li>
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
     * Resolve the distribution list for an event/kind pair. Organizers only: the response is a
     * list of email addresses.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
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
     * The one create of participants (Story 4.10c): exactly one of {@code username} (single add,
     * 201, optional {@code force}/{@code notify}), {@code people} (historical import, 200) or
     * {@code stakeholders: true} (200). Any other combination is a 400. Organizer-only.
     *
     * <p>Single add: an existing user as a confirmed participant (no self-registration step);
     * 404 unknown user, 409 already registered or event full ({@code details.code =
     * "capacity_exceeded"} unless {@code force=true}). Only this mode sends mail.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AddParticipantsResponse> addParticipants(
            String eventCode,
            AddParticipantsRequest request) {
        Mode mode = mode(request);
        log.debug("POST /events/{}/participants mode={}", eventCode, mode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        return switch (mode) {
            case SINGLE -> {
                // Spec defaults: force absent = false, notify absent = true.
                boolean force = Boolean.TRUE.equals(request.getForce());
                boolean notify = request.getNotify() == null || request.getNotify();
                String addedBy = securityContextHelper.getCurrentUsername();
                Registration registration = registrationService.addParticipant(
                        event, request.getUsername(), force, notify, addedBy);
                yield ResponseEntity.status(HttpStatus.CREATED).body(new AddParticipantsResponse()
                        .registrationCode(registration.getRegistrationCode())
                        .status(registration.getStatus())
                        .attendeeUsername(registration.getAttendeeUsername()));
            }
            case PEOPLE -> ResponseEntity.ok(registrationService.importParticipants(event, request.getPeople()));
            case STAKEHOLDERS -> {
                RegistrationService.EnrollmentSummary result = registrationService.enrollStakeholders(event);
                yield ResponseEntity.ok(new AddParticipantsResponse()
                        .enrolled(result.enrolled())
                        .skipped(result.skipped()));
            }
        };
    }

    /**
     * One attendee's registrations across all events, newest event first (Story 4.10b, moved from
     * {@code EventController}'s {@code GET /events/registrations} with the username in a header).
     * {@code filter} must be exactly {@code {"attendeeUsername": "<username>"}}; anything else 400.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<RegistrationPage> listUserRegistrations(String filter, Integer page, Integer limit) {
        String attendeeUsername = attendeeUsernameFrom(filter);
        PaginationParams params = PaginationUtils.parseParams(page, limit != null ? limit : 25);
        Page<RegistrationResponse> result = registrationService.listForAttendee(
                attendeeUsername, PageRequest.of(params.getPage() - 1, params.getLimit()));

        return ResponseEntity.ok(new RegistrationPage()
                .data(result.getContent())
                .pagination(PaginationMetadata.builder()
                        .page(params.getPage())
                        .limit(params.getLimit())
                        .totalItems(result.getTotalElements())
                        .totalPages(result.getTotalPages())
                        .hasNext(result.hasNext())
                        .hasPrev(result.hasPrevious())
                        .build()));
    }

    /** The one filter {@code GET /registrations} accepts: {@code {"attendeeUsername": "<username>"}}. */
    private static String attendeeUsernameFrom(String filter) {
        FilterCriteria criteria = FilterParser.parse(filter);
        if (criteria == null
                || criteria.getOperator() != FilterOperator.EQUALS
                || !"attendeeUsername".equals(criteria.getField())
                || !(criteria.getValue() instanceof String username)
                || username.isBlank()) {
            throw new ValidationException("filter must be {\"attendeeUsername\": \"<username>\"}");
        }
        return username;
    }

    private enum Mode { SINGLE, PEOPLE, STAKEHOLDERS }

    /** Exactly one mode per request; force and notify belong to the single add. */
    private static Mode mode(AddParticipantsRequest request) {
        boolean single = request.getUsername() != null;
        boolean people = request.getPeople() != null;
        boolean stakeholders = request.getStakeholders() != null;
        int modes = (single ? 1 : 0) + (people ? 1 : 0) + (stakeholders ? 1 : 0);
        if (modes != 1) {
            throw new ValidationException("Give exactly one of username, people or stakeholders");
        }
        if (!single && (request.getForce() != null || request.getNotify() != null)) {
            throw new ValidationException("force and notify belong to the single add (username)");
        }
        if (stakeholders && !request.getStakeholders()) {
            throw new ValidationException("stakeholders must be true");
        }
        return single ? Mode.SINGLE : people ? Mode.PEOPLE : Mode.STAKEHOLDERS;
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
