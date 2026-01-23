package ch.batbern.events.controller;

import ch.batbern.events.dto.ProfileUpdateRequest;
import ch.batbern.events.dto.SpeakerProfileDto;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.service.SpeakerProfileService;
import ch.batbern.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker portal profile management.
 * Story 6.2b: Speaker Profile Update Portal
 *
 * Provides profile viewing and updating endpoints for the speaker portal.
 * This is a PUBLIC endpoint - no authentication required.
 * The magic link token IS the authentication mechanism.
 *
 * Endpoints:
 * - GET /api/v1/speaker-portal/profile?token=xxx - Get speaker profile
 * - PATCH /api/v1/speaker-portal/profile - Update speaker profile
 *
 * Security:
 * - Token validated on each request
 * - Failed attempts logged with IP for audit
 * - Token never logged (security requirement)
 */
@RestController
@RequestMapping("/api/v1/speaker-portal")
public class SpeakerPortalProfileController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerPortalProfileController.class);

    private final SpeakerProfileService speakerProfileService;

    public SpeakerPortalProfileController(SpeakerProfileService speakerProfileService) {
        this.speakerProfileService = speakerProfileService;
    }

    /**
     * Get speaker profile.
     * AC1: Profile view on page load
     *
     * @param token magic link token (query parameter)
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with combined profile if successful, error status otherwise
     */
    @GetMapping("/profile")
    public ResponseEntity<SpeakerProfileDto> getProfile(
            @RequestParam(required = false) String token,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (token == null || token.isBlank()) {
            LOG.warn("Profile view failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.info("Profile view request received from IP: {}", clientIp);

        try {
            SpeakerProfileDto profile = speakerProfileService.getProfile(token);

            LOG.info("Profile view successful for user: {} from IP: {}",
                    profile.getUsername(), clientIp);

            return ResponseEntity.ok(profile);

        } catch (InvalidTokenException e) {
            LOG.warn("Profile view failed - invalid token: {} from IP: {}",
                    e.getErrorCode(), clientIp);
            throw e;

        } catch (SpeakerNotFoundException e) {
            LOG.warn("Profile view failed - speaker not found from IP: {}", clientIp);
            throw e;
        }
    }

    /**
     * Update speaker profile.
     * AC2-AC10: Profile updates with validation and cross-service sync
     *
     * @param request the profile update request containing token and updates
     * @param httpRequest the HTTP request (for IP logging)
     * @return 200 with updated profile if successful, error status otherwise
     */
    @PatchMapping("/profile")
    public ResponseEntity<SpeakerProfileDto> updateProfile(
            @Valid @RequestBody ProfileUpdateRequestWithToken request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);

        // Validate token presence
        if (request.getToken() == null || request.getToken().isBlank()) {
            LOG.warn("Profile update failed - missing token from IP: {}", clientIp);
            throw new ValidationException("Token is required");
        }

        LOG.info("Profile update request received from IP: {}", clientIp);

        try {
            // Convert to service request (without token)
            ProfileUpdateRequest updateRequest = ProfileUpdateRequest.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .bio(request.getBio())
                    .expertiseAreas(request.getExpertiseAreas())
                    .speakingTopics(request.getSpeakingTopics())
                    .linkedInUrl(request.getLinkedInUrl())
                    .languages(request.getLanguages())
                    .build();

            SpeakerProfileDto profile = speakerProfileService.updateProfile(
                    request.getToken(), updateRequest);

            LOG.info("Profile update successful for user: {} from IP: {}",
                    profile.getUsername(), clientIp);

            return ResponseEntity.ok(profile);

        } catch (InvalidTokenException e) {
            LOG.warn("Profile update failed - invalid token: {} from IP: {}",
                    e.getErrorCode(), clientIp);
            throw e;

        } catch (SpeakerNotFoundException e) {
            LOG.warn("Profile update failed - speaker not found from IP: {}", clientIp);
            throw e;

        } catch (ValidationException e) {
            LOG.warn("Profile update failed - validation error: {} from IP: {}",
                    e.getMessage(), clientIp);
            throw e;
        }
    }

    /**
     * Extract client IP address from request.
     * Handles X-Forwarded-For header for requests through load balancers/proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Request DTO for profile update that includes the token.
     * The token is passed in the request body for PATCH requests.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProfileUpdateRequestWithToken {
        private String token;
        private String firstName;
        private String lastName;
        private String bio;
        private java.util.List<String> expertiseAreas;
        private java.util.List<String> speakingTopics;
        private String linkedInUrl;
        private java.util.List<String> languages;
    }
}
