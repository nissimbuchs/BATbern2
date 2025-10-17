package ch.batbern.companyuser.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
     */
    @Test
    void should_extractUserRoles_when_authenticated() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("cognito:groups")).thenReturn(java.util.List.of("ORGANIZER", "SPEAKER"));

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
     */
    @Test
    void should_returnTrue_when_userHasRole() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("cognito:groups")).thenReturn(java.util.List.of("ORGANIZER", "SPEAKER"));

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
     */
    @Test
    void should_returnFalse_when_userDoesNotHaveRole() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("cognito:groups")).thenReturn(java.util.List.of("SPEAKER"));

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
}
