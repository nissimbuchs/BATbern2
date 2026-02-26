package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.CompanySessionResponse;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.SessionUserService;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.dto.PaginatedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Top-level sessions search across all events.
 *
 * GET /api/v1/sessions?companyName={name}&page={n}&limit={n}
 *
 * Returns sessions where at least one speaker belongs to the given company,
 * enriched with event metadata and full speaker list per session.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
public class GlobalSessionController {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionUserService sessionUserService;

    /**
     * List sessions filtered by company name.
     *
     * Returns sessions where at least one speaker (session_user) belongs to the company
     * (matched via user_profiles.company_id). Each result includes all speakers so the
     * frontend can highlight the company's speakers.
     *
     * @param companyName Company name (ADR-003 meaningful ID)
     * @param page        1-based page number
     * @param limit       Page size (default 50)
     * @return Paginated list of CompanySessionResponse
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PaginatedResponse<CompanySessionResponse>> searchSessions(
            @RequestParam(required = false) String companyName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {

        log.debug("GET /api/v1/sessions - companyName={}, page={}, limit={}", companyName, page, limit);

        if (companyName == null || companyName.isBlank()) {
            PaginationMetadata emptyPagination = PaginationMetadata.builder()
                    .page(page).limit(limit).totalItems(0).totalPages(0)
                    .hasNext(false).hasPrev(false).build();
            return ResponseEntity.ok(PaginatedResponse.<CompanySessionResponse>builder()
                    .data(List.of())
                    .pagination(emptyPagination)
                    .build());
        }

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.unsorted());
        Page<Session> sessionsPage = sessionRepository.findSessionsByCompanyName(companyName, pageable);

        // Batch-load events to avoid N+1 queries
        Set<UUID> eventIds = sessionsPage.getContent().stream()
                .map(Session::getEventId)
                .collect(Collectors.toSet());
        Map<UUID, Event> eventsById = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        List<CompanySessionResponse> responses = sessionsPage.getContent().stream()
                .map(session -> {
                    Event event = eventsById.get(session.getEventId());
                    List<SessionSpeakerResponse> speakers =
                            sessionUserService.getSessionSpeakers(session.getId());
                    return CompanySessionResponse.builder()
                            .sessionSlug(session.getSessionSlug())
                            .eventCode(session.getEventCode())
                            .eventTitle(event != null ? event.getTitle() : null)
                            .eventDate(event != null ? event.getDate().toString() : null)
                            .title(session.getTitle())
                            .sessionType(session.getSessionType())
                            .startTime(session.getStartTime() != null
                                    ? session.getStartTime().toString() : null)
                            .endTime(session.getEndTime() != null
                                    ? session.getEndTime().toString() : null)
                            .room(session.getRoom())
                            .speakers(speakers)
                            .build();
                })
                .collect(Collectors.toList());

        PaginationMetadata pagination = PaginationMetadata.builder()
                .page(page)
                .limit(limit)
                .totalItems(sessionsPage.getTotalElements())
                .totalPages(sessionsPage.getTotalPages())
                .hasNext(sessionsPage.hasNext())
                .hasPrev(sessionsPage.hasPrevious())
                .build();

        return ResponseEntity.ok(PaginatedResponse.<CompanySessionResponse>builder()
                .data(responses)
                .pagination(pagination)
                .build());
    }
}
