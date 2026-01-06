package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.PublicOrganizerResponse;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for retrieving public organizer information
 * Used by public About page - no authentication required
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicOrganizerService {

    private final UserRepository userRepository;

    /**
     * Get all users with ORGANIZER role (public information only)
     *
     * @return List of organizers with only public fields
     */
    @Transactional(readOnly = true)
    public List<PublicOrganizerResponse> getAllOrganizers() {
        log.debug("Fetching all organizers from database");

        List<User> organizers = userRepository.findByRolesContaining(Role.ORGANIZER);

        log.info("Found {} organizers", organizers.size());

        return organizers.stream()
                .map(this::mapToPublicResponse)
                .collect(Collectors.toList());
    }

    /**
     * Map User entity to public organizer response
     * Only includes publicly shareable fields
     */
    private PublicOrganizerResponse mapToPublicResponse(User user) {
        return PublicOrganizerResponse.builder()
                .id(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .bio(user.getBio())
                .profilePictureUrl(user.getProfilePictureUrl())
                .companyId(user.getCompanyId())
                .build();
    }
}
