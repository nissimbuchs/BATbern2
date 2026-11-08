package ch.batbern.events.architecture.fixtures.registrations;

import ch.batbern.events.architecture.fixtures.forwarding.FixtureForwarder;
import ch.batbern.events.architecture.fixtures.mailcenter.FixtureMailCenterService;
import ch.batbern.events.architecture.fixtures.mailcenter.render.FixtureMailPipeline;
import ch.batbern.events.architecture.fixtures.usermerge.FixtureUserMergeService;

/**
 * Breaks two L6 walls: an L3 module calling mail center (mailcenter and forwarding) and usermerge.
 * Its use of the mail core ({@code mailcenter.render}) is allowed.
 */
public class FixtureRegistrationService {
    public void register(FixtureMailCenterService mailCenter, FixtureUserMergeService merge) {
        mailCenter.compose();
        merge.merge();
    }

    public void forward(FixtureForwarder forwarder, FixtureMailCenterService mailCenter) {
        forwarder.forward(mailCenter);
    }

    public void confirm(FixtureMailPipeline pipeline) {
        pipeline.send();
    }
}
