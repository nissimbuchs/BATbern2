package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.CreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.UpdateUserRequest;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.events.UserCreatedEvent;
import ch.batbern.companyuser.events.UserDeletedEvent;
import ch.batbern.companyuser.events.UserUpdatedEvent;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.exception.UserValidationException;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        String username = "john.doe";
        when(securityContext.getCurrentUsername()).thenReturn(username);  // Story 2.6: Returns username from JWT

        User existingUser = User.builder()
                .username(username)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));
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
        String username = "john.doe";
        when(securityContext.getCurrentUsername()).thenReturn(username);  // Story 2.6: Returns username from JWT

        User user = User.builder()
                .username(username)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
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
        when(securityContext.getCurrentUsername()).thenReturn("admin.user");  // Story 2.6: Returns username from JWT custom:username claim

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
        when(securityContext.getCurrentUsername()).thenReturn("admin.user");  // Story 2.6: Returns username from JWT custom:username claim

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
        when(securityContext.getCurrentUsername()).thenReturn("admin.user");  // Story 2.6: Returns username from JWT custom:username claim

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

    // Test: should_updateUserByUsername_when_adminUpdatesUser
    @Test
    void should_updateUserByUsername_when_adminUpdatesUser() {
        // Given
        String username = "john.doe";
        String adminUsername = "admin.user";

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .bio("Old bio")
                .companyId("OldCompany")
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(securityContext.getCurrentUserId()).thenReturn(adminUsername);

        UserResponse mockResponse = new UserResponse();
        mockResponse.setId(username);
        mockResponse.setFirstName("Johnny");
        mockResponse.setLastName("Smith");
        mockResponse.setEmail("johnny.smith@example.com");
        mockResponse.setBio("New bio");
        mockResponse.setCompanyId("NewCompany");
        when(responseMapper.mapToResponse(any(User.class))).thenReturn(mockResponse);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("Johnny");
        request.setLastName("Smith");
        request.setEmail("johnny.smith@example.com");
        request.setBio("New bio");
        request.setCompanyId("NewCompany");

        // When
        UserResponse response = userService.updateUserByUsername(username, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(username);
        assertThat(response.getFirstName()).isEqualTo("Johnny");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getEmail()).isEqualTo("johnny.smith@example.com");
        assertThat(response.getBio()).isEqualTo("New bio");
        assertThat(response.getCompanyId()).isEqualTo("NewCompany");

        verify(userRepository).save(any(User.class));
        verify(searchService).invalidateCache();

        ArgumentCaptor<UserUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(UserUpdatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        UserUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getAggregateId()).isEqualTo(username);
        assertThat(event.getUpdatedFields()).containsKeys("firstName", "lastName", "email", "bio", "companyId");
        assertThat(event.getUserId()).isEqualTo(adminUsername);
    }

    // Test: should_updatePartialFields_when_someFieldsProvided
    @Test
    void should_updatePartialFields_when_someFieldsProvided() {
        // Given
        String username = "john.doe";

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .bio("Old bio")
                .companyId("OldCompany")
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(securityContext.getCurrentUserId()).thenReturn("admin.user");

        UserResponse mockResponse = new UserResponse();
        when(responseMapper.mapToResponse(any(User.class))).thenReturn(mockResponse);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("Johnny");  // Only update first name
        request.setBio("New bio");       // and bio

        // When
        userService.updateUserByUsername(username, request);

        // Then
        ArgumentCaptor<UserUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(UserUpdatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        UserUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getUpdatedFields()).containsKeys("firstName", "bio");
        assertThat(event.getUpdatedFields()).doesNotContainKeys("lastName", "email", "companyId");
    }

    // Test: should_throwUserNotFoundException_when_userDoesNotExist
    @Test
    void should_throwUserNotFoundException_when_updatingNonexistentUser() {
        // Given
        String username = "nonexistent.user";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("John");

        // When / Then
        assertThatThrownBy(() -> userService.updateUserByUsername(username, request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User with ID 'nonexistent.user' not found");

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    // Test: should_createUser_when_validDataProvided
    @Test
    void should_createUser_when_validDataProvided() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("newuser@example.com");
        request.setFirstName("New");
        request.setLastName("User");
        request.setCompanyId("TechCorp");
        request.setBio("Software engineer");
        request.setInitialRoles(List.of(CreateUserRequest.InitialRolesEnum.SPEAKER));

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(slugService.generateUsername("New", "User")).thenReturn("new.user");
        when(slugService.ensureUniqueUsername(eq("new.user"), any())).thenReturn("new.user");
        when(securityContext.getCurrentUsername()).thenReturn("admin.user");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .username("new.user")
                .email("newuser@example.com")
                .firstName("New")
                .lastName("User")
                .companyId("TechCorp")
                .bio("Software engineer")
                .roles(Set.of(Role.SPEAKER))
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse userResponse = new UserResponse();
        userResponse.setId("new.user");
        userResponse.setEmail("newuser@example.com");
        when(responseMapper.mapToResponse(any(User.class))).thenReturn(userResponse);

        // When
        UserResponse response = userService.createUser(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("new.user");

        verify(slugService).generateUsername("New", "User");
        verify(slugService).ensureUniqueUsername(eq("new.user"), any());
        verify(userRepository).save(any(User.class));

        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getAggregateId()).isEqualTo("new.user");
        assertThat(event.getEmail()).isEqualTo("newuser@example.com");
        assertThat(event.getFirstName()).isEqualTo("New");
        assertThat(event.getLastName()).isEqualTo("User");
        assertThat(event.getCompanyId()).isEqualTo("TechCorp");
    }

    // Test: should_createUserWithDefaultRole_when_noRolesProvided
    @Test
    void should_createUserWithDefaultRole_when_noRolesProvided() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("attendee@example.com");
        request.setFirstName("Jane");
        request.setLastName("Attendee");
        request.setCompanyId("FinanceCorp");
        // No initial roles specified

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(slugService.generateUsername("Jane", "Attendee")).thenReturn("jane.attendee");
        when(slugService.ensureUniqueUsername(eq("jane.attendee"), any())).thenReturn("jane.attendee");
        when(securityContext.getCurrentUsername()).thenReturn("admin.user");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .username("jane.attendee")
                .email("attendee@example.com")
                .firstName("Jane")
                .lastName("Attendee")
                .companyId("FinanceCorp")
                .roles(Set.of(Role.ATTENDEE))  // Default role
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse userResponse = new UserResponse();
        userResponse.setId("jane.attendee");
        when(responseMapper.mapToResponse(any(User.class))).thenReturn(userResponse);

        // When
        UserResponse response = userService.createUser(request);

        // Then
        assertThat(response).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publish(any(UserCreatedEvent.class));
    }

    // Test: should_throwException_when_emailAlreadyExists
    @Test
    void should_throwException_when_emailAlreadyExists() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("existing@example.com");
        request.setFirstName("Existing");
        request.setLastName("User");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(UserValidationException.class)
                .hasMessageContaining("User with email existing@example.com already exists");

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
