package ch.batbern.gateway.auth;

import ch.batbern.gateway.auth.exception.AuthenticationException;
import ch.batbern.gateway.auth.exception.TokenExpiredException;
import ch.batbern.gateway.auth.model.UserContext;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitoJWTValidatorTest {

    private CognitoJWTValidator jwtValidator;

    @Mock
    private CognitoJWKSProvider jwksProvider;

    private TestKeyPairGenerator testKeyPair;

    private static final String USER_POOL_ID = "eu-central-1_TestPool";
    private static final String REGION = "eu-central-1";
    private static final String APP_CLIENT_ID = "test-client-id";
    private static final String TEST_KEY_ID = "test-key-id-123";

    @BeforeEach
    void setUp() {
        testKeyPair = new TestKeyPairGenerator();
        jwtValidator = new CognitoJWTValidator(jwksProvider, USER_POOL_ID, REGION, APP_CLIENT_ID);
    }

    // Test 3.1: should_validateJWTToken_when_requestReceived
    @Test
    @DisplayName("should_validateJWTToken_when_requestReceived")
    void should_validateJWTToken_when_requestReceived() throws Exception {
        // Given
        String validToken = createValidJwtToken();
        when(jwksProvider.getAlgorithm(any())).thenReturn(testKeyPair.getVerificationAlgorithm());

        // When
        UserContext userContext = jwtValidator.validateToken(validToken);

        // Then
        assertThat(userContext).isNotNull();
        assertThat(userContext.getUserId()).isEqualTo("test-user-id");
        assertThat(userContext.getEmail()).isEqualTo("test@example.com");
        assertThat(userContext.getRole()).isEqualTo("organizer");
        assertThat(userContext.getCompanyId()).isEqualTo("company-123");
    }

    // Test 3.2: should_extractUserContext_when_validTokenProvided
    @Test
    @DisplayName("should_extractUserContext_when_validTokenProvided")
    void should_extractUserContext_when_validTokenProvided() throws Exception {
        // Given
        String validToken = createValidJwtToken();
        when(jwksProvider.getAlgorithm(any())).thenReturn(testKeyPair.getVerificationAlgorithm());

        // When
        UserContext userContext = jwtValidator.validateToken(validToken);

        // Then
        assertThat(userContext.getUserId()).isEqualTo("test-user-id");
        assertThat(userContext.getEmail()).isEqualTo("test@example.com");
        assertThat(userContext.isEmailVerified()).isTrue();
        assertThat(userContext.getRole()).isEqualTo("organizer");
        assertThat(userContext.getCompanyId()).isEqualTo("company-123");
        assertThat(userContext.getPreferences()).containsEntry("language", "en");
        assertThat(userContext.getPreferences()).containsEntry("theme", "light");
    }

    // Test 3.3: should_rejectExpiredToken_when_tokenExpired
    @Test
    @DisplayName("should_rejectExpiredToken_when_tokenExpired")
    void should_rejectExpiredToken_when_tokenExpired() {
        // Given
        String expiredToken = createExpiredJwtToken();

        // When / Then
        assertThatThrownBy(() -> jwtValidator.validateToken(expiredToken))
            .isInstanceOf(TokenExpiredException.class)
            .hasMessageContaining("Token has expired");
    }

    // Test 3.4: should_refreshToken_when_nearExpiration
    @Test
    @DisplayName("should_refreshToken_when_nearExpiration")
    void should_refreshToken_when_nearExpiration() throws Exception {
        // Given
        String tokenNearExpiration = createTokenNearExpiration();
        // No mocking needed since isTokenNearExpiration only decodes the token

        // When
        boolean shouldRefresh = jwtValidator.isTokenNearExpiration(tokenNearExpiration);

        // Then
        assertThat(shouldRefresh).isTrue();
    }

    @Test
    @DisplayName("should_rejectMalformedJWT_when_invalidTokenProvided")
    void should_rejectMalformedJWT_when_invalidTokenProvided() {
        // Given
        String malformedToken = "invalid.jwt.token";

        // When / Then
        assertThatThrownBy(() -> jwtValidator.validateToken(malformedToken))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("Invalid JWT token format");
    }

    @Test
    @DisplayName("should_rejectTokenWithInvalidSignature_when_signatureVerificationFails")
    void should_rejectTokenWithInvalidSignature_when_signatureVerificationFails() throws Exception {
        // Given
        String tokenWithInvalidSignature = createTokenWithInvalidSignature();
        when(jwksProvider.getAlgorithm(any())).thenThrow(new RuntimeException("Invalid signature"));

        // When / Then
        assertThatThrownBy(() -> jwtValidator.validateToken(tokenWithInvalidSignature))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("Token signature verification failed");
    }

    @Test
    @DisplayName("should_rejectTokenWithWrongAudience_when_audienceDoesNotMatch")
    void should_rejectTokenWithWrongAudience_when_audienceDoesNotMatch() throws Exception {
        // Given
        String tokenWithWrongAudience = createTokenWithWrongAudience();
        when(jwksProvider.getAlgorithm(any())).thenReturn(testKeyPair.getVerificationAlgorithm());

        // When / Then
        assertThatThrownBy(() -> jwtValidator.validateToken(tokenWithWrongAudience))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("audience");
    }

    @Test
    @DisplayName("should_rejectTokenWithWrongIssuer_when_issuerDoesNotMatch")
    void should_rejectTokenWithWrongIssuer_when_issuerDoesNotMatch() throws Exception {
        // Given
        String tokenWithWrongIssuer = createTokenWithWrongIssuer();
        when(jwksProvider.getAlgorithm(any())).thenReturn(testKeyPair.getVerificationAlgorithm());

        // When / Then
        assertThatThrownBy(() -> jwtValidator.validateToken(tokenWithWrongIssuer))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("issuer");
    }

    @Test
    @DisplayName("should_extractAllCustomClaims_when_validTokenProvided")
    void should_extractAllCustomClaims_when_validTokenProvided() throws Exception {
        // Given
        String tokenWithCustomClaims = createTokenWithCustomClaims();
        when(jwksProvider.getAlgorithm(any())).thenReturn(testKeyPair.getVerificationAlgorithm());

        // When
        UserContext userContext = jwtValidator.validateToken(tokenWithCustomClaims);

        // Then
        assertThat(userContext.getRole()).isEqualTo("partner");
        assertThat(userContext.getCompanyId()).isEqualTo("partner-company-456");
        assertThat(userContext.getPreferences()).containsEntry("language", "de");
        assertThat(userContext.getPreferences()).containsEntry("theme", "dark");
        assertThat(userContext.getPreferences()).containsEntry("notifications", "enabled");
    }

    // SECURITY TEST: Critical - Verify Algorithm.none() is explicitly rejected
    @Test
    @DisplayName("should_rejectAlgorithmNone_when_unsignedTokenProvided")
    void should_rejectAlgorithmNone_when_unsignedTokenProvided() throws Exception {
        // Given - Token signed with Algorithm.none() (security vulnerability)
        String unsignedToken = JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("attacker-id")
            .withClaim("email", "attacker@example.com")
            .withArrayClaim("cognito:groups", new String[]{"organizer"})  // Attacker claims admin role
            .withAudience(APP_CLIENT_ID)
            .withIssuer("https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID)
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(Algorithm.none());

        // Mock provider returns Algorithm.none() (simulating attack scenario)
        when(jwksProvider.getAlgorithm(any())).thenReturn(Algorithm.none());

        // When / Then - Validator MUST reject the token
        assertThatThrownBy(() -> jwtValidator.validateToken(unsignedToken))
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("verification failed");
    }

    // Helper methods to create test tokens
    private String createValidJwtToken() {
        return JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("test-user-id")
            .withClaim("email", "test@example.com")
            .withClaim("email_verified", true)
            .withArrayClaim("cognito:groups", new String[]{"organizer"})
            .withClaim("custom:companyId", "company-123")
            .withClaim("custom:preferences", "{\"language\":\"en\",\"theme\":\"light\"}")
            .withAudience(APP_CLIENT_ID)
            .withIssuer("https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID)
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .withIssuedAt(Date.from(Instant.now()))
            .sign(testKeyPair.getAlgorithm());
    }

    private String createExpiredJwtToken() {
        return JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("test-user-id")
            .withClaim("email", "test@example.com")
            .withAudience(APP_CLIENT_ID)
            .withIssuer("https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID)
            .withExpiresAt(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
            .withIssuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
            .sign(testKeyPair.getAlgorithm());
    }

    private String createTokenNearExpiration() {
        return JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("test-user-id")
            .withClaim("email", "test@example.com")
            .withAudience(APP_CLIENT_ID)
            .withIssuer("https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID)
            .withExpiresAt(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
            .withIssuedAt(Date.from(Instant.now().minus(55, ChronoUnit.MINUTES)))
            .sign(testKeyPair.getAlgorithm());
    }

    private String createTokenWithInvalidSignature() {
        return JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("test-user-id")
            .withAudience(APP_CLIENT_ID)
            .withIssuer("https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID)
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(Algorithm.HMAC256("wrong-secret"));
    }

    private String createTokenWithWrongAudience() {
        return JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("test-user-id")
            .withAudience("wrong-audience")
            .withIssuer("https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID)
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(testKeyPair.getAlgorithm());
    }

    private String createTokenWithWrongIssuer() {
        return JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("test-user-id")
            .withAudience(APP_CLIENT_ID)
            .withIssuer("https://wrong-issuer.com")
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(testKeyPair.getAlgorithm());
    }

    private String createTokenWithCustomClaims() {
        return JWT.create()
            .withKeyId(TEST_KEY_ID)
            .withSubject("test-user-id")
            .withClaim("email", "partner@example.com")
            .withClaim("email_verified", true)
            .withArrayClaim("cognito:groups", new String[]{"partner"})
            .withClaim("custom:companyId", "partner-company-456")
            .withClaim("custom:preferences", "{\"language\":\"de\",\"theme\":\"dark\",\"notifications\":\"enabled\"}")
            .withAudience(APP_CLIENT_ID)
            .withIssuer("https://cognito-idp." + REGION + ".amazonaws.com/" + USER_POOL_ID)
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(testKeyPair.getAlgorithm());
    }
}