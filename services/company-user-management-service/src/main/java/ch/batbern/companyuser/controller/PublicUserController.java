package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.PublicUserResponse;
import ch.batbern.companyuser.service.PublicUserService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Public REST controller for anonymous user lookup by username.
 * Story 11.C.1 (AC7): mirror of {@link PublicOrganizerController} for any user.
 *
 * NO AUTHENTICATION REQUIRED — returns the narrow public projection only
 * (username, firstName, lastName, profilePictureUrl). Consumed by the public
 * archive page's useUserPortrait hook in the web frontend, which used to call
 * GET /api/v1/speakers/{username} on the deleted SpeakerController.
 */
@RestController
@RequestMapping("/api/v1/public/users")
@RequiredArgsConstructor
@Slf4j
public class PublicUserController {

    private final PublicUserService publicUserService;

    /**
     * Get a single user's public projection by username.
     * GET /api/v1/public/users/{username}
     *
     * @param username public identifier (ADR-003)
     * @return 200 with the narrow public projection, 404 if user does not exist
     */
    @GetMapping("/{username}")
    @Timed(value = "public.users.getByUsername",
            description = "Time to look up a public user by username",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PublicUserResponse> getByUsername(@PathVariable String username) {
        PublicUserResponse body = publicUserService.getByUsername(username);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                .body(body);
    }
}
