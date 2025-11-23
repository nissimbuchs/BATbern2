package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
// TODO: Create UserRoleChangedEvent in future story (Task 6 deferred)
// import ch.batbern.companyuser.events.UserRoleChangedEvent;
import ch.batbern.companyuser.exception.MinimumOrganizersException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import io.micrometer.core.annotation.Counted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for managing user roles with business rules enforcement
 *
 * Story 1.14-2 Task 10: Role Service & Business Rules
 * Business Rules:
 * - Minimum 2 organizers must exist at all times
 * - Cannot remove ORGANIZER role if it would violate this rule
 * - Publishes UserRoleChangedEvent on role changes
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private static final int MINIMUM_ORGANIZERS = 2;

    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Get all roles for a user
     *
     * @param username User username (Story 1.16.2: meaningful ID)
     * @return Set of roles
     * @throws UserNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public Set<Role> getUserRoles(String username) {
        log.debug("Getting roles for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        return new HashSet<>(user.getRoles());
    }

    /**
     * Add a role to a user
     *
     * @param username User username (Story 1.16.2: meaningful ID)
     * @param role Role to add
     * @return Updated set of roles
     * @throws UserNotFoundException if user not found
     */
    @Counted(value = "users.roleChanges", description = "Count of role changes (add/remove)")
    public Set<Role> addRole(String username, Role role) {
        log.info("Adding role {} to user: {}", role, username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        // Check if role already exists (avoid duplicate event)
        boolean roleAdded = user.getRoles().add(role);

        user = userRepository.save(user);

        // Only publish event if role was actually added
        if (roleAdded) {
            publishRoleChangedEvent(user, "Role added: " + role);
            log.info("Role {} added to user: {}", role, username);
        } else {
            log.debug("Role {} already exists for user: {}", role, username);
        }

        return new HashSet<>(user.getRoles());
    }

    /**
     * Remove a role from a user
     * Enforces business rule: minimum 2 organizers must exist
     *
     * @param username User username (Story 1.16.2: meaningful ID)
     * @param role Role to remove
     * @return Updated set of roles
     * @throws UserNotFoundException if user not found
     * @throws MinimumOrganizersException if removing ORGANIZER would violate business rule
     */
    @Counted(value = "users.roleChanges", description = "Count of role changes (add/remove)")
    public Set<Role> removeRole(String username, Role role) {
        log.info("Removing role {} from user: {}", role, username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        // Business rule: Enforce minimum 2 organizers
        if (role == Role.ORGANIZER && user.hasRole(Role.ORGANIZER)) {
            enforceMinimumOrganizers(user);
        }

        // Check if role was actually removed (avoid event if role didn't exist)
        boolean roleRemoved = user.getRoles().remove(role);

        user = userRepository.save(user);

        // Only publish event if role was actually removed
        if (roleRemoved) {
            publishRoleChangedEvent(user, "Role removed: " + role);
            log.info("Role {} removed from user: {}", role, username);
        } else {
            log.debug("Role {} was not present for user: {}", role, username);
        }

        return new HashSet<>(user.getRoles());
    }

    /**
     * Set roles for a user (replaces all existing roles)
     * Enforces business rule: minimum 2 organizers must exist
     *
     * @param username User username (Story 1.16.2: meaningful ID)
     * @param newRoles New set of roles
     * @return Updated set of roles
     * @throws UserNotFoundException if user not found
     * @throws MinimumOrganizersException if removing ORGANIZER would violate business rule
     */
    public Set<Role> setRoles(String username, Set<Role> newRoles) {
        log.info("Setting roles for user {}: {}", username, newRoles);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        Set<Role> oldRoles = new HashSet<>(user.getRoles());

        // Business rule: If user had ORGANIZER role and new roles don't include it, enforce minimum
        boolean removingOrganizer = oldRoles.contains(Role.ORGANIZER) && !newRoles.contains(Role.ORGANIZER);
        if (removingOrganizer) {
            enforceMinimumOrganizers(user);
        }

        // Replace roles
        user.getRoles().clear();
        user.getRoles().addAll(newRoles);

        user = userRepository.save(user);

        // Publish event if roles actually changed
        if (!oldRoles.equals(newRoles)) {
            publishRoleChangedEvent(user, "Roles updated from " + oldRoles + " to " + newRoles);
            log.info("Roles updated for user: {}", username);
        } else {
            log.debug("Roles unchanged for user: {}", username);
        }

        return new HashSet<>(user.getRoles());
    }

    /**
     * Enforce business rule: minimum 2 organizers must exist
     *
     * @param userToCheck User whose ORGANIZER role is being removed
     * @throws MinimumOrganizersException if removing this user's ORGANIZER role would violate the rule
     */
    private void enforceMinimumOrganizers(User userToCheck) {
        List<User> organizers = userRepository.findByRolesContaining(Role.ORGANIZER);

        // Count organizers excluding the user being checked
        long organizerCount = organizers.stream()
                .filter(u -> !u.getId().equals(userToCheck.getId()))
                .count();

        if (organizerCount < MINIMUM_ORGANIZERS - 1) {
            String message = String.format(
                    "Cannot remove ORGANIZER role from user %s. System requires a minimum of %d organizers. "
                            + "Currently there are %d organizers remaining after removal.",
                    userToCheck.getUsername(),
                    MINIMUM_ORGANIZERS,
                    organizerCount + 1  // +1 because we're counting before removal
            );
            log.warn(message);
            throw new MinimumOrganizersException(message);
        }
    }

    /**
     * Publish UserRoleChangedEvent to EventBridge
     *
     * @param user User whose roles changed
     * @param description Description of the change
     */
    private void publishRoleChangedEvent(User user, String description) {
        // TODO: Implement UserRoleChangedEvent in future story
        // For now, we just log the event
        log.info("Publishing UserRoleChangedEvent for user {}: {}", user.getUsername(), description);

        // Story 1.16.2: Event would use username as aggregateId (String), not UUID
        // UserRoleChangedEvent event = new UserRoleChangedEvent(
        //     user.getUsername(),  // aggregateId
        //     user.getRoles(),
        //     description,
        //     currentUsername  // userId from SecurityContext
        // );
        // eventPublisher.publish(event);
    }
}
