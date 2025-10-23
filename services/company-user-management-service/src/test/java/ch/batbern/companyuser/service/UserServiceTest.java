package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.UpdateUserRequest;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.events.UserCreatedEvent;
import ch.batbern.companyuser.events.UserDeletedEvent;
import ch.batbern.companyuser.events.UserUpdatedEvent;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.service.SlugGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RED Phase: Failing tests for UserService
 * Story 1.16.2: Username generation and dual-identifier pattern
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CognitoIntegrationService cognitoService;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private UserSearchService searchService;

    @Mock
    private SecurityContextHelper securityContext;

    @Mock
    private SlugGenerationService slugService;

    @Mock
    private ch.batbern.companyuser.service.UserResponseMapper responseMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository,
            cognitoService,
            eventPublisher,
            searchService,
            securityContext,
            slugService,
            responseMapper
        );
    }

    // Test 2.1: should_updateUserProfile_when_validDataProvided (Story 1.16.2: cognito-based)
    @Test
    void should_updateUserProfile_when_validDataProvided() {
        // Given
        String cognitoUserId = "cognito-123";
        String username = "john.doe";
        when(securityContext.getCurrentUserId()).thenReturn(cognitoUserId);  // Returns Cognito user ID

        User existingUser = User.builder()
                .username(username)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId(cognitoUserId)
                .build();

        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        UserResponse mockResponse = new UserResponse();
        mockResponse.setId(username);
        mockResponse.setFirstName("Johnny");
        mockResponse.setBio("Updated bio");
        when(responseMapper.mapToResponse(any(User.class))).thenReturn(mockResponse);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("Johnny");
        request.setBio("Updated bio");

        // When
        UserResponse response = userService.updateCurrentUser(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(username);  // Story 1.16.2: username as id
        assertThat(response.getFirstName()).isEqualTo("Johnny");
        assertThat(response.getBio()).isEqualTo("Updated bio");

        verify(userRepository).save(any(User.class));
        verify(cognitoService, never()).syncUserAttributes(any(User.class));  // NO-OP: DB is source of truth
        verify(searchService).invalidateCache();
    }

    // Test 2.2: NO-OP Cognito sync - DB is source of truth
    @Test
    void should_syncCognito_when_userUpdated() {
        // Given
        String cognitoUserId = "cognito-123";
        String username = "john.doe";
        when(securityContext.getCurrentUserId()).thenReturn(cognitoUserId);  // Returns Cognito user ID

        User user = User.builder()
                .username(username)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId(cognitoUserId)
                .build();

        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("Johnny");

        // When
        userService.updateCurrentUser(request);

        // Then - NO-OP: DB is source of truth, Cognito not synced
        verify(cognitoService, never()).syncUserAttributes(any(User.class));
    }

    // Test 5.3: should_return404_when_userNotFound
    @Test
    void should_return404_when_userNotFound() {
        // Given
        String username = "nonexistent.user";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> userService.getUserByUsername(username))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User with ID 'nonexistent.user' not found");
    }

    // Test 12.1: should_createUser_when_userNotExists (get-or-create)
    @Test
    void should_createUser_when_userNotExists() {
        // Given
        GetOrCreateUserRequest request = new GetOrCreateUserRequest();
        request.setEmail("new.user@example.com");
        request.setFirstName("New");
        request.setLastName("User");
        request.setCompanyId("TechCorp");
        request.setCreateIfMissing(true);

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(cognitoService.createCognitoUser(request)).thenReturn("cognito-123");
        when(slugService.generateUsername("New", "User")).thenReturn("new.user");
        when(slugService.ensureUniqueUsername(eq("new.user"), any())).thenReturn("new.user");

        User createdUser = User.builder()
                .id(UUID.randomUUID())
                .username("new.user")
                .email("new.user@example.com")
                .firstName("New")
                .lastName("User")
                .companyId("TechCorp")
                .cognitoUserId("cognito-123")
                .roles(Set.of(Role.ATTENDEE))
                .build();

        when(userRepository.save(any(User.class))).thenReturn(createdUser);
        when(securityContext.getCurrentUserId()).thenReturn("admin.user");

        UserResponse userResponse = new UserResponse();
        userResponse.setId("new.user");
        userResponse.setEmail("new.user@example.com");
        userResponse.setFirstName("New");
        userResponse.setLastName("User");
        userResponse.setCompanyId("TechCorp");
        when(responseMapper.mapToResponse(any(User.class))).thenReturn(userResponse);

        // When
        GetOrCreateUserResponse response = userService.getOrCreateUser(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("new.user");  // Story 1.16.2: username
        assertThat(response.getCreated()).isTrue();
        assertThat(response.getCognitoUserId()).isEqualTo("cognito-123");
        assertThat(response.getUser().getId()).isEqualTo("new.user");
        assertThat(response.getUser().getCompanyId()).isEqualTo("TechCorp");

        verify(cognitoService).createCognitoUser(request);
        verify(slugService).generateUsername("New", "User");
        verify(slugService).ensureUniqueUsername(eq("new.user"), any());
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publish(any(UserCreatedEvent.class));
    }

    // Test 12.2: should_returnExistingUser_when_userExists (get-or-create)
    @Test
    void should_returnExistingUser_when_userExists() {
        // Given
        GetOrCreateUserRequest request = new GetOrCreateUserRequest();
        request.setEmail("existing@example.com");
        request.setFirstName("Existing");
        request.setLastName("User");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .username("existing.user")
                .email("existing@example.com")
                .firstName("Existing")
                .lastName("User")
                .companyId("TechCorp")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(existingUser));

        UserResponse userResponse = new UserResponse();
        userResponse.setId("existing.user");
        userResponse.setEmail("existing@example.com");
        userResponse.setFirstName("Existing");
        userResponse.setLastName("User");
        userResponse.setCompanyId("TechCorp");
        when(responseMapper.mapToResponse(any(User.class))).thenReturn(userResponse);

        // When
        GetOrCreateUserResponse response = userService.getOrCreateUser(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("existing.user");  // Story 1.16.2
        assertThat(response.getCreated()).isFalse();
        assertThat(response.getUser().getId()).isEqualTo("existing.user");

        verify(userRepository, never()).save(any());
        verify(cognitoService, never()).createCognitoUser(any());
        verify(eventPublisher, never()).publish(any());
    }

    // Test: should_publishUserCreatedEvent_with_stringIDs (Story 1.16.2)
    @Test
    void should_publishUserCreatedEvent_with_stringIDs() {
        // Given
        GetOrCreateUserRequest request = new GetOrCreateUserRequest()
                .email("new@example.com")
                .firstName("New")
                .lastName("User")
                .companyId("CompanyX")
                .createIfMissing(true);

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(cognitoService.createCognitoUser(request)).thenReturn("cognito-123");
        when(slugService.generateUsername("New", "User")).thenReturn("new.user");
        when(slugService.ensureUniqueUsername(eq("new.user"), any())).thenReturn("new.user");

        User createdUser = User.builder()
                .id(UUID.randomUUID())
                .username("new.user")
                .email("new@example.com")
                .firstName("New")
                .lastName("User")
                .companyId("CompanyX")
                .cognitoUserId("cognito-123")
                .roles(Set.of(Role.ATTENDEE))
                .build();

        when(userRepository.save(any(User.class))).thenReturn(createdUser);
        when(securityContext.getCurrentUserId()).thenReturn("admin.user");

        // When
        userService.getOrCreateUser(request);

        // Then
        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getAggregateId()).isEqualTo("new.user");  // Story 1.16.2: username
        assertThat(event.getUsername()).isEqualTo("new.user");  // Story 1.16.2: username
        assertThat(event.getCompanyId()).isEqualTo("CompanyX");  // Story 1.16.2: company name
        assertThat(event.getEmail()).isEqualTo("new@example.com");
        assertThat(event.getCognitoUserId()).isEqualTo("cognito-123");
    }

    // Test: should_deleteUser_and_publishEvent
    @Test
    void should_deleteUser_and_publishEvent() {
        // Given
        String username = "john.doe";
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(securityContext.getCurrentUserId()).thenReturn("admin.user");

        // When
        userService.deleteUser(username);

        // Then
        verify(userRepository).delete(user);

        ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        UserDeletedEvent event = eventCaptor.getValue();
        assertThat(event.getAggregateId()).isEqualTo(username);
        assertThat(event.getUsername()).isEqualTo(username);
        assertThat(event.getEmail()).isEqualTo("john@example.com");
        assertThat(event.getReason()).isEqualTo("GDPR compliance");
    }
}
