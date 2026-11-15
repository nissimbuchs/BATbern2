package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a session material is not found
 * Story 5.9: Session Materials Upload
 */
public class MaterialNotFoundException extends DomainException {
    public MaterialNotFoundException(String materialId) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "Session material not found with ID: " + materialId);
    }
}
