package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.ProfileUpdateRequest;
import ch.batbern.events.dto.SpeakerProfileDto;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.dto.UserUpdateDto;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for speaker profile management via speaker portal.
 * Story 6.2b: Speaker Profile Update Portal
 *
 * Handles:
 * - AC1: Profile view (combined User + Speaker data)
 * - AC2-6: Profile updates with validation
 * - AC8: Profile completeness calculation
 * - AC10: Cross-service sync to Company Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerProfileService {

    private static final int MAX_BIO_LENGTH = 500;
    private static final int MAX_LIST_ITEMS = 10;
    private static final Pattern LINKEDIN_URL_PATTERN = Pattern.compile(
            "^https?://(www\\.)?linkedin\\.com/.*$",
            Pattern.CASE_INSENSITIVE
    );

    private final SpeakerRepository speakerRepository;
    private final MagicLinkService magicLinkService;
    private final UserApiClient userApiClient;

    /**
     * Get speaker profile by magic link token.
     * Combines User data from Company Service with Speaker data from Event Service.
     *
     * @param token magic link token for authentication
     * @return combined speaker profile
     * @throws InvalidTokenException if token is invalid, expired, or not found
     * @throws SpeakerNotFoundException if speaker doesn't exist
     */
    public SpeakerProfileDto getProfile(String token) {
        log.debug("Getting profile for token");

        // 1. Validate token
        TokenValidationResult tokenResult = magicLinkService.validateToken(token);
        if (!tokenResult.valid()) {
            log.warn("Token validation failed: {}", tokenResult.error());
            throw new InvalidTokenException(tokenResult.error());
        }

        String username = tokenResult.username();
        log.debug("Token valid for username: {}", username);

        // 2. Get Speaker entity
        Speaker speaker = speakerRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Speaker not found for username: {}", username);
                    return new SpeakerNotFoundException(username);
                });

        // 3. Enrich with User data from Company Service
        UserResponse user = userApiClient.getUserByUsername(username);

        // 4. Calculate completeness
        int completeness = calculateCompleteness(user, speaker);
        List<String> missingFields = getMissingFields(user, speaker);

        // 5. Build combined response with session context (AC10)
        return buildProfileDto(user, speaker, completeness, missingFields, tokenResult);
    }

    /**
     * Update speaker profile.
     * Routes updates to appropriate service based on field type.
     *
     * @param token magic link token for authentication
     * @param request profile update request
     * @return updated speaker profile
     * @throws InvalidTokenException if token is invalid
     * @throws SpeakerNotFoundException if speaker doesn't exist
     * @throws ValidationException if validation fails
     */
    @Transactional
    public SpeakerProfileDto updateProfile(String token, ProfileUpdateRequest request) {
        log.debug("Updating profile for token");

        // 1. Validate token
        TokenValidationResult tokenResult = magicLinkService.validateToken(token);
        if (!tokenResult.valid()) {
            log.warn("Token validation failed: {}", tokenResult.error());
            throw new InvalidTokenException(tokenResult.error());
        }

        String username = tokenResult.username();
        log.debug("Token valid for username: {}", username);

        // 2. Get Speaker entity
        Speaker speaker = speakerRepository.findByUsername(username)
                .orElseThrow(() -> new SpeakerNotFoundException(username));

        // 3. Validate request
        validateUpdateRequest(request);

        // 4. Update all fields locally in Speaker entity
        // Speaker portal uses token auth (not JWT), so we store everything locally
        updateSpeakerFields(speaker, request);
        speakerRepository.save(speaker);
        log.debug("Updated speaker profile fields locally for: {}", username);

        // 5. Try to sync User fields to Company Service (best effort)
        // This will fail in speaker portal context (no JWT auth) - that's OK
        if (hasUserFields(request)) {
            UserUpdateDto userUpdate = UserUpdateDto.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .bio(request.getBio())
                    .build();
            try {
                userApiClient.updateUser(username, userUpdate);
                log.debug("Synced user fields to Company Service for: {}", username);
            } catch (UserServiceException e) {
                // Expected in speaker portal context - local storage is authoritative
                log.debug("Could not sync to Company Service (speaker portal context): {}",
                        e.getMessage());
            }
        }

        // 6. Return updated profile
        return getProfile(token);
    }

    /**
     * Calculate profile completeness percentage.
     * Fields contributing to completeness:
     * - firstName (required) - 15%
     * - lastName (required) - 15%
     * - bio - 20%
     * - profilePhoto - 20%
     * - expertiseAreas (at least 1) - 15%
     * - languages (at least 1) - 15%
     *
     * Prefers local Speaker values over User service values.
     */
    private int calculateCompleteness(UserResponse user, Speaker speaker) {
        int score = 0;

        // First name: prefer local Speaker value, fall back to User service
        String firstName = speaker.getFirstName() != null ? speaker.getFirstName() : user.getFirstName();
        if (firstName != null && !firstName.isBlank()) {
            score += 15;
        }

        // Last name: prefer local Speaker value, fall back to User service
        String lastName = speaker.getLastName() != null ? speaker.getLastName() : user.getLastName();
        if (lastName != null && !lastName.isBlank()) {
            score += 15;
        }

        // Bio: prefer local Speaker value, fall back to User service
        String bio = speaker.getBio() != null ? speaker.getBio() : user.getBio();
        if (bio != null && !bio.isBlank()) {
            score += 20;
        }

        // Profile picture: prefer local Speaker value, fall back to User service
        if (speaker.getProfilePictureUrl() != null || user.getProfilePictureUrl() != null) {
            score += 20;
        }

        // Expertise areas
        if (speaker.getExpertiseAreas() != null && !speaker.getExpertiseAreas().isEmpty()) {
            score += 15;
        }

        // Languages
        if (speaker.getLanguages() != null && !speaker.getLanguages().isEmpty()) {
            score += 15;
        }

        return score;
    }

    /**
     * Get list of missing fields for 100% completeness.
     * Prefers local Speaker values over User service values.
     */
    private List<String> getMissingFields(UserResponse user, Speaker speaker) {
        List<String> missing = new ArrayList<>();

        // First name: prefer local Speaker value, fall back to User service
        String firstName = speaker.getFirstName() != null ? speaker.getFirstName() : user.getFirstName();
        if (firstName == null || firstName.isBlank()) {
            missing.add("firstName");
        }

        // Last name: prefer local Speaker value, fall back to User service
        String lastName = speaker.getLastName() != null ? speaker.getLastName() : user.getLastName();
        if (lastName == null || lastName.isBlank()) {
            missing.add("lastName");
        }

        // Bio: prefer local Speaker value, fall back to User service
        String bio = speaker.getBio() != null ? speaker.getBio() : user.getBio();
        if (bio == null || bio.isBlank()) {
            missing.add("bio");
        }

        // Profile picture: prefer local Speaker value, fall back to User service
        if (speaker.getProfilePictureUrl() == null && user.getProfilePictureUrl() == null) {
            missing.add("profilePictureUrl");
        }

        // Expertise areas
        if (speaker.getExpertiseAreas() == null || speaker.getExpertiseAreas().isEmpty()) {
            missing.add("expertiseAreas");
        }

        // Languages
        if (speaker.getLanguages() == null || speaker.getLanguages().isEmpty()) {
            missing.add("languages");
        }

        return missing;
    }

    /**
     * Build combined profile DTO from User and Speaker data.
     * Prefers local Speaker values over User service values.
     */
    private SpeakerProfileDto buildProfileDto(
            UserResponse user,
            Speaker speaker,
            int completeness,
            List<String> missingFields,
            TokenValidationResult tokenResult) {

        // Prefer local Speaker values, fall back to User service values
        String firstName = speaker.getFirstName() != null
                ? speaker.getFirstName() : user.getFirstName();
        String lastName = speaker.getLastName() != null
                ? speaker.getLastName() : user.getLastName();
        String bio = speaker.getBio() != null
                ? speaker.getBio() : user.getBio();
        String email = speaker.getEmail() != null
                ? speaker.getEmail() : user.getEmail();
        String profilePictureUrl = speaker.getProfilePictureUrl() != null
                ? speaker.getProfilePictureUrl()
                : (user.getProfilePictureUrl() != null ? user.getProfilePictureUrl().toString() : null);

        // AC10: Session context for navigation
        boolean hasSession = tokenResult.sessionTitle() != null
                && !tokenResult.sessionTitle().isBlank();

        return SpeakerProfileDto.builder()
                .username(user.getId())  // 'id' contains username per Story 1.16.2
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .bio(bio)
                .profilePictureUrl(profilePictureUrl)
                // Speaker-specific fields (always from Speaker entity)
                .expertiseAreas(speaker.getExpertiseAreas())
                .speakingTopics(speaker.getSpeakingTopics())
                .linkedInUrl(speaker.getLinkedInUrl())
                .languages(speaker.getLanguages())
                // Computed
                .profileCompleteness(completeness)
                .missingFields(missingFields)
                // Navigation context (AC10)
                .hasSessionAssigned(hasSession)
                .sessionTitle(tokenResult.sessionTitle())
                .eventCode(tokenResult.eventCode())
                .build();
    }

    /**
     * Validate update request.
     */
    private void validateUpdateRequest(ProfileUpdateRequest request) {
        // Validate bio length
        if (request.getBio() != null && request.getBio().length() > MAX_BIO_LENGTH) {
            throw new ValidationException(
                    "bio must not exceed " + MAX_BIO_LENGTH + " characters");
        }

        // Validate expertise areas count
        if (request.getExpertiseAreas() != null && request.getExpertiseAreas().size() > MAX_LIST_ITEMS) {
            throw new ValidationException(
                    "expertiseAreas must not exceed " + MAX_LIST_ITEMS + " items");
        }

        // Validate speaking topics count
        if (request.getSpeakingTopics() != null && request.getSpeakingTopics().size() > MAX_LIST_ITEMS) {
            throw new ValidationException(
                    "speakingTopics must not exceed " + MAX_LIST_ITEMS + " items");
        }

        // Validate LinkedIn URL
        if (request.getLinkedInUrl() != null && !request.getLinkedInUrl().isEmpty()) {
            if (!LINKEDIN_URL_PATTERN.matcher(request.getLinkedInUrl()).matches()) {
                throw new ValidationException(
                        "linkedInUrl must be a valid LinkedIn URL");
            }
        }
    }

    /**
     * Check if request has User fields to update.
     */
    private boolean hasUserFields(ProfileUpdateRequest request) {
        return request.getFirstName() != null
                || request.getLastName() != null
                || request.getBio() != null;
    }

    /**
     * Check if request has Speaker fields to update.
     */
    private boolean hasSpeakerFields(ProfileUpdateRequest request) {
        return request.getExpertiseAreas() != null
                || request.getSpeakingTopics() != null
                || request.getLinkedInUrl() != null
                || request.getLanguages() != null;
    }

    /**
     * Update Speaker entity fields from request.
     * Stores all profile fields locally (both "user" and "speaker" fields).
     */
    private void updateSpeakerFields(Speaker speaker, ProfileUpdateRequest request) {
        // User-type fields (stored locally for speaker portal)
        if (request.getFirstName() != null) {
            speaker.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            speaker.setLastName(request.getLastName());
        }
        if (request.getBio() != null) {
            speaker.setBio(request.getBio());
        }
        // Speaker-specific fields
        if (request.getExpertiseAreas() != null) {
            speaker.setExpertiseAreas(request.getExpertiseAreas());
        }
        if (request.getSpeakingTopics() != null) {
            speaker.setSpeakingTopics(request.getSpeakingTopics());
        }
        if (request.getLinkedInUrl() != null) {
            speaker.setLinkedInUrl(request.getLinkedInUrl());
        }
        if (request.getLanguages() != null) {
            speaker.setLanguages(request.getLanguages());
        }
    }
}
