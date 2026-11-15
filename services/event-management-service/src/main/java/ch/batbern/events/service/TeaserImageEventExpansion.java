package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The teaser images every single-event read carries (Story 10.22 AC4), media module. Moved from
 * {@code EventController} unchanged.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class TeaserImageEventExpansion implements EventResponseContributor {

    private final EventTeaserImageService eventTeaserImageService;

    @Override
    public Set<String> includes() {
        return Set.of();
    }

    @Override
    public void enrich(Event event, EventResponse response) {
        if (response.getEventCode() != null) {
            response.setTeaserImages(eventTeaserImageService.listByEventCode(response.getEventCode()));
        }
    }
}
