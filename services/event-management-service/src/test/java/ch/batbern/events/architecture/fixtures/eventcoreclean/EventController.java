package ch.batbern.events.architecture.fixtures.eventcoreclean;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Keeps the endpoint placement rule: event paths only (a registration count in a name is no path). */
@RequestMapping("/api/v1")
public class EventController {

    @GetMapping(path = "/events/{eventCode}/sessions")
    public String listSessions() {
        return "sessions";
    }

    @GetMapping("/events/{eventCode}")
    public String getEventWithRegistrationCounts() {
        return "event";
    }
}
