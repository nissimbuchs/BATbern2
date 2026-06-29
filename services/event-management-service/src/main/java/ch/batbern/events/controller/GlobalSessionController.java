package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.SessionUserService;
import ch.batbern.events.sessions.api.generated.CompanySessionsApi;
import ch.batbern.events.sessions.dto.generated.CompanySessionResponse;
import ch.batbern.events.sessions.dto.generated.SearchSessionsByCompany200Response;
import ch.batbern.events.sessions.dto.generated.SessionSpeaker;
import ch.batbern.shared.api.PaginationMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Top-level sessions search across all events — implements the generated {@link CompanySessionsApi}.
 *
 * <p>GET /api/v1/sessions?companyName={name}&amp;page={n}&amp;limit={n}
 *
 * <p>Returns sessions where at least one speaker belongs to the given company, enriched with event
 * metadata and the full speaker list per session. Consumed by the organizer Company-detail
 * Sessions tab (web-frontend {@code CompanyDetailView}).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class GlobalSessionController implements CompanySessionsApi {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionUserService sessionUserService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SearchSessionsByCompany200Response> searchSessionsByCompany(
            String companyName,
            Integer page,
            Integer limit) {

        log.debug("GET /api/v1/sessions - companyName={}, page={}, limit={}", companyName, page, limit);

        if (companyName == null || companyName.isBlank()) {
            PaginationMetadata emptyPagination = PaginationMetadata.builder()
                    .page(page).limit(limit).totalItems(0).totalPages(0)
                    .hasNext(false).hasPrev(false).build();
            return ResponseEntity.ok(new SearchSessionsByCompany200Response()
                    .data(List.of())
                    .pagination(emptyPagination));
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
                    List<SessionSpeaker> speakers =
                            sessionUserService.getSessionSpeakers(session.getId());
                    return new CompanySessionResponse()
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
                            .speakers(speakers);
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

        return ResponseEntity.ok(new SearchSessionsByCompany200Response()
                .data(responses)
                .pagination(pagination));
    }
}
