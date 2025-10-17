package ch.batbern.companyuser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Swiss UID Validation Service
 * Validates Swiss company UIDs (Unternehmens-Identifikationsnummer)
 * AC3: Data Validation - Swiss company UID validation
 * AC12: Swiss UID Validation Endpoint with business registry integration
 */
@Service
@Slf4j
public class SwissUIDValidationService {

    /**
     * Swiss UID format: CHE-###.###.###
     * - CHE prefix (Swiss company identifier)
     * - Three groups of 3 digits separated by dots
     * - Example: CHE-123.456.789
     */
    private static final Pattern UID_PATTERN = Pattern.compile(
            "^CHE-\\d{3}\\.\\d{3}\\.\\d{3}$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Validates a Swiss UID format
     * AC12.1: should_validateUID_when_validSwissUIDProvided
     * AC12.2: should_returnInvalid_when_invalidUIDFormat
     *
     * @param uid Swiss UID to validate
     * @return true if valid format, false otherwise
     */
    public boolean isValidUID(String uid) {
        if (uid == null || uid.isBlank()) {
            log.debug("UID validation failed: null or blank");
            return false;
        }

        // Trim whitespace
        uid = uid.trim();

        // Check format
        if (!UID_PATTERN.matcher(uid).matches()) {
            log.debug("UID validation failed: invalid format - {}", uid);
            return false;
        }

        // Reject all-zero UIDs
        if (uid.toUpperCase().matches("CHE-000\\.000\\.000")) {
            log.debug("UID validation failed: all zeros not allowed");
            return false;
        }

        log.debug("UID validation successful: {}", uid);
        return true;
    }

    /**
     * Validates UID and checks against Swiss Business Registry
     * TODO: AC12.3: Integrate with Swiss Business Registry API
     * Currently validates format only
     *
     * @param uid Swiss UID to validate
     * @return true if valid and exists in registry, false otherwise
     */
    public boolean isValidUIDWithRegistryCheck(String uid) {
        // Format validation first
        if (!isValidUID(uid)) {
            return false;
        }

        // TODO: Implement Swiss Business Registry API integration
        // For now, just return format validation result
        log.info("Business registry check for UID: {} (not yet implemented)", uid);
        return true;
    }
}
