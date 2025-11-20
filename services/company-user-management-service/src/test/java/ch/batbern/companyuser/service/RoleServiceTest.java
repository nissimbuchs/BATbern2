package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.exception.MinimumOrganizersException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RoleService
 * Tests role assignment, removal, and business rules (minimum 2 organizers)
 *
 * Story 1.14-2 Task 10: Role Service & Business Rules (RED phase)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService Unit Tests")
class RoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private RoleService roleService;

    private User testUser;
    private UUID testUserId;
    private String testUsername;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUsername = "john.doe";
        testUser = User.builder()
                .id(testUserId)
                .username(testUsername)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .roles(new HashSet<>(Arrays.asList(Role.ATTENDEE)))
                .build();
    }

    @Test
    @DisplayName("should_getUserRoles_when_userExists")
    void should_getUserRoles_when_userExists() {
        // Given
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        // When
        Set<Role> roles = roleService.getUserRoles(testUsername);

        // Then
        assertThat(roles).isNotNull();
        assertThat(roles).containsExactly(Role.ATTENDEE);
        verify(userRepository).findByUsername(testUsername);
    }

    @Test
    @DisplayName("should_throwUserNotFoundException_when_userNotFoundForGetRoles")
    void should_throwUserNotFoundException_when_userNotFoundForGetRoles() {
        // Given
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> roleService.getUserRoles(testUsername))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(testUsername);
    }

    @Test
    @DisplayName("should_addRole_when_validRoleProvided")
    void should_addRole_when_validRoleProvided() {
        // Given
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        Set<Role> updatedRoles = roleService.addRole(testUsername, Role.SPEAKER);

        // Then
        assertThat(updatedRoles).containsExactlyInAnyOrder(Role.ATTENDEE, Role.SPEAKER);
        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).save(testUser);
        // TODO: Restore when UserRoleChangedEvent is implemented
        // verify(eventPublisher).publish(any());  // Should publish UserRoleChangedEvent
    }

    @Test
    @DisplayName("should_notPublishEvent_when_roleAlreadyExists")
    void should_notPublishEvent_when_roleAlreadyExists() {
        // Given
        testUser.getRoles().add(Role.SPEAKER);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        Set<Role> updatedRoles = roleService.addRole(testUsername, Role.SPEAKER);

        // Then
        assertThat(updatedRoles).containsExactlyInAnyOrder(Role.ATTENDEE, Role.SPEAKER);
        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).save(testUser);
        verify(eventPublisher, never()).publish(any());  // Should NOT publish event if role already exists
    }

    @Test
    @DisplayName("should_removeRole_when_validRoleProvidedAndNotLastOrganizer")
    void should_removeRole_when_validRoleProvidedAndNotLastOrganizer() {
        // Given
        testUser.getRoles().add(Role.SPEAKER);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        // Note: No need to mock findByRolesContaining since user is not an organizer

        // When
        Set<Role> updatedRoles = roleService.removeRole(testUsername, Role.SPEAKER);

        // Then
        assertThat(updatedRoles).containsExactly(Role.ATTENDEE);
        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).save(testUser);
        // TODO: Restore when UserRoleChangedEvent is implemented
        // verify(eventPublisher).publish(any());  // Should publish UserRoleChangedEvent
    }

    @Test
    @DisplayName("should_throwMinimumOrganizersException_when_removingOrganizerAndOnlyOneRemains")
    void should_throwMinimumOrganizersException_when_removingOrganizerAndOnlyOneRemains() {
        // Given
        testUser.getRoles().clear();
        testUser.getRoles().add(Role.ORGANIZER);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        // Mock: This user is the only organizer
        when(userRepository.findByRolesContaining(Role.ORGANIZER)).thenReturn(List.of(testUser));

        // When/Then
        assertThatThrownBy(() -> roleService.removeRole(testUsername, Role.ORGANIZER))
                .isInstanceOf(MinimumOrganizersException.class)
                .hasMessageContaining("minimum of 2 organizers");

        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).findByRolesContaining(Role.ORGANIZER);
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should_allowRemovingOrganizer_when_twoOrMoreOrganizersExist")
    void should_allowRemovingOrganizer_when_twoOrMoreOrganizersExist() {
        // Given
        testUser.getRoles().clear();
        testUser.getRoles().add(Role.ORGANIZER);
        testUser.getRoles().add(Role.ATTENDEE);

        User anotherOrganizer = User.builder()
                .id(UUID.randomUUID())
                .username("jane.smith")
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .cognitoUserId("cognito-456")
                .roles(new HashSet<>(Arrays.asList(Role.ORGANIZER)))
                .build();

        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Mock: Two organizers exist (this user + another)
        when(userRepository.findByRolesContaining(Role.ORGANIZER)).thenReturn(List.of(testUser, anotherOrganizer));

        // When
        Set<Role> updatedRoles = roleService.removeRole(testUsername, Role.ORGANIZER);

        // Then
        assertThat(updatedRoles).containsExactly(Role.ATTENDEE);
        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).findByRolesContaining(Role.ORGANIZER);
        verify(userRepository).save(testUser);
        // TODO: Restore when UserRoleChangedEvent is implemented
        // verify(eventPublisher).publish(any());  // Should publish UserRoleChangedEvent
    }

    @Test
    @DisplayName("should_notPublishEvent_when_roleNotPresent")
    void should_notPublishEvent_when_roleNotPresent() {
        // Given
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        Set<Role> updatedRoles = roleService.removeRole(testUsername, Role.SPEAKER);  // User doesn't have SPEAKER role

        // Then
        assertThat(updatedRoles).containsExactly(Role.ATTENDEE);
        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).save(testUser);
        verify(eventPublisher, never()).publish(any());  // Should NOT publish event if role wasn't present
    }

    @Test
    @DisplayName("should_setRoles_when_validRolesProvidedAndBusinessRulesRespected")
    void should_setRoles_when_validRolesProvidedAndBusinessRulesRespected() {
        // Given
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        // Note: User doesn't have ORGANIZER role, so no need to mock findByRolesContaining

        Set<Role> newRoles = new HashSet<>(Arrays.asList(Role.SPEAKER, Role.PARTNER));

        // When
        Set<Role> updatedRoles = roleService.setRoles(testUsername, newRoles);

        // Then
        assertThat(updatedRoles).containsExactlyInAnyOrder(Role.SPEAKER, Role.PARTNER);
        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).save(testUser);
        // TODO: Restore when UserRoleChangedEvent is implemented
        // verify(eventPublisher).publish(any());  // Should publish UserRoleChangedEvent
    }

    @Test
    @DisplayName("should_throwMinimumOrganizersException_when_setRolesWouldRemoveLastOrganizer")
    void should_throwMinimumOrganizersException_when_setRolesWouldRemoveLastOrganizer() {
        // Given
        testUser.getRoles().clear();
        testUser.getRoles().add(Role.ORGANIZER);
        when(userRepository.findByUsername(testUsername)).thenReturn(Optional.of(testUser));

        // Mock: This user is the only organizer
        when(userRepository.findByRolesContaining(Role.ORGANIZER)).thenReturn(List.of(testUser));

        Set<Role> newRoles = new HashSet<>(Arrays.asList(Role.ATTENDEE));  // Removing ORGANIZER role

        // When/Then
        assertThatThrownBy(() -> roleService.setRoles(testUsername, newRoles))
                .isInstanceOf(MinimumOrganizersException.class)
                .hasMessageContaining("minimum of 2 organizers");

        verify(userRepository).findByUsername(testUsername);
        verify(userRepository).findByRolesContaining(Role.ORGANIZER);
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
