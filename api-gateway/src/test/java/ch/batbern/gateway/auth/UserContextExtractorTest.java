package ch.batbern.gateway.auth;

import ch.batbern.gateway.auth.model.UserContext;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextExtractorTest {

    private UserContextExtractor extractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        extractor = new UserContextExtractor(objectMapper);
    }

    @Test
    @DisplayName("should_extractUserContext_when_tokenContainsAllRequiredClaims")
    void should_extractUserContext_when_tokenContainsAllRequiredClaims() {
        // Given
        String token = JWT.create()
            .withSubject("user-123")
            .withClaim("email", "user@example.com")
            .withClaim("email_verified", true)
            .withClaim("custom:role", "organizer")
            .withClaim("custom:companyId", "company-456")
            .withClaim("custom:preferences", "{\"language\":\"en\",\"theme\":\"light\"}")
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(Algorithm.none());

        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        UserContext userContext = extractor.extractUserContext(decodedJWT);

        // Then
        assertThat(userContext).isNotNull();
        assertThat(userContext.getUserId()).isEqualTo("user-123");
        assertThat(userContext.getEmail()).isEqualTo("user@example.com");
        assertThat(userContext.isEmailVerified()).isTrue();
        assertThat(userContext.getRole()).isEqualTo("organizer");
        assertThat(userContext.getCompanyId()).isEqualTo("company-456");
        assertThat(userContext.getPreferences()).containsEntry("language", "en");
        assertThat(userContext.getPreferences()).containsEntry("theme", "light");
    }

    @Test
    @DisplayName("should_handleMissingCustomClaims_when_tokenHasMinimalClaims")
    void should_handleMissingCustomClaims_when_tokenHasMinimalClaims() {
        // Given
        String token = JWT.create()
            .withSubject("user-123")
            .withClaim("email", "user@example.com")
            .withClaim("email_verified", false)
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(Algorithm.none());

        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        UserContext userContext = extractor.extractUserContext(decodedJWT);

        // Then
        assertThat(userContext).isNotNull();
        assertThat(userContext.getUserId()).isEqualTo("user-123");
        assertThat(userContext.getEmail()).isEqualTo("user@example.com");
        assertThat(userContext.isEmailVerified()).isFalse();
        assertThat(userContext.getRole()).isNull();
        assertThat(userContext.getCompanyId()).isNull();
        assertThat(userContext.getPreferences()).isEmpty();
    }

    @Test
    @DisplayName("should_parseComplexPreferences_when_jsonStringProvided")
    void should_parseComplexPreferences_when_jsonStringProvided() {
        // Given
        String complexPreferences = "{\"language\":\"de\",\"theme\":\"dark\",\"notifications\":{\"email\":true,\"sms\":false},\"timezone\":\"Europe/Zurich\"}";
        String token = JWT.create()
            .withSubject("user-123")
            .withClaim("email", "user@example.com")
            .withClaim("custom:preferences", complexPreferences)
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(Algorithm.none());

        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        UserContext userContext = extractor.extractUserContext(decodedJWT);

        // Then
        assertThat(userContext.getPreferences()).containsEntry("language", "de");
        assertThat(userContext.getPreferences()).containsEntry("theme", "dark");
        assertThat(userContext.getPreferences()).containsKey("notifications");
        assertThat(userContext.getPreferences()).containsEntry("timezone", "Europe/Zurich");
    }

    @Test
    @DisplayName("should_handleInvalidPreferencesJson_when_malformedJsonProvided")
    void should_handleInvalidPreferencesJson_when_malformedJsonProvided() {
        // Given
        String token = JWT.create()
            .withSubject("user-123")
            .withClaim("email", "user@example.com")
            .withClaim("custom:preferences", "invalid-json-{")
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(Algorithm.none());

        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        UserContext userContext = extractor.extractUserContext(decodedJWT);

        // Then
        assertThat(userContext).isNotNull();
        assertThat(userContext.getPreferences()).isEmpty();
    }

    @Test
    @DisplayName("should_extractAllRoles_when_multipleRolesProvided")
    void should_extractAllRoles_when_multipleRolesProvided() {
        // Given - Simulating a user with multiple roles (edge case)
        String token = JWT.create()
            .withSubject("user-123")
            .withClaim("email", "user@example.com")
            .withClaim("custom:role", "organizer")
            .withArrayClaim("custom:additionalRoles", new String[]{"speaker", "partner"})
            .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
            .sign(Algorithm.none());

        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        UserContext userContext = extractor.extractUserContext(decodedJWT);

        // Then
        assertThat(userContext.getRole()).isEqualTo("organizer");
        assertThat(userContext.getAdditionalRoles()).containsExactly("speaker", "partner");
    }

    @Test
    @DisplayName("should_extractTokenMetadata_when_tokenContainsIssuedAtAndExpiry")
    void should_extractTokenMetadata_when_tokenContainsIssuedAtAndExpiry() {
        // Given
        Instant issuedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

        String token = JWT.create()
            .withSubject("user-123")
            .withClaim("email", "user@example.com")
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(Algorithm.none());

        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        UserContext userContext = extractor.extractUserContext(decodedJWT);

        // Then
        assertThat(userContext.getIssuedAt()).isNotNull();
        assertThat(userContext.getExpiresAt()).isNotNull();
        assertThat(userContext.getIssuedAt()).isBefore(userContext.getExpiresAt());
    }
}