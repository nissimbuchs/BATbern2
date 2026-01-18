package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a session material is not found
 * Story 5.9: Session Materials Upload
 */
public class MaterialNotFoundException extends NotFoundException {
    public MaterialNotFoundException(String materialId) {
        super("Session material not found with ID: " + materialId);
    }
}
