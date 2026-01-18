package ch.batbern.events.domain;

/**
 * Speaker's travel/accommodation requirements - Story 6.2.
 */
public enum TravelRequirement {
    LOCAL,          // No accommodation needed
    ACCOMMODATION,  // Needs hotel/lodging
    VIRTUAL         // Remote participation
}
