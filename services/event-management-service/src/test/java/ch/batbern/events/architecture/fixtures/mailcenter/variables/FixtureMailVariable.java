package ch.batbern.events.architecture.fixtures.mailcenter.variables;

import ch.batbern.events.architecture.fixtures.forwarding.FixtureForwarder;
import ch.batbern.events.architecture.fixtures.mailcenter.FixtureMailCenterService;

/** Breaks the mail-core rule: mail core (L1) calling the mail center and forwarding (L6). */
public class FixtureMailVariable {
    public void resolve(FixtureForwarder forwarder, FixtureMailCenterService mailCenter) {
        forwarder.forward(mailCenter);
    }
}
