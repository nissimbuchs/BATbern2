package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.PublicUserResponse;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for anonymous (unauthenticated) lookup of public user data by username.
 * Story 11.C.1 (AC7): replaces the deleted GET /api/v1/speakers/{username} surface.
 *
 * Returns only the narrow public projection (username, firstName, lastName,
 * profilePictureUrl) — no email, no role list, no other PII.
 *
 * Row scope: SPEAKER role only. Non-speakers (attendees, partners, organizers)
 * are reported as 404 to match the row scope of the deleted SpeakerController
 * and prevent mass-enumeration of attendee identities. Organizer portraits are
 * served by the separate PublicOrganizerController.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicUserService {

    private final UserRepository userRepository;

    /**
     * Look up the public projection of a user by username.
     *
     * @param username public identifier (ADR-003)
     * @return narrow public projection
     * @throws UserNotFoundException when no user exists with the given username
     */
    @Transactional(readOnly = true)
    public PublicUserResponse getByUsername(String username) {
        log.debug("Public user lookup for username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (!user.hasRole(Role.SPEAKER)) {
            throw new UserNotFoundException(username);
        }

        return PublicUserResponse.builder()
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .build();
    }
}
