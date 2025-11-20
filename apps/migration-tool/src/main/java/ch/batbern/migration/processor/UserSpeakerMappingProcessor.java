package ch.batbern.migration.processor;

import ch.batbern.migration.model.legacy.LegacySpeaker;
import ch.batbern.migration.model.target.SpeakerDto;
import ch.batbern.migration.model.target.UserDto;
import ch.batbern.migration.service.EntityIdMappingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps legacy speaker data to User + Speaker DTOs
 * Implements AC9: Username generation, name parsing, company validation
 * Implements ADR-004: Bio in User, NOT in Speaker
 */
@Slf4j
@Component
public class UserSpeakerMappingProcessor implements ItemProcessor<LegacySpeaker, UserSpeakerMappingProcessor.UserSpeakerPair> {

    @Autowired
    private EntityIdMappingService idMappingService;

    // Pattern to parse "FirstName LastName, Company" format
    private static final Pattern NAME_PATTERN = Pattern.compile("([^,]+),\\s*(.*)");

    @Override
    public UserSpeakerPair process(LegacySpeaker legacy) throws Exception {
        log.debug("Processing speaker: {}", legacy.getName());

        // Parse name: "Thomas Goetz, Die Mobiliar" → firstName="Thomas", lastName="Goetz"
        NameParts nameParts = parseName(legacy.getName());

        // Generate username: "thomas.goetz"
        String username = generateUsername(nameParts.firstName, nameParts.lastName);

        // Validate company exists
        UUID companyId = validateCompany(legacy.getCompany());

        // Create User DTO (ADR-004: bio goes here)
        UserDto user = new UserDto();
        user.setUsername(username);
        user.setFirstName(nameParts.firstName);
        user.setLastName(nameParts.lastName);
        user.setBio(legacy.getBio());
        user.setCompanyId(companyId);
        user.setRole("SPEAKER");

        // Profile picture S3 key (will be uploaded by FileWriter later)
        if (legacy.getPortrait() != null && !legacy.getPortrait().isEmpty()) {
            String s3Key = "portraits/" + legacy.getPortrait();
            user.setProfilePictureS3Key(s3Key);
        }

        // Create Speaker DTO (ADR-004: NO bio)
        SpeakerDto speaker = new SpeakerDto();
        // userId will be set by writer after User creation
        speaker.setIsActive(true);

        log.info("Mapped speaker: {} → username={}, companyId={}",
            legacy.getName(), username, companyId);

        return new UserSpeakerPair(user, speaker, username);
    }

    /**
     * Parse name from "FirstName LastName, Company" format
     */
    private NameParts parseName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Speaker name cannot be null or empty");
        }

        Matcher matcher = NAME_PATTERN.matcher(fullName.trim());
        if (matcher.matches()) {
            String namePart = matcher.group(1).trim();
            String[] parts = namePart.split("\\s+");

            if (parts.length >= 2) {
                String firstName = parts[0];
                String lastName = parts[parts.length - 1];

                // Handle middle names if present
                if (parts.length > 2) {
                    // Take first and last, ignore middle
                    return new NameParts(firstName, lastName);
                }

                return new NameParts(firstName, lastName);
            } else if (parts.length == 1) {
                // Single name - use as both first and last
                return new NameParts(parts[0], parts[0]);
            }
        }

        throw new IllegalArgumentException("Unable to parse name: " + fullName);
    }

    /**
     * Generate username from first and last name
     * Format: "firstname.lastname" (lowercase, no special chars)
     * Handles German umlauts: ä→ae, ö→oe, ü→ue, ß→ss
     */
    private String generateUsername(String firstName, String lastName) {
        String username = firstName.toLowerCase() + "." + lastName.toLowerCase();

        // Normalize Unicode characters (ä → ae, ö → oe, etc.)
        username = normalizeGermanChars(username);

        // Remove any remaining non-alphanumeric characters except dots
        username = username.replaceAll("[^a-z0-9.]", "");

        log.debug("Generated username: {} from {}.{}", username, firstName, lastName);
        return username;
    }

    /**
     * Normalize German characters: ä→ae, ö→oe, ü→ue, ß→ss
     */
    private String normalizeGermanChars(String input) {
        String normalized = input
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace("Ä", "ae")
            .replace("Ö", "oe")
            .replace("Ü", "ue");

        // Remove other accents/diacritics
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");

        return normalized;
    }

    /**
     * Validate company exists in entity_id_mapping
     * Returns company UUID or null if not found (will be logged as warning)
     */
    private UUID validateCompany(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            log.warn("No company specified for speaker");
            return null;
        }

        try {
            UUID companyId = idMappingService.getNewId("Company", companyName.toLowerCase().trim());
            log.debug("Validated company: {} → UUID: {}", companyName, companyId);
            return companyId;
        } catch (IllegalStateException e) {
            log.warn("Company not found in entity_id_mapping: {} (speaker will have null companyId)", companyName);
            return null;
        }
    }

    /**
     * Wrapper for name parts
     */
    @Data
    private static class NameParts {
        private final String firstName;
        private final String lastName;
    }

    /**
     * Wrapper for User + Speaker pair
     * Username stored for entity ID mapping
     */
    @Data
    public static class UserSpeakerPair {
        private final UserDto user;
        private final SpeakerDto speaker;
        private final String username;  // For entity ID mapping
    }
}
