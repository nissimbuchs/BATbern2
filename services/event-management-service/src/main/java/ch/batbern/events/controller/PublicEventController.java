package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.PublicEventsApi;
import ch.batbern.events.core.dto.generated.EventDetail;
import ch.batbern.events.core.dto.generated.ListEvents200Response;
import ch.batbern.events.service.publishing.PublicEventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public events read model ({@code PublicEventsApi}: {@code /public/events}, {@code /current},
 * {@code /{eventCode}}), publishing: what the website shows, shaped by publishing state, the same for
 * every caller. Moved out of {@code EventController}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicEventController implements PublicEventsApi {

    private final PublicEventsService publicEventsService;

    @Override
    public ResponseEntity<EventDetail> getPublicCurrentEvent() {
        return publicEventsService.current()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<EventDetail> getPublicEvent(String eventCode) {
        return ResponseEntity.ok(publicEventsService.detail(eventCode));
    }

    @Override
    public ResponseEntity<ListEvents200Response> listPublicEvents(String scope, String search, String topicCodes,
                                                                  String sort, Integer page, Integer limit) {
        return ResponseEntity.ok(publicEventsService.list(scope, search, topicCodes, sort, page, limit));
    }
}
