package ch.batbern.events.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Response wrapper for Watch active events endpoint.
 * W2.3: Event Join & Schedule Sync
 */
@Getter
@AllArgsConstructor
public class ActiveEventsResponse {
    private final List<ActiveEventDetail> activeEvents;
}
