package ch.batbern.events.architecture.fixtures.forwarding;

import ch.batbern.events.architecture.fixtures.mailcenter.FixtureMailCenterService;

/** Part of the mail center module (L6): may use mailcenter, and no class outside may use it. */
public class FixtureForwarder {
    public void forward(FixtureMailCenterService mailCenter) {
        mailCenter.compose();
    }
}
