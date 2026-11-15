package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.core.api.generated.EventsApi;
import ch.batbern.events.core.dto.generated.CreateEventRequest;
import ch.batbern.events.core.dto.generated.Event;
import ch.batbern.events.core.dto.generated.EventDetail;
import ch.batbern.events.core.dto.generated.ListEvents200Response;
import ch.batbern.events.core.dto.generated.PatchEventRequest;
import ch.batbern.events.core.dto.generated.UpdateEventRequest;
import ch.batbern.events.service.EventReadService;
import ch.batbern.events.service.EventWriteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The event resource ({@code EventsApi}, Story 1.15a.1): list with filter, sort, paging and
 * {@code include=} expansion, detail (cached), current event, create, update, patch, delete.
 *
 * <p>HTTP only: the reads are {@link EventReadService}'s, the writes {@link EventWriteService}'s, both
 * event core. The cache evictions stay here so they run after the write committed. The public read
 * model is {@code PublicEventController} (publishing), the analytics {@code EventAnalyticsController},
 * the topic selection {@code EventTopicController}. {@code EmsEndpointPlacementTest} holds that this
 * class uses event core and platform only.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event management API - consolidated endpoints")
public class EventController implements EventsApi {

    private final EventReadService eventReadService;
    private final EventWriteService eventWriteService;

    /** GET /events?filter={}&sort={}&page={}&limit={}&includeArchived={}&include={} */
    @Override
    public ResponseEntity<ListEvents200Response> listEvents(String filter, String sort, Integer page, Integer limit,
                                                            Boolean includeArchived, String include) {
        return ResponseEntity.ok(eventReadService.list(filter, sort, page, limit, includeArchived, include));
    }

    /** GET /events/{eventCode}?include=...; {@code X-Cache-Status} tells whether the cache answered. */
    @Override
    public ResponseEntity<EventDetail> getEvent(String eventCode, String include) {
        EventReadService.Detail detail = eventReadService.detail(eventCode, include);
        return ResponseEntity.ok().header("X-Cache-Status", detail.cacheStatus()).body(detail.body());
    }

    /** GET /events/current (Apple Watch, #1070): 404 when no event qualifies. */
    @Override
    public ResponseEntity<EventDetail> getCurrentEvent(String include) {
        return eventReadService.current(include)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Event> createEvent(CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventWriteService.create(request));
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true),
        @CacheEvict(value = CacheConfig.ARCHIVE_EVENTS_CACHE, allEntries = true)
    })
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Event> updateEvent(String eventCode, UpdateEventRequest request) {
        return ResponseEntity.ok(eventWriteService.update(eventCode, request));
    }

    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Event> patchEvent(String eventCode, PatchEventRequest request) {
        return ResponseEntity.ok(eventWriteService.patch(eventCode, request));
    }

    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteEvent(String eventCode) {
        eventWriteService.delete(eventCode);
        return ResponseEntity.noContent().build();
    }
}
