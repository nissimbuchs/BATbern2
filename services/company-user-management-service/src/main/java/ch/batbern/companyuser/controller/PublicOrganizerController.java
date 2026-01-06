package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.PublicOrganizerResponse;
import ch.batbern.companyuser.service.PublicOrganizerService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public REST Controller for Organizer Information
 * NO AUTHENTICATION REQUIRED - Returns public organizer data for About page
 *
 * Security: Only exposes public information (name, bio, email, profile picture)
 * Does not expose sensitive fields like cognitoUserId, roles, preferences, etc.
 */
@RestController
@RequestMapping("/api/v1/public/organizers")
@RequiredArgsConstructor
@Slf4j
public class PublicOrganizerController {

    private final PublicOrganizerService publicOrganizerService;

    /**
     * Get all organizers (public information only)
     * GET /api/v1/public/organizers
     *
     * No authentication required - this is a public endpoint
     *
     * @return List of organizers with public information
     */
    @GetMapping
    @Timed(value = "public.organizers.getAll",
            description = "Time to get all public organizers",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<List<PublicOrganizerResponse>> getAllOrganizers() {
        log.debug("Fetching all public organizers");

        List<PublicOrganizerResponse> organizers = publicOrganizerService.getAllOrganizers();

        log.info("Returning {} public organizers", organizers.size());
        return ResponseEntity.ok(organizers);
    }
}
