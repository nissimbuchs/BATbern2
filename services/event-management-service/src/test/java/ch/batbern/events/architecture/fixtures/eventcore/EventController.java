package ch.batbern.events.architecture.fixtures.eventcore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Breaks the endpoint placement rule by hand: maps a registration path itself. Not a
 * {@code @RestController}: the EMS component scan reaches test packages.
 */
@RequestMapping("/api/v1")
public class EventController {

    @GetMapping("/events/{eventCode}")
    public String getEvent() {
        return "event";
    }

    @PostMapping("/events/{eventCode}/registrations")
    public String createRegistration() {
        return "registration";
    }
}
