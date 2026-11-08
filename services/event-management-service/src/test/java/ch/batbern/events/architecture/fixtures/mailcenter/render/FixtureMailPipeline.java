package ch.batbern.events.architecture.fixtures.mailcenter.render;

import ch.batbern.events.architecture.fixtures.mailcenter.variables.FixtureMailVariable;

/**
 * Stands for the mail core that still lives in {@code mailcenter.render} (L1, usable by all). It uses
 * other mail core ({@link FixtureMailVariable}), which the mail-core rule allows.
 */
public class FixtureMailPipeline {
    public void send() {
    }

    public void resolve(FixtureMailVariable variable) {
        variable.resolve(null, null);
    }
}
