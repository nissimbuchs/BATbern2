package ch.batbern.companyuser.interceptor;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.event.UserCreatedEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for JITUserProvisioningInterceptor
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * Task 5a: JIT Provisioning Interceptor TDD Tests
 * <p>
 * TEST NAMING CONVENTION: should_expectedBehavior_when_condition
 */
@ExtendWith(MockitoExtension.class)
class JITUserProvisioningInterceptorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private JITUserProvisioningInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // Setup security context
        SecurityContextHolder.setContext(securityContext);
    }

    /**
     * Test Data Builders
     */

    private Jwt createJwt(String subject, String email, String givenName, String familyName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        if (email != null) claims.put("email", email);
        if (givenName != null) claims.put("given_name", givenName);
        if (familyName != null) claims.put("family_name", familyName);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());

        return new Jwt(
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                headers,
                claims
        );
    }

    private JwtAuthenticationToken createJwtAuthentication(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private User createUser(String cognitoUserId, String username, String email, Set<Role> roles) {
        return User.builder()
                .id(UUID.randomUUID())
                .cognitoUserId(cognitoUserId)
                .username(username)
                .email(email)
                .firstName("Test")
                .lastName("User")
                .roles(roles)
                .isActive(true)
                .build();
    }

    /**
     * AC2 Tests: User Existence Check
     */

    @Test
    void should_continueRequest_when_userAlreadyExistsInDatabase() throws Exception {
        // Given: User exists in database
        String cognitoUserId = "test-cognito-id-123";
        Jwt jwt = createJwt(cognitoUserId, "test@example.com", "John", "Doe");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        User existingUser = createUser(cognitoUserId, "john.doe", "test@example.com", Set.of(Role.ATTENDEE));
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.of(existingUser));

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues
        assertThat(result).isTrue();

        // And: User repository was queried
        verify(userRepository).findByCognitoUserId(cognitoUserId);

        // And: No new user was created
        verify(userRepository, never()).save(any(User.class));

        // And: No event was published
        verify(eventPublisher, never()).publishEvent(any(UserCreatedEvent.class));
    }

    @Test
    void should_skipProvisioning_when_authenticationIsNull() throws Exception {
        // Given: No authentication in security context
        when(securityContext.getAuthentication()).thenReturn(null);

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues
        assertThat(result).isTrue();

        // And: No database queries were made
        verify(userRepository, never()).findByCognitoUserId(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void should_skipProvisioning_when_authenticationIsNotJWT() throws Exception {
        // Given: Authentication is not JWT token
        Authentication nonJwtAuth = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(nonJwtAuth);

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues
        assertThat(result).isTrue();

        // And: No database queries were made
        verify(userRepository, never()).findByCognitoUserId(anyString());
    }

    @Test
    void should_skipProvisioning_when_jwtSubjectIsNull() throws Exception {
        // Given: JWT token with null subject
        Jwt jwt = createJwt(null, "test@example.com", "John", "Doe");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );
        when(securityContext.getAuthentication()).thenReturn(authentication);

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues
        assertThat(result).isTrue();

        // And: No database queries were made
        verify(userRepository, never()).findByCognitoUserId(anyString());
    }

    @Test
    void should_skipProvisioning_when_jwtSubjectIsEmpty() throws Exception {
        // Given: JWT token with empty subject
        Jwt jwt = createJwt("", "test@example.com", "John", "Doe");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );
        when(securityContext.getAuthentication()).thenReturn(authentication);

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues
        assertThat(result).isTrue();

        // And: No database queries were made
        verify(userRepository, never()).findByCognitoUserId(anyString());
    }

    /**
     * AC2 Tests: Automatic User Creation
     */

    @Test
    void should_createUser_when_cognitoUserNotInDatabase() throws Exception {
        // Given: User does not exist in database
        String cognitoUserId = "new-cognito-id-456";
        String email = "newuser@example.com";
        Jwt jwt = createJwt(cognitoUserId, email, "Jane", "Smith");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        User savedUser = createUser(cognitoUserId, "newuser", email, Set.of(Role.ATTENDEE));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues
        assertThat(result).isTrue();

        // And: User was created in database
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getCognitoUserId()).isEqualTo(cognitoUserId);
        assertThat(createdUser.getEmail()).isEqualTo(email);
        assertThat(createdUser.getFirstName()).isEqualTo("Jane");
        assertThat(createdUser.getLastName()).isEqualTo("Smith");
        assertThat(createdUser.getUsername()).isEqualTo("newuser");
        assertThat(createdUser.isActive()).isTrue();
        assertThat(createdUser.getRoles()).contains(Role.ATTENDEE);

        // And: UserCreatedEvent was published
        verify(eventPublisher).publishEvent(any(UserCreatedEvent.class));
    }

    @Test
    void should_assignRoleFromJWT_when_jitProvisioningUser() throws Exception {
        // Given: User does not exist with ORGANIZER role in JWT
        String cognitoUserId = "organizer-cognito-id";
        Jwt jwt = createJwt(cognitoUserId, "organizer@example.com", "Admin", "User");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        // When: Interceptor pre-handle is called
        interceptor.preHandle(request, response, new Object());

        // Then: User was created with ORGANIZER role
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getRoles()).contains(Role.ORGANIZER);
    }

    @Test
    void should_assignMultipleRoles_when_userHasMultipleAuthorities() throws Exception {
        // Given: User does not exist with multiple roles in JWT
        String cognitoUserId = "multi-role-cognito-id";
        Jwt jwt = createJwt(cognitoUserId, "multi@example.com", "Multi", "Role");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("ROLE_ORGANIZER"),
                        new SimpleGrantedAuthority("ROLE_SPEAKER")
                )
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        // When: Interceptor pre-handle is called
        interceptor.preHandle(request, response, new Object());

        // Then: User was created with both roles
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getRoles()).containsExactlyInAnyOrder(Role.ORGANIZER, Role.SPEAKER);
    }

    @Test
    void should_defaultToAttendeeRole_when_noRolesInJWT() throws Exception {
        // Given: User does not exist with no roles in JWT
        String cognitoUserId = "no-role-cognito-id";
        Jwt jwt = createJwt(cognitoUserId, "norole@example.com", "No", "Role");
        JwtAuthenticationToken authentication = createJwtAuthentication(jwt, Collections.emptyList());

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        // When: Interceptor pre-handle is called
        interceptor.preHandle(request, response, new Object());

        // Then: User was created with default ATTENDEE role
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getRoles()).containsExactly(Role.ATTENDEE);
    }

    /**
     * AC2 Tests: Username Generation
     */

    @Test
    void should_generateUsernameFromEmail_when_creatingUser() throws Exception {
        // Given: User does not exist with email
        String cognitoUserId = "username-test-id";
        String email = "john.doe@example.com";
        Jwt jwt = createJwt(cognitoUserId, email, "John", "Doe");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("john.doe")).thenReturn(false);

        // When: Interceptor pre-handle is called
        interceptor.preHandle(request, response, new Object());

        // Then: Username was generated from email
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getUsername()).isEqualTo("john.doe");
    }

    @Test
    void should_addNumericSuffix_when_usernameAlreadyExists() throws Exception {
        // Given: User does not exist but username is taken
        String cognitoUserId = "collision-test-id";
        String email = "duplicate@example.com";
        Jwt jwt = createJwt(cognitoUserId, email, "Duplicate", "User");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("duplicate")).thenReturn(true);
        when(userRepository.existsByUsername("duplicate.1")).thenReturn(false);

        // When: Interceptor pre-handle is called
        interceptor.preHandle(request, response, new Object());

        // Then: Username has numeric suffix
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getUsername()).isEqualTo("duplicate.1");
    }

    @Test
    void should_generateTimestampUsername_when_emailIsNull() throws Exception {
        // Given: User does not exist with null email
        String cognitoUserId = "no-email-id";
        Jwt jwt = createJwt(cognitoUserId, null, "No", "Email");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            // Set ID to simulate database save
            return User.builder()
                    .id(UUID.randomUUID())
                    .cognitoUserId(user.getCognitoUserId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .roles(user.getRoles())
                    .isActive(user.isActive())
                    .build();
        });

        // When: Interceptor pre-handle is called
        interceptor.preHandle(request, response, new Object());

        // Then: Username starts with "user."
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getUsername()).startsWith("user.");
        assertThat(createdUser.getEmail()).isEmpty();
    }

    /**
     * AC2 Tests: Event Publishing
     */

    @Test
    void should_publishUserCreatedEvent_when_jitProvisioningSucceeds() throws Exception {
        // Given: User does not exist
        String cognitoUserId = "event-test-id";
        String email = "event@example.com";
        Jwt jwt = createJwt(cognitoUserId, email, "Event", "User");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        User savedUser = createUser(cognitoUserId, "event", email, Set.of(Role.ATTENDEE));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When: Interceptor pre-handle is called
        interceptor.preHandle(request, response, new Object());

        // Then: UserCreatedEvent was published
        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(savedUser.getId());
        assertThat(event.getCognitoUserId()).isEqualTo(cognitoUserId);
        assertThat(event.getEmail()).isEqualTo(email);
        assertThat(event.getSource()).isEqualTo("JIT_PROVISIONING");
        assertThat(event.getRoles()).contains("ATTENDEE");
    }

    /**
     * AC2 Tests: Error Handling (Non-Blocking)
     */

    @Test
    void should_continueRequest_when_jitProvisioningFails() throws Exception {
        // Given: User repository throws exception
        String cognitoUserId = "error-test-id";
        Jwt jwt = createJwt(cognitoUserId, "error@example.com", "Error", "User");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues (non-blocking)
        assertThat(result).isTrue();

        // And: No user was saved (exception prevented it)
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void should_continueRequest_when_userSaveFails() throws Exception {
        // Given: User save operation fails
        String cognitoUserId = "save-error-id";
        Jwt jwt = createJwt(cognitoUserId, "saveerror@example.com", "Save", "Error");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenThrow(new RuntimeException("Database constraint violation"));

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues (non-blocking)
        assertThat(result).isTrue();
    }

    @Test
    void should_continueRequest_when_eventPublishingFails() throws Exception {
        // Given: Event publishing fails
        String cognitoUserId = "event-error-id";
        Jwt jwt = createJwt(cognitoUserId, "eventerror@example.com", "Event", "Error");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        User savedUser = createUser(cognitoUserId, "eventerror", "eventerror@example.com", Set.of(Role.ATTENDEE));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doThrow(new RuntimeException("EventBridge unavailable"))
                .when(eventPublisher).publishEvent(any(UserCreatedEvent.class));

        // When: Interceptor pre-handle is called
        boolean result = interceptor.preHandle(request, response, new Object());

        // Then: Request continues (non-blocking)
        assertThat(result).isTrue();

        // And: User was still saved
        verify(userRepository).save(any(User.class));
    }

    /**
     * AC2 Tests: Request Continuation
     */

    @Test
    void should_alwaysReturnTrue_when_preHandleCalled() throws Exception {
        // Given: Various scenarios (null auth, existing user, new user, errors)
        // Test 1: Null authentication
        when(securityContext.getAuthentication()).thenReturn(null);
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        // Test 2: Existing user
        String cognitoUserId = "existing-user-id";
        Jwt jwt = createJwt(cognitoUserId, "existing@example.com", "Existing", "User");
        JwtAuthenticationToken authentication = createJwtAuthentication(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );
        User existingUser = createUser(cognitoUserId, "existing", "existing@example.com", Set.of(Role.ATTENDEE));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(userRepository.findByCognitoUserId(cognitoUserId)).thenReturn(Optional.of(existingUser));
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        // Test 3: New user provisioning
        String newCognitoUserId = "new-user-id";
        Jwt newJwt = createJwt(newCognitoUserId, "new@example.com", "New", "User");
        JwtAuthenticationToken newAuthentication = createJwtAuthentication(
                newJwt,
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))
        );
        when(securityContext.getAuthentication()).thenReturn(newAuthentication);
        when(userRepository.findByCognitoUserId(newCognitoUserId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        // Test 4: Error scenario
        when(userRepository.findByCognitoUserId(anyString()))
                .thenThrow(new RuntimeException("Database error"));
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
