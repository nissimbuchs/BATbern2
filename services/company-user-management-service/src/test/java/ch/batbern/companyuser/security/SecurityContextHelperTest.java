package ch.batbern.companyuser.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SecurityContextHelper
 * AC10: Authentication integration - provide company context
 *
 * RED Phase: Writing failing tests first
 */
@ExtendWith(MockitoExtension.class)
class SecurityContextHelperTest {

    @InjectMocks
    private SecurityContextHelper securityContextHelper;

    /**
     * Test 10.1: should_extractUserId_when_authenticated
     */
    @Test
    void should_extractUserId_when_authenticated() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-123");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String userId = securityContextHelper.getCurrentUserId();

        // Then
        assertThat(userId).isEqualTo("user-123");
    }

    /**
     * Test 10.2: should_extractUserEmail_when_authenticated
     */
    @Test
    void should_extractUserEmail_when_authenticated() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("email")).thenReturn("user@example.com");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String email = securityContextHelper.getCurrentUserEmail();

        // Then
        assertThat(email).isEqualTo("user@example.com");
    }

    /**
     * Test 10.3: should_extractUserRoles_when_authenticated
     * Story 1.2.6: Updated to use custom:role claim
     */
    @Test
    void should_extractUserRoles_when_authenticated() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:role")).thenReturn("ORGANIZER,SPEAKER");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        var roles = securityContextHelper.getCurrentUserRoles();

        // Then
        assertThat(roles).hasSize(2);
        assertThat(roles).contains("ORGANIZER", "SPEAKER");
    }

    /**
     * Test 10.4: should_throwException_when_notAuthenticated
     */
    @Test
    void should_throwException_when_notAuthenticated() {
        // Given
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        // When/Then
        assertThatThrownBy(() -> securityContextHelper.getCurrentUserId())
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("No authentication found");
    }

    /**
     * Test 10.5: should_throwException_when_unauthenticated
     */
    @Test
    void should_throwException_when_unauthenticated() {
        // Given
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When/Then
        assertThatThrownBy(() -> securityContextHelper.getCurrentUserId())
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("User is not authenticated");
    }

    /**
     * Test 10.6: should_hasRole_when_roleInToken
     * Story 1.2.6: Updated to use custom:role claim
     */
    @Test
    void should_returnTrue_when_userHasRole() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:role")).thenReturn("ORGANIZER,SPEAKER");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        boolean hasRole = securityContextHelper.hasRole("ORGANIZER");

        // Then
        assertThat(hasRole).isTrue();
    }

    /**
     * Test 10.7: should_returnFalse_when_userDoesNotHaveRole
     * Story 1.2.6: Updated to use custom:role claim
     */
    @Test
    void should_returnFalse_when_userDoesNotHaveRole() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:role")).thenReturn("SPEAKER");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        boolean hasRole = securityContextHelper.hasRole("ORGANIZER");

        // Then
        assertThat(hasRole).isFalse();
    }

    /**
     * Test 10.8: should_extractCompanyId_when_presentInToken
     */
    @Test
    void should_extractCompanyId_when_presentInToken() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:companyId")).thenReturn("company-456");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String companyId = securityContextHelper.getCompanyId();

        // Then
        assertThat(companyId).isEqualTo("company-456");
    }

    /**
     * Test 10.9: should_returnNull_when_companyIdNotInToken
     */
    @Test
    void should_returnNull_when_companyIdNotInToken() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:companyId")).thenReturn(null);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String companyId = securityContextHelper.getCompanyId();

        // Then
        assertThat(companyId).isNull();
    }

    /**
     * Test 10.10: should_extractUsername_when_customUsernameClaimPresent
     * ADR-001: PreTokenGeneration Lambda sets custom:username claim from database
     * Story 1.16.2: Use username (meaningful ID) instead of UUID for public API
     */
    @Test
    void should_extractUsername_when_customUsernameClaimPresent() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:username")).thenReturn("john.doe");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String username = securityContextHelper.getCurrentUsername();

        // Then
        assertThat(username).isEqualTo("john.doe");
    }

    /**
     * Test 10.11: should_fallbackToSubject_when_customUsernameClaimMissing
     * Backward compatibility: if custom:username not present, use subject (UUID)
     */
    @Test
    void should_fallbackToSubject_when_customUsernameClaimMissing() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:username")).thenReturn(null);
        when(jwt.getSubject()).thenReturn("2324b842-2021-707d-09f7-0bf3fc7e981c");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String username = securityContextHelper.getCurrentUsername();

        // Then
        assertThat(username).isEqualTo("2324b842-2021-707d-09f7-0bf3fc7e981c");
    }

    /**
     * Test 10.12: should_returnNull_when_emailClaimMissing
     * Email is optional in JWT tokens
     */
    @Test
    void should_returnNull_when_emailClaimMissing() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("email")).thenReturn(null);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String email = securityContextHelper.getCurrentUserEmail();

        // Then
        assertThat(email).isNull();
    }

    /**
     * Test 10.13: should_returnEmptyList_when_roleClaimMissing
     * Roles are optional in JWT tokens
     */
    @Test
    void should_returnEmptyList_when_roleClaimMissing() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:role")).thenReturn(null);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        var roles = securityContextHelper.getCurrentUserRoles();

        // Then
        assertThat(roles).isEmpty();
    }

    /**
     * Test 10.14: should_returnEmptyList_when_roleClaimEmpty
     * Handle empty role string
     */
    @Test
    void should_returnEmptyList_when_roleClaimEmpty() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:role")).thenReturn("");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        var roles = securityContextHelper.getCurrentUserRoles();

        // Then
        assertThat(roles).isEmpty();
    }

    /**
     * Test 10.15: should_trimRoles_when_roleClaimHasWhitespace
     * Handle roles with extra whitespace
     */
    @Test
    void should_trimRoles_when_roleClaimHasWhitespace() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:role")).thenReturn(" ORGANIZER , SPEAKER ");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        var roles = securityContextHelper.getCurrentUserRoles();

        // Then
        assertThat(roles).hasSize(2);
        assertThat(roles).contains("ORGANIZER", "SPEAKER");
    }

    /**
     * Test 10.16: should_extractUserId_when_mockUserPrincipal
     * Test support for @WithMockUser in tests
     */
    @Test
    void should_extractUserId_when_mockUserPrincipal() {
        // Given
        org.springframework.security.core.userdetails.User mockUser =
                new org.springframework.security.core.userdetails.User(
                        "testuser",
                        "password",
                        java.util.Collections.emptyList()
                );

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String userId = securityContextHelper.getCurrentUserId();

        // Then
        assertThat(userId).isEqualTo("testuser");
    }

    /**
     * Test 10.17: should_extractUsername_when_mockUserPrincipal
     * Test support for @WithMockUser in tests
     */
    @Test
    void should_extractUsername_when_mockUserPrincipal() {
        // Given
        org.springframework.security.core.userdetails.User mockUser =
                new org.springframework.security.core.userdetails.User(
                        "testuser",
                        "password",
                        java.util.Collections.emptyList()
                );

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String username = securityContextHelper.getCurrentUsername();

        // Then
        assertThat(username).isEqualTo("testuser");
    }

    /**
     * Test 10.18: should_extractEmail_when_mockUserPrincipal
     * Test support for @WithMockUser in tests (uses username as email)
     */
    @Test
    void should_extractEmail_when_mockUserPrincipal() {
        // Given
        org.springframework.security.core.userdetails.User mockUser =
                new org.springframework.security.core.userdetails.User(
                        "testuser@example.com",
                        "password",
                        java.util.Collections.emptyList()
                );

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String email = securityContextHelper.getCurrentUserEmail();

        // Then
        assertThat(email).isEqualTo("testuser@example.com");
    }

    /**
     * Test 10.19: should_extractRoles_when_mockUserPrincipal
     * Test support for @WithMockUser(roles = {"ORGANIZER"}) in tests
     */
    @Test
    void should_extractRoles_when_mockUserPrincipal() {
        // Given
        org.springframework.security.core.userdetails.User mockUser =
                new org.springframework.security.core.userdetails.User(
                        "testuser",
                        "password",
                        java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ORGANIZER"),
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SPEAKER")
                        )
                );

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> mockUser.getAuthorities());

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        var roles = securityContextHelper.getCurrentUserRoles();

        // Then
        assertThat(roles).hasSize(2);
        assertThat(roles).contains("ORGANIZER", "SPEAKER");
    }

    /**
     * Test 10.20: should_returnNull_when_mockUserPrincipal_getCompanyId
     * Company ID is not available for mock users
     */
    @Test
    void should_returnNull_when_mockUserPrincipal_getCompanyId() {
        // Given
        org.springframework.security.core.userdetails.User mockUser =
                new org.springframework.security.core.userdetails.User(
                        "testuser",
                        "password",
                        java.util.Collections.emptyList()
                );

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        String companyId = securityContextHelper.getCompanyId();

        // Then
        assertThat(companyId).isNull();
    }

    /**
     * Test 10.21: should_throwException_when_unsupportedPrincipalType
     * Verify error handling for unknown principal types
     */
    @Test
    void should_throwException_when_unsupportedPrincipalType_getCurrentUserId() {
        // Given
        String unsupportedPrincipal = "unsupported-principal";

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(unsupportedPrincipal);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When/Then
        assertThatThrownBy(() -> securityContextHelper.getCurrentUserId())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsupported authentication principal type");
    }

    /**
     * Test 10.22: should_throwException_when_unsupportedPrincipalType_getCurrentUsername
     * Verify error handling for unknown principal types
     */
    @Test
    void should_throwException_when_unsupportedPrincipalType_getCurrentUsername() {
        // Given
        String unsupportedPrincipal = "unsupported-principal";

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(unsupportedPrincipal);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When/Then
        assertThatThrownBy(() -> securityContextHelper.getCurrentUsername())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsupported authentication principal type");
    }

    /**
     * Test 10.23: should_throwException_when_unsupportedPrincipalType_getCurrentUserEmail
     * Verify error handling for unknown principal types
     */
    @Test
    void should_throwException_when_unsupportedPrincipalType_getCurrentUserEmail() {
        // Given
        String unsupportedPrincipal = "unsupported-principal";

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(unsupportedPrincipal);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When/Then
        assertThatThrownBy(() -> securityContextHelper.getCurrentUserEmail())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsupported authentication principal type");
    }

    /**
     * Test 10.24: should_throwException_when_unsupportedPrincipalType_getCurrentUserRoles
     * Verify error handling for unknown principal types
     */
    @Test
    void should_throwException_when_unsupportedPrincipalType_getCurrentUserRoles() {
        // Given
        String unsupportedPrincipal = "unsupported-principal";

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(unsupportedPrincipal);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When/Then
        assertThatThrownBy(() -> securityContextHelper.getCurrentUserRoles())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsupported authentication principal type");
    }

    /**
     * Test 10.25: should_throwException_when_unsupportedPrincipalType_getCompanyId
     * Verify error handling for unknown principal types
     */
    @Test
    void should_throwException_when_unsupportedPrincipalType_getCompanyId() {
        // Given
        String unsupportedPrincipal = "unsupported-principal";

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(unsupportedPrincipal);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When/Then
        assertThatThrownBy(() -> securityContextHelper.getCompanyId())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unsupported authentication principal type");
    }

    /**
     * Test 10.26: should_parseSingleRole_when_noCommaInRoleClaim
     * Handle single role without comma
     */
    @Test
    void should_parseSingleRole_when_noCommaInRoleClaim() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("custom:role")).thenReturn("ORGANIZER");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(authentication.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // When
        var roles = securityContextHelper.getCurrentUserRoles();

        // Then
        assertThat(roles).hasSize(1);
        assertThat(roles).contains("ORGANIZER");
    }
}

