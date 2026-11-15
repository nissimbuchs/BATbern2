package ch.batbern.events.architecture.fixtures.eventcoreinherited;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/** Stands in for the generated {@code RegistrationsApi}: the mapping sits on the interface. */
public interface FixtureRegistrationsApi {

    @RequestMapping(method = RequestMethod.GET, value = "/events/{eventCode}/my-registration")
    String getMyRegistration();
}
