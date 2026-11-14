package ch.batbern.events.architecture.fixtures.eventcoreinherited;

import org.springframework.web.bind.annotation.RequestMapping;

/** Breaks the endpoint placement rule through an implemented interface; no mapping of its own. */
@RequestMapping("/api/v1")
public class EventController implements FixtureRegistrationsApi {

    @Override
    public String getMyRegistration() {
        return "mine";
    }
}
