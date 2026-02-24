package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.JwtConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerMagicLoginController (Story 9.1).
 *
 * Tests the POST /api/v1/auth/speaker-magic-login endpoint which:
 * - Validates RS256-signed JWTs from magic link URLs
 * - Issues opaque VIEW session tokens for the existing dashboard flow
 * - Sets an HTTP-only cookie containing the JWT
 * - Returns 401 with a German-language message for invalid/expired JWTs
 */
@DisplayName("SpeakerMagicLoginController - POST /api/v1/auth/speaker-magic-login")
class SpeakerMagicLoginControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;
    @Autowired
    private JwtConfig jwtConfig;

    private SpeakerPool speakerPool;
    private Event event;

    @BeforeEach
    void setUp() {
        // Clean up in FK order
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        long ts = System.currentTimeMillis();

        event = Event.builder()
                .eventCode("bat-magic-login-" + ts)
                .eventNumber((int) (ts % 100_000))
                .title("BATbern Magic Login Test " + ts)
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        event = eventRepository.save(event);

        speakerPool = SpeakerPool.builder()
                .eventId(event.getId())
                .speakerName("Jane Doe")
                .company("Tech Corp AG")
                .email("jane@example.com")
                .status(SpeakerWorkflowState.INVITED)
                .invitedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speakerPool = speakerPoolRepository.save(speakerPool);
    }

    @Test
    @DisplayName("should return 200, set HTTP-only cookie, and return sessionToken when valid JWT provided")
    void should_return200AndSetCookie_when_validJwtProvided() throws Exception {
        // Arrange - generate a valid JWT using the application's cached key pair
        String jwt = jwtConfig.buildJwtForTest(speakerPool);

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/speaker-magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jwtToken", jwt))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakerPoolId").value(speakerPool.getId().toString()))
                .andExpect(jsonPath("$.speakerName").value("Jane Doe"))
                .andExpect(jsonPath("$.eventCode").value(event.getEventCode()))
                .andExpect(jsonPath("$.eventTitle").value(event.getTitle()))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(result -> {
                    String cookie = result.getResponse().getHeader("Set-Cookie");
                    assertThat(cookie).contains("speaker_jwt=");
                    assertThat(cookie).contains("HttpOnly");
                    assertThat(cookie).contains("Max-Age=2592000");
                    assertThat(cookie).contains("Path=/");
                });
    }

    @Test
    @DisplayName("should return 401 when JWT is expired")
    void should_return401_when_expiredJwtProvided() throws Exception {
        // Arrange - generate an expired JWT using the application's key pair
        KeyPair keyPair = jwtConfig.getKeyPair();
        String expiredJwt = Jwts.builder()
                .subject(speakerPool.getId().toString())
                .issuer("batbern")
                .claim("roles", List.of("SPEAKER"))
                .claim("speakerPoolId", speakerPool.getId().toString())
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/speaker-magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jwtToken", expiredJwt))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("should return 401 when JWT has an invalid (wrong key) signature")
    void should_return401_when_invalidSignatureJwtProvided() throws Exception {
        // Arrange - JWT signed with a completely different RSA key pair
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair wrongKey = gen.generateKeyPair();
        String wrongJwt = Jwts.builder()
                .subject(speakerPool.getId().toString())
                .issuer("batbern")
                .claim("roles", List.of("SPEAKER"))
                .claim("speakerPoolId", speakerPool.getId().toString())
                .expiration(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))
                .signWith(wrongKey.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/speaker-magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jwtToken", wrongJwt))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("should return 401 when JWT is missing SPEAKER role claim")
    void should_return401_when_missingRoleClaim() throws Exception {
        // Arrange - JWT without the roles claim
        KeyPair keyPair = jwtConfig.getKeyPair();
        String noRoleJwt = Jwts.builder()
                .subject(speakerPool.getId().toString())
                .issuer("batbern")
                .claim("speakerPoolId", speakerPool.getId().toString())
                // deliberately omitting roles claim
                .expiration(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/speaker-magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("jwtToken", noRoleJwt))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("should return 400 when request body is missing jwtToken")
    void should_return400_when_jwtTokenMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/speaker-magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }
}
