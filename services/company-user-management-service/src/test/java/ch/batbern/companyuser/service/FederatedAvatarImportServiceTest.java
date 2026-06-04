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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
 *       (request) thread;</li>
 *   <li>12.12 review findings #1 + #5 — the attempt is claimed via the atomic
 *       compare-and-set repository method, and transient fetch failures (5xx/IO,
 *       executor rejection) release the claim while terminal ones (4xx) keep it.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Story 12.12 — FederatedAvatarImportService unit")
class FederatedAvatarImportServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

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

    private static User eligibleUser(String username, String sub) {
        return User.builder()
                .id(USER_ID)
                .username(username)
                .email(username + "@example.ch")
                .firstName("Test")
                .lastName("User")
                .cognitoUserId(sub)
                .build();
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

        // 12.12 review finding #9: a size token followed by a query string or fragment
        // previously slipped past the $-anchored pattern and a 96px thumbnail was stored
        // as the permanent original.
        @Test
        @DisplayName("size suffix followed by query string → still upgraded")
        void should_upgradeSizedVariant_when_followedByQueryString() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://lh3.googleusercontent.com/a/ACg8ocK-abc=s96-c?sz=96"))
                    .isEqualTo("https://lh3.googleusercontent.com/a/ACg8ocK-abc=s512-c?sz=96");
        }

        @Test
        @DisplayName("size suffix followed by fragment → still upgraded")
        void should_upgradeSizedVariant_when_followedByFragment() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://lh3.googleusercontent.com/a/ACg8ocK-abc=s96#frag"))
                    .isEqualTo("https://lh3.googleusercontent.com/a/ACg8ocK-abc=s512-c#frag");
        }

        @Test
        @DisplayName("legacy /s96-c/ path-segment sizing → upgraded to /s512-c/")
        void should_upgradePathSegmentVariant_when_legacyUrlShape() {
            assertThat(FederatedAvatarImportService.normalizeAndValidateGoogleUrl(
                    "https://lh3.googleusercontent.com/-AbC/XyZ/s96-c/photo.jpg"))
                    .isEqualTo("https://lh3.googleusercontent.com/-AbC/XyZ/s512-c/photo.jpg");
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
    @DisplayName("importIfNeeded — atomic claim + async hand-off (AC7, findings #1/#5)")
    class AsyncHandOff {

        @Test
        @DisplayName("eligible user → attempt claimed via CAS, fetch ONLY submitted to executor")
        void should_submitFetchToExecutor_when_importNeeded() {
            User user = eligibleUser("async.user", "async-sub");
            when(userRepository.findByCognitoUserId("async-sub")).thenReturn(Optional.of(user));
            when(userRepository.claimPictureImportAttempt(eq(USER_ID), any(Instant.class)))
                    .thenReturn(1);

            service.importIfNeeded("async-sub", "https://lh3.googleusercontent.com/a/p=s96-c");

            // The claim is a single atomic conditional UPDATE — no load-modify-save.
            verify(userRepository).claimPictureImportAttempt(eq(USER_ID), any(Instant.class));
            verify(userRepository, never()).save(any());

            // The fetch was handed to the executor but NOT executed: no network I/O yet.
            assertThat(capturedWork).hasSize(1);
            verifyNoInteractions(httpClient);
            verifyNoInteractions(profilePictureService);
        }

        @Test
        @DisplayName("CAS lost (another request claimed concurrently) → nothing submitted")
        void should_submitNothing_when_claimLostToConcurrentRequest() {
            User user = eligibleUser("racing.user", "racing-sub");
            when(userRepository.findByCognitoUserId("racing-sub")).thenReturn(Optional.of(user));
            when(userRepository.claimPictureImportAttempt(eq(USER_ID), any(Instant.class)))
                    .thenReturn(0);

            service.importIfNeeded("racing-sub", "https://lh3.googleusercontent.com/a/p");

            assertThat(capturedWork).isEmpty();
            verifyNoInteractions(httpClient);
            verifyNoInteractions(profilePictureService);
        }

        @Test
        @DisplayName("executor rejects the fetch → claim released (transient, retry later)")
        void should_releaseClaim_when_executorRejectsFetch() {
            User user = eligibleUser("rejected.user", "rejected-sub");
            when(userRepository.findByCognitoUserId("rejected-sub")).thenReturn(Optional.of(user));
            when(userRepository.claimPictureImportAttempt(eq(USER_ID), any(Instant.class)))
                    .thenReturn(1);

            FederatedAvatarImportService rejecting = new FederatedAvatarImportService(
                    userRepository, profilePictureService, httpClient,
                    task -> {
                        throw new RejectedExecutionException("queue full");
                    });

            rejecting.importIfNeeded("rejected-sub", "https://lh3.googleusercontent.com/a/p");

            verify(userRepository).releasePictureImportAttempt(USER_ID);
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("invalid (non-Google) claim → claim kept, nothing dispatched (terminal)")
        void should_keepClaimAndSubmitNothing_when_claimInvalid() {
            User user = eligibleUser("ssrf.user", "ssrf-sub");
            when(userRepository.findByCognitoUserId("ssrf-sub")).thenReturn(Optional.of(user));
            when(userRepository.claimPictureImportAttempt(eq(USER_ID), any(Instant.class)))
                    .thenReturn(1);

            service.importIfNeeded("ssrf-sub", "https://evil.example.com/avatar.png");

            assertThat(capturedWork).isEmpty();
            verify(userRepository, never()).releasePictureImportAttempt(any());
            verifyNoInteractions(httpClient);
        }

        @Test
        @DisplayName("ineligible (picture already set) → no CAS, nothing submitted")
        void should_submitNothing_when_pictureAlreadyPresent() {
            User user = User.builder()
                    .id(USER_ID)
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
            verify(userRepository, never()).claimPictureImportAttempt(any(), any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("unknown sub (JIT has not created the row) → quiet no-op")
        void should_doNothing_when_userNotFound() {
            when(userRepository.findByCognitoUserId("ghost-sub")).thenReturn(Optional.empty());

            service.importIfNeeded("ghost-sub", "https://lh3.googleusercontent.com/a/p");

            assertThat(capturedWork).isEmpty();
            verify(userRepository, never()).claimPictureImportAttempt(any(), any());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("fetchAndImport — transient vs terminal failure classification (finding #1)")
    class FailureClassification {

        @SuppressWarnings("unchecked")
        private void stubHttpStatus(int statusCode) throws Exception {
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(statusCode);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);
        }

        @Test
        @DisplayName("upstream 503 → transient: claim released for a later retry")
        void should_releaseClaim_when_upstreamReturns5xx() throws Exception {
            stubHttpStatus(503);

            service.fetchAndImport(USER_ID, "blip.user", "https://lh3.googleusercontent.com/a/p");

            verify(userRepository).releasePictureImportAttempt(USER_ID);
            verifyNoInteractions(profilePictureService);
        }

        @Test
        @DisplayName("network IOException → transient: claim released for a later retry")
        void should_releaseClaim_when_networkFails() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any()))
                    .thenThrow(new IOException("connection reset"));

            service.fetchAndImport(USER_ID, "offline.user", "https://lh3.googleusercontent.com/a/p");

            verify(userRepository).releasePictureImportAttempt(USER_ID);
            verifyNoInteractions(profilePictureService);
        }

        @Test
        @DisplayName("upstream 403 → terminal: claim kept, never retried")
        void should_keepClaim_when_upstreamReturns4xx() throws Exception {
            stubHttpStatus(403);

            service.fetchAndImport(USER_ID, "broken.user", "https://lh3.googleusercontent.com/a/p");

            verify(userRepository, never()).releasePictureImportAttempt(any());
            verifyNoInteractions(profilePictureService);
        }

        @Test
        @DisplayName("redirect (302, refused by Redirect.NEVER client) → terminal: claim kept")
        void should_keepClaim_when_upstreamRedirects() throws Exception {
            stubHttpStatus(302);

            service.fetchAndImport(USER_ID, "redirected.user", "https://lh3.googleusercontent.com/a/p");

            verify(userRepository, never()).releasePictureImportAttempt(any());
            verifyNoInteractions(profilePictureService);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("non-image content type → terminal: claim kept")
        void should_keepClaim_when_responseIsNotAnImage() throws Exception {
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.headers()).thenReturn(HttpHeaders.of(
                    Map.of("Content-Type", List.of("text/html")), (a, b) -> true));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            service.fetchAndImport(USER_ID, "html.user", "https://lh3.googleusercontent.com/a/p");

            verify(userRepository, never()).releasePictureImportAttempt(any());
            verifyNoInteractions(profilePictureService);
        }
    }
}
