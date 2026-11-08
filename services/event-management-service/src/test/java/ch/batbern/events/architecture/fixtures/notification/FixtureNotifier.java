package ch.batbern.events.architecture.fixtures.notification;

import ch.batbern.events.architecture.fixtures.watch.FixtureWatchService;

/** Breaks the notification rule (and the watch wall): notification calling the watch module. */
public class FixtureNotifier {
    public void notifyWatch(FixtureWatchService watch) {
        watch.push();
    }
}
