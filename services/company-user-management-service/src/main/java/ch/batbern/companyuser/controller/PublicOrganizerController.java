package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.PublicOrganizersApi;
import ch.batbern.companyuser.dto.generated.PublicOrganizerResponse;
import ch.batbern.companyuser.service.PublicOrganizerService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public REST Controller for Organizer Information — implements the generated
 * {@link PublicOrganizersApi} (users-api {@code Public Organizers} tag).
 *
 * <p>NO AUTHENTICATION REQUIRED — returns public organizer data for the About page.
 * Only exposes publicly-shareable fields (name, bio, email, profile picture, company);
 * never sensitive fields like cognitoUserId, roles, or preferences.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PublicOrganizerController implements PublicOrganizersApi {

    private final PublicOrganizerService publicOrganizerService;

    @Override
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
