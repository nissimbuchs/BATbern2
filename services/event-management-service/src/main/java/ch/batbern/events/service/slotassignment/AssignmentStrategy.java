package ch.batbern.events.service.slotassignment;

/**
 * Strategies for auto-assigning speakers to slots
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
public enum AssignmentStrategy {
    PREFERENCE_OPTIMIZED,  // Maximize match with speaker time preferences
    EXPERTISE_OPTIMIZED,   // Optimize based on topic expertise
    BALANCED               // Balance preferences, expertise, and room requirements
}
