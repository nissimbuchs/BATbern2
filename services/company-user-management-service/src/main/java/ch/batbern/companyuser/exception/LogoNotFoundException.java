package ch.batbern.companyuser.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a logo is not found by upload ID
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Extends shared-kernel NotFoundException for consistent error handling
 */
public class LogoNotFoundException extends NotFoundException {

    public LogoNotFoundException(String uploadId) {
        super("Logo", uploadId);
    }
}
