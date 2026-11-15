package ch.batbern.events.service;

import ch.batbern.events.domain.Event;

/**
 * What the modules above event core do when an event is written (event core SPI), implemented in
 * their own module so {@link EventWriteService} depends on none of them (module map: a lower module
 * defines the interface, a higher one implements it).
 */
public interface EventWriteHooks {

    /**
     * The registration capacity was saved (PUT: changed; PATCH: given). Runs in the transaction of the
     * save: a failure rolls the capacity change back.
     */
    default void capacitySaved(Event event) {
    }

    /**
     * The event is about to be deleted, in the delete's transaction. Throw to refuse the delete (an
     * event with real attendees answers 409).
     */
    default void beforeDelete(Event event) {
    }
}
