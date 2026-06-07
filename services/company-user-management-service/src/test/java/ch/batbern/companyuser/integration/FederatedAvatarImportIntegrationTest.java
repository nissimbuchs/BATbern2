package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 12.12 (AC2/AC3/AC5/AC6/AC7): one-time server-side import of the Google profile
 * picture, keyed off the ID-token {@code picture} claim.
 *
 * <p>Drives real authenticated requests through {@code /api/**} (same pattern as
 * {@link JITProvisioningIntegrationTest}) so BOTH interceptors actually run in their
 * registered order. The avatar fetch is deterministic: {@code TestAwsConfig} supplies a
 * synchronous {@code avatarImportExecutor} and a mock {@code avatarFetchHttpClient}.
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Story 12.12 — one-time federated avatar import integration")
class FederatedAvatarImportIntegrationTest extends AbstractIntegrationTest {

    private static final String PICTURE_CLAIM =
            "https://lh3.googleusercontent.com/a/ACg8ocK-test=s96-c";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private S3Client s3Client;
    @Autowired
    @Qualifier("avatarFetchHttpClient")
    private HttpClient avatarFetchHttpClient;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(s3Client, avatarFetchHttpClient);
    }

    private User seedUser(String sub, String username, String email) {
        return userRepository.save(User.builder()
                .cognitoUserId(sub)
                .username(username)
                .email(email)
                .firstName("Google")
                .lastName("Federated")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build());
    }

    @SuppressWarnings("unchecked")
    private void stubFetchSuccess(byte[] imageBytes, String contentType) throws Exception {
        HttpResponse<byte[]> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("Content-Type", List.of(contentType)), (a, b) -> true));
        when(response.body()).thenReturn(imageBytes);
        when(avatarFetchHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void stubFetchFailure(int statusCode) throws Exception {
        HttpResponse<byte[]> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(avatarFetchHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    private void performAuthenticatedRequest(String sub, String email, String pictureClaim)
            throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt()
                                .jwt(j -> {
                                    j.subject(sub).claim("email", email);
                                    if (pictureClaim != null) {
                                        j.claim("picture", pictureClaim);
                                    }
                                })
                                .authorities(Collections.emptyList())))
                .andExpect(status().isOk());
    }

    // ============================================================
    // AC2 + AC6 — claim + NULL picture → fetched once, stored in OUR S3, cdn URL set
    // ============================================================

    @Test
    @DisplayName("picture claim + NULL picture → S3 put + cdn.batbern.ch URL + attempt recorded")
    void should_importPictureOnce_when_claimPresentAndNoPicture() throws Exception {
        User seeded = seedUser("avatar-sub-1", "google.federated", "google.federated@example.ch");
        assertThat(seeded.getProfilePictureUrl()).isNull();
        assertThat(seeded.getPictureImportAttemptedAt()).isNull();

        stubFetchSuccess(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg");

        performAuthenticatedRequest("avatar-sub-1", "google.federated@example.ch", PICTURE_CLAIM);

        // The image was stored in OUR S3 under the existing profile-pictures key convention.
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getValue().key())
                .startsWith("profile-pictures/")
                .contains("/google.federated/profile-")
                .endsWith(".jpeg");

        // The fetch upgraded the =s96-c sized variant to =s512-c before requesting.
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(avatarFetchHttpClient).send(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().uri().toString())
                .isEqualTo("https://lh3.googleusercontent.com/a/ACg8ocK-test=s512-c");

        // AC6: the stored URL is OUR CloudFront domain, never googleusercontent.
        User updated = userRepository.findByCognitoUserId("avatar-sub-1").orElseThrow();
        assertThat(updated.getProfilePictureUrl())
                .startsWith("https://cdn.batbern.ch/profile-pictures/")
                .doesNotContain("googleusercontent");
        assertThat(updated.getProfilePictureS3Key()).startsWith("profile-pictures/");
        assertThat(updated.getPictureImportAttemptedAt()).isNotNull();
    }

    @Test
    @DisplayName("fresh federated identity → JIT creates row AND avatar imports in the SAME request")
    void should_importPicture_when_freshFederatedIdentityFirstRequest() throws Exception {
        stubFetchSuccess(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}, "image/png");

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt()
                                .jwt(j -> j.subject("avatar-fresh-sub")
                                        .claim("email", "fresh.google@example.ch")
                                        .claim("given_name", "Fresh")
                                        .claim("family_name", "Google")
                                        .claim("picture", PICTURE_CLAIM))
                                .authorities(Collections.emptyList())))
                .andExpect(status().isOk());

        // JIT created the row (interceptor 1), avatar import ran right after (interceptor 2).
        User created = userRepository.findByCognitoUserId("avatar-fresh-sub").orElseThrow();
        assertThat(created.getUsername()).isEqualTo("fresh.google");
        assertThat(created.getProfilePictureUrl()).startsWith("https://cdn.batbern.ch/");
        assertThat(created.getPictureImportAttemptedAt()).isNotNull();
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // ============================================================
    // AC3 — never clobber, never loop
    // ============================================================

    @Test
    @DisplayName("picture claim + EXISTING picture → no-op (never clobber)")
    void should_notImport_when_userAlreadyHasPicture() throws Exception {
        User seeded = seedUser("avatar-sub-2", "has.picture", "has.picture@example.ch");
        seeded.setProfilePictureUrl("https://cdn.batbern.ch/profile-pictures/2025/has.picture/profile-x.png");
        seeded.setProfilePictureS3Key("profile-pictures/2025/has.picture/profile-x.png");
        userRepository.save(seeded);

        performAuthenticatedRequest("avatar-sub-2", "has.picture@example.ch", PICTURE_CLAIM);

        verify(avatarFetchHttpClient, never()).send(any(), any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        User after = userRepository.findByCognitoUserId("avatar-sub-2").orElseThrow();
        assertThat(after.getProfilePictureUrl()).endsWith("profile-x.png");
    }

    @Test
    @DisplayName("picture claim + prior import attempt → no-op (one attempt per user, ever)")
    void should_notImport_when_attemptAlreadyRecorded() throws Exception {
        User seeded = seedUser("avatar-sub-3", "tried.before", "tried.before@example.ch");
        seeded.setPictureImportAttemptedAt(Instant.parse("2026-01-01T00:00:00Z"));
        userRepository.save(seeded);

        performAuthenticatedRequest("avatar-sub-3", "tried.before@example.ch", PICTURE_CLAIM);

        verify(avatarFetchHttpClient, never()).send(any(), any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        User after = userRepository.findByCognitoUserId("avatar-sub-3").orElseThrow();
        // Picture deleted-or-never-imported stays absent; the attempt timestamp is never reset.
        assertThat(after.getProfilePictureUrl()).isNull();
        assertThat(after.getPictureImportAttemptedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("terminal fetch failure (HTTP 403) → attempt recorded, request still 200, no picture set")
    void should_recordAttemptAndStaySilent_when_fetchFails() throws Exception {
        seedUser("avatar-sub-4", "broken.url", "broken.url@example.ch");
        stubFetchFailure(403);

        // The request itself must succeed — broken avatar fetch NEVER fails the API call.
        performAuthenticatedRequest("avatar-sub-4", "broken.url@example.ch", PICTURE_CLAIM);

        User after = userRepository.findByCognitoUserId("avatar-sub-4").orElseThrow();
        assertThat(after.getPictureImportAttemptedAt()).isNotNull();
        assertThat(after.getProfilePictureUrl()).isNull();
        assertThat(after.getProfilePictureS3Key()).isNull();
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // Terminal means terminal: a later request with the same claim does not refetch.
        performAuthenticatedRequest("avatar-sub-4", "broken.url@example.ch", PICTURE_CLAIM);
        verify(avatarFetchHttpClient, Mockito.times(1)).send(any(), any());
    }

    // 12.12 review finding #1: a one-off Google blip must not permanently cost the user
    // their avatar — transient failures (5xx/429, network errors) release the claim so a
    // later federated request retries.
    @Test
    @DisplayName("transient fetch failure (HTTP 503) → claim released, retried + imported on a later request")
    void should_retryOnLaterRequest_when_fetchFailsTransiently() throws Exception {
        seedUser("avatar-sub-7", "transient.blip", "transient.blip@example.ch");
        stubFetchFailure(503);

        performAuthenticatedRequest("avatar-sub-7", "transient.blip@example.ch", PICTURE_CLAIM);

        // The claim was released — no permanent attempt on record, no picture.
        User afterFirst = userRepository.findByCognitoUserId("avatar-sub-7").orElseThrow();
        assertThat(afterFirst.getPictureImportAttemptedAt()).isNull();
        assertThat(afterFirst.getProfilePictureUrl()).isNull();
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // Google recovers → the next federated request retries and imports.
        stubFetchSuccess(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg");
        performAuthenticatedRequest("avatar-sub-7", "transient.blip@example.ch", PICTURE_CLAIM);

        User afterSecond = userRepository.findByCognitoUserId("avatar-sub-7").orElseThrow();
        assertThat(afterSecond.getPictureImportAttemptedAt()).isNotNull();
        assertThat(afterSecond.getProfilePictureUrl()).startsWith("https://cdn.batbern.ch/profile-pictures/");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("non-googleusercontent picture claim → attempt recorded, NO fetch (SSRF guard)")
    void should_refuseFetch_when_claimHostIsNotGoogleusercontent() throws Exception {
        seedUser("avatar-sub-6", "ssrf.attempt", "ssrf.attempt@example.ch");

        performAuthenticatedRequest("avatar-sub-6", "ssrf.attempt@example.ch",
                "https://evil.example.com/steal?cb=googleusercontent.com");

        // The attempt is recorded (one evaluation ever) but no outbound request is made.
        User after = userRepository.findByCognitoUserId("avatar-sub-6").orElseThrow();
        assertThat(after.getPictureImportAttemptedAt()).isNotNull();
        assertThat(after.getProfilePictureUrl()).isNull();
        verify(avatarFetchHttpClient, never()).send(any(), any());
    }

    // ============================================================
    // AC5 — native users unaffected
    // ============================================================

    @Test
    @DisplayName("no picture claim (native sign-in) → no import attempt recorded, nothing fetched")
    void should_doNothing_when_jwtHasNoPictureClaim() throws Exception {
        seedUser("avatar-sub-5", "native.user", "native.user@example.ch");

        performAuthenticatedRequest("avatar-sub-5", "native.user@example.ch", null);

        User after = userRepository.findByCognitoUserId("avatar-sub-5").orElseThrow();
        assertThat(after.getPictureImportAttemptedAt()).isNull();
        assertThat(after.getProfilePictureUrl()).isNull();
        verify(avatarFetchHttpClient, never()).send(any(), any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
