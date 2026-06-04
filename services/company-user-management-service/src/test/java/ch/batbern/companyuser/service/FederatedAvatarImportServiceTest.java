package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Story 12.12 unit tests:
 * <ul>
 *   <li>SSRF guard + sized-variant normalization of the Google {@code picture} claim
 *       (Task 3 — only {@code googleusercontent.com} hosts may ever be fetched);</li>
 *   <li>AC7 — async executor behaviour: {@code importIfNeeded} hands the fetch to the
 *       injected executor and returns WITHOUT performing any network I/O on the calling
 *       (request) thread.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Story 12.12 — FederatedAvatarImportService unit")
class FederatedAvatarImportServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfilePictureService profilePictureService;
    @Mock
    private HttpClient httpClient;

    /** Captures submitted work WITHOUT running it — proves the request thread never fetches. */
    private final List<Runnable> capturedWork = new ArrayList<>();

    private FederatedAvatarImportService service;

    @BeforeEach
    void setUp() {
        capturedWork.clear();
        service = new FederatedAvatarImportService(
                userRepository, profilePictureService, httpClient, capturedWork::add);
    }

    @Nested
    @DisplayName("normalizeAndValidateGoogleUrl — SSRF guard + variant upgrade")
    class UrlGuard {

        @Test
        @DisplayName("real Google claim with =s96-c → upgraded to =s512-c")
        void should_upgradeSizedVariant_when_googleClaimHasSizeSuffix() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://lh3.googleusercontent.com/a/ACg8ocK-abc123=s96-c"))
                    .isEqualTo("https://lh3.googleusercontent.com/a/ACg8ocK-abc123=s512-c");
        }

        @Test
        @DisplayName("google claim without size suffix → unchanged")
        void should_keepUrl_when_noSizeSuffix() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://lh3.googleusercontent.com/a/photo"))
                    .isEqualTo("https://lh3.googleusercontent.com/a/photo");
        }

        @Test
        @DisplayName("bare googleusercontent.com host → accepted")
        void should_acceptUrl_when_hostIsBareGoogleusercontent() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://googleusercontent.com/a/photo"))
                    .isNotNull();
        }

        @Test
        @DisplayName("non-google host → rejected")
        void should_reject_when_hostIsNotGoogleusercontent() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://evil.example.com/avatar.png")).isNull();
        }

        @Test
        @DisplayName("google-as-suffix-of-attacker-domain → rejected (endsWith dot-check)")
        void should_reject_when_attackerDomainEndsWithGoogleusercontent() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://evilgoogleusercontent.com/x")).isNull();
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://lh3.googleusercontent.com.evil.com/x")).isNull();
        }

        @Test
        @DisplayName("googleusercontent in query/path only → rejected (host is what counts)")
        void should_reject_when_googleusercontentOnlyInQueryOrPath() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://evil.com/steal?cb=lh3.googleusercontent.com")).isNull();
        }

        @Test
        @DisplayName("plain http → rejected (https only)")
        void should_reject_when_schemeIsHttp() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "http://lh3.googleusercontent.com/a/photo")).isNull();
        }

        @Test
        @DisplayName("garbage / non-URL claim → rejected, never throws")
        void should_rejectQuietly_when_claimIsNotAUrl() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl("not a url")).isNull();
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl("")).isNull();
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(null)).isNull();
        }
    }

    @Nested
    @DisplayName("importIfNeeded — async hand-off (AC7: never blocks the request thread)")
    class AsyncHandOff {

        @Test
        @DisplayName("eligible user → attempt marked synchronously, fetch ONLY submitted to executor")
        void should_submitFetchToExecutor_when_importNeeded() {
            User user = User.builder()
                    .username("async.user")
                    .email("async.user@example.ch")
                    .firstName("Async")
                    .lastName("User")
                    .cognitoUserId("async-sub")
                    .build();
            when(userRepository.findByCognitoUserId("async-sub")).thenReturn(Optional.of(user));

            service.importIfNeeded("async-sub", "https://lh3.googleusercontent.com/a/p=s96-c");

            // Attempt was recorded on the calling thread (one attempt ever, even if the
            // process dies before the async fetch runs).
            assertThat(user.getPictureImportAttemptedAt()).isNotNull();
            verify(userRepository).save(user);

            // The fetch was handed to the executor but NOT executed: no network I/O yet.
            assertThat(capturedWork).hasSize(1);
            verifyNoInteractions(httpClient);
            verifyNoInteractions(profilePictureService);
        }

        @Test
        @DisplayName("ineligible (picture already set) → nothing submitted, nothing saved")
        void should_submitNothing_when_pictureAlreadyPresent() {
            User user = User.builder()
                    .username("done.user")
                    .email("done.user@example.ch")
                    .firstName("Done")
                    .lastName("User")
                    .cognitoUserId("done-sub")
                    .profilePictureUrl("https://cdn.batbern.ch/profile-pictures/x.png")
                    .build();
            when(userRepository.findByCognitoUserId("done-sub")).thenReturn(Optional.of(user));

            service.importIfNeeded("done-sub", "https://lh3.googleusercontent.com/a/p");

            assertThat(capturedWork).isEmpty();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("unknown sub (JIT has not created the row) → quiet no-op")
        void should_doNothing_when_userNotFound() {
            when(userRepository.findByCognitoUserId("ghost-sub")).thenReturn(Optional.empty());

            service.importIfNeeded("ghost-sub", "https://lh3.googleusercontent.com/a/p");

            assertThat(capturedWork).isEmpty();
            verify(userRepository, never()).save(any());
        }
    }
}
