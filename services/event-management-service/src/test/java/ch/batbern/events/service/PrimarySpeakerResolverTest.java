package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.repository.SessionUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrimarySpeakerResolver — canonical speaker identity for pool rows")
class PrimarySpeakerResolverTest {

    @Mock
    private SessionUserRepository sessionUserRepository;

    @Mock
    private UserApiClient userApiClient;

    private PrimarySpeakerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PrimarySpeakerResolver(sessionUserRepository, userApiClient);
    }

    @Nested
    @DisplayName("resolve(SpeakerPool)")
    class Resolve {

        @Test
        @DisplayName("returns full profile from session_users + UserApiClient when session exists")
        void shouldReturnFullProfile_whenSessionAndUserExist() {
            UUID sessionId = UUID.randomUUID();
            SpeakerPool pool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    // Stale pool columns — deliberately wrong to prove resolver ignores them.
                    .speakerName("Stale Brainstorm Name")
                    .build();

            SessionUser primary = SessionUser.builder()
                    .id(UUID.randomUUID())
                    .session(Session.builder().id(sessionId).build())
                    .username("nissim.buchs.3")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .build();

            UserResponse user = new UserResponse();
            user.setId("nissim.buchs.3");
            user.setEmail("nissim3@elca.example");
            user.setFirstName("Nissim3");
            user.setLastName("Buchs");
            user.setCompanyId("ELCA");

            when(sessionUserRepository.findBySessionIdAndSpeakerRole(
                    sessionId, SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(Optional.of(primary));
            when(userApiClient.getUserByUsername("nissim.buchs.3")).thenReturn(user);

            Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> result = resolver.resolve(pool);

            assertThat(result).isPresent();
            PrimarySpeakerResolver.PrimarySpeakerProfile profile = result.get();
            assertThat(profile.username()).isEqualTo("nissim.buchs.3");
            assertThat(profile.email()).isEqualTo("nissim3@elca.example");
            assertThat(profile.firstName()).isEqualTo("Nissim3");
            assertThat(profile.lastName()).isEqualTo("Buchs");
            assertThat(profile.companyName()).isEqualTo("ELCA");
            assertThat(profile.fullName()).isEqualTo("Nissim3 Buchs");
        }

        @Test
        @DisplayName("returns empty when pool row has no session — pre-session brainstorm rows are not contactable")
        void shouldReturnEmpty_whenNoSession() {
            SpeakerPool pool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .sessionId(null)
                    .build();

            assertThat(resolver.resolve(pool)).isEmpty();
        }

        @Test
        @DisplayName("returns empty when session has no PRIMARY_SPEAKER yet")
        void shouldReturnEmpty_whenNoPrimarySpeakerYet() {
            UUID sessionId = UUID.randomUUID();
            SpeakerPool pool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .build();

            when(sessionUserRepository.findBySessionIdAndSpeakerRole(
                    sessionId, SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(Optional.empty());

            assertThat(resolver.resolve(pool)).isEmpty();
        }

        @Test
        @DisplayName("falls back to SessionUser cache + null email when CUMS times out (UserServiceException)")
        void shouldFallBackToCache_whenCumsUnavailable() {
            UUID sessionId = UUID.randomUUID();
            SpeakerPool pool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .build();

            SessionUser primary = SessionUser.builder()
                    .id(UUID.randomUUID())
                    .session(Session.builder().id(sessionId).build())
                    .username("offline.user")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .speakerFirstName("Cached")
                    .speakerLastName("Identity")
                    .build();

            when(sessionUserRepository.findBySessionIdAndSpeakerRole(
                    sessionId, SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(Optional.of(primary));
            when(userApiClient.getUserByUsername("offline.user"))
                    .thenThrow(new UserServiceException("CUMS unavailable", new RuntimeException()));

            Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> result = resolver.resolve(pool);

            assertThat(result).isPresent();
            PrimarySpeakerResolver.PrimarySpeakerProfile profile = result.get();
            assertThat(profile.username()).isEqualTo("offline.user");
            assertThat(profile.email()).isNull();
            assertThat(profile.firstName()).isEqualTo("Cached");
            assertThat(profile.lastName()).isEqualTo("Identity");
            assertThat(profile.fullName()).isEqualTo("Cached Identity");
        }

        @Test
        @DisplayName("falls back to SessionUser cache when user not found in CUMS (UserNotFoundException)")
        void shouldFallBackToCache_whenUserDeletedInCums() {
            UUID sessionId = UUID.randomUUID();
            SpeakerPool pool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .build();

            SessionUser primary = SessionUser.builder()
                    .id(UUID.randomUUID())
                    .session(Session.builder().id(sessionId).build())
                    .username("ghost.user")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .speakerFirstName("Ghost")
                    .speakerLastName(null)
                    .build();

            when(sessionUserRepository.findBySessionIdAndSpeakerRole(
                    sessionId, SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(Optional.of(primary));
            when(userApiClient.getUserByUsername("ghost.user"))
                    .thenThrow(new UserNotFoundException("not found"));

            Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> result = resolver.resolve(pool);

            assertThat(result).isPresent();
            assertThat(result.get().username()).isEqualTo("ghost.user");
            assertThat(result.get().email()).isNull();
            assertThat(result.get().firstName()).isEqualTo("Ghost");
            assertThat(result.get().fullName()).isEqualTo("Ghost");
        }

        @Test
        @DisplayName("returns empty for a null pool argument — defensive")
        void shouldReturnEmpty_whenPoolIsNull() {
            assertThat(resolver.resolve(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveEmail(SpeakerPool)")
    class ResolveEmail {

        @Test
        @DisplayName("returns email when full profile resolves and email is non-blank")
        void shouldReturnEmail_whenAvailable() {
            UUID sessionId = UUID.randomUUID();
            SpeakerPool pool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .build();
            SessionUser primary = SessionUser.builder()
                    .id(UUID.randomUUID())
                    .session(Session.builder().id(sessionId).build())
                    .username("alice")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .build();
            UserResponse user = new UserResponse();
            user.setEmail("alice@example.com");
            user.setFirstName("Alice");
            user.setLastName("Liddell");

            when(sessionUserRepository.findBySessionIdAndSpeakerRole(
                    sessionId, SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(Optional.of(primary));
            when(userApiClient.getUserByUsername("alice")).thenReturn(user);

            assertThat(resolver.resolveEmail(pool)).contains("alice@example.com");
        }

        @Test
        @DisplayName("returns empty on degraded resolution where email is null")
        void shouldReturnEmpty_whenEmailMissing() {
            UUID sessionId = UUID.randomUUID();
            SpeakerPool pool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .build();
            SessionUser primary = SessionUser.builder()
                    .id(UUID.randomUUID())
                    .session(Session.builder().id(sessionId).build())
                    .username("offline.user")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .build();

            when(sessionUserRepository.findBySessionIdAndSpeakerRole(
                    sessionId, SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(Optional.of(primary));
            when(userApiClient.getUserByUsername("offline.user"))
                    .thenThrow(new UserServiceException("CUMS unavailable", new RuntimeException()));

            assertThat(resolver.resolveEmail(pool)).isEmpty();
        }
    }
}
