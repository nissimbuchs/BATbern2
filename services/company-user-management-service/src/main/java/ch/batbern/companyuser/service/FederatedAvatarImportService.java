package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Story 12.12: one-time import of the Google profile picture on federated sign-in.
 *
 * <p>Google's OIDC {@code picture} claim carries a {@code googleusercontent.com} photo URL
 * on every federated request (once the Cognito IdP mapping of AC1 is deployed). Hotlinking
 * it would be wrong — those URLs rotate/expire — so this service fetches the photo ONCE,
 * stores it in our own S3 via the existing {@link ProfilePictureService} machinery
 * (content-type + 5MB validation, {@code profile-pictures/{year}/{username}/} key
 * convention, CloudFront URL), and never touches it again.
 *
 * <p><b>One-attempt-ever semantics (AC3):</b> {@code picture_import_attempted_at} is set
 * synchronously BEFORE the fetch is dispatched — success or failure, the import is never
 * retried. This avoids clobbering user uploads, re-import-after-delete loops, and repeated
 * fetches of broken Google URLs. A richer "sync from Google" feature can supersede it.
 *
 * <p><b>Latency budget (AC3):</b> the network fetch runs on a dedicated small executor
 * ({@code avatarImportExecutor}); the triggering request thread only pays for one indexed
 * SELECT + (once ever) one UPDATE.
 *
 * <p><b>SSRF guard:</b> the claim is attacker-influenceable in principle, so only
 * {@code https} URLs whose host is exactly {@code googleusercontent.com} or a subdomain
 * of it are ever fetched.
 */
@Service
@Slf4j
public class FederatedAvatarImportService {

    /**
     * Google sizes avatar URLs with an {@code =s96-c} style suffix; request a larger
     * variant before fetching so the stored original is not a 96px thumbnail.
     */
    private static final String SIZED_VARIANT_PATTERN = "=s\\d+(-c)?$";
    private static final String UPGRADED_VARIANT = "=s512-c";

    private static final Duration FETCH_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final ProfilePictureService profilePictureService;
    private final HttpClient avatarFetchHttpClient;
    private final Executor avatarImportExecutor;

    public FederatedAvatarImportService(
            UserRepository userRepository,
            ProfilePictureService profilePictureService,
            HttpClient avatarFetchHttpClient,
            @Qualifier("avatarImportExecutor") Executor avatarImportExecutor) {
        this.userRepository = userRepository;
        this.profilePictureService = profilePictureService;
        this.avatarFetchHttpClient = avatarFetchHttpClient;
        this.avatarImportExecutor = avatarImportExecutor;
    }

    /**
     * Import the Google avatar for this identity if — and only if — the user exists,
     * has no profile picture, and no import was ever attempted. Called per request by
     * {@code FederatedAvatarImportInterceptor}; exits in one indexed SELECT when the
     * guards fail, which is every request except the very first eligible one.
     *
     * @param cognitoUserId the JWT subject ({@code sub})
     * @param pictureClaim  the raw {@code picture} claim value
     */
    public void importIfNeeded(String cognitoUserId, String pictureClaim) {
        Optional<User> maybeUser = userRepository.findByCognitoUserId(cognitoUserId);
        if (maybeUser.isEmpty()) {
            // JIT runs before this interceptor, so this only happens when JIT itself
            // failed — stay quiet and let a later request try again.
            return;
        }

        User user = maybeUser.get();
        if (user.getProfilePictureUrl() != null || user.getPictureImportAttemptedAt() != null) {
            return;
        }

        // Mark the attempt BEFORE dispatching the fetch (and before validating the URL):
        // success OR failure, there is exactly one attempt per user, ever. The save commits
        // immediately (no surrounding transaction in the interceptor path), so concurrent
        // requests and async-thread snapshots cannot resurrect a NULL timestamp.
        user.setPictureImportAttemptedAt(Instant.now());
        userRepository.save(user);

        String fetchUrl = normalizeAndValidateGoogleUrl(pictureClaim);
        if (fetchUrl == null) {
            log.warn("Refusing federated avatar fetch for user {} — picture claim is not a "
                    + "googleusercontent.com https URL", user.getUsername());
            return;
        }

        String username = user.getUsername();
        avatarImportExecutor.execute(() -> fetchAndImport(username, fetchUrl));
    }

    /**
     * Fetch the avatar and store it through {@link ProfilePictureService} (which performs
     * the content-type/extension + 5MB validation and the S3 put + CloudFront URL update).
     * Runs on {@code avatarImportExecutor}; every failure is logged and swallowed — the
     * attempt was already recorded, there is no retry.
     */
    void fetchAndImport(String username, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(FETCH_REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<byte[]> response =
                    avatarFetchHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.warn("Federated avatar fetch for user {} returned HTTP {} — not retrying",
                        username, response.statusCode());
                return;
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("application/octet-stream");
            if (!contentType.startsWith("image/")) {
                log.warn("Federated avatar URL for user {} is not an image ({}) — not retrying",
                        username, contentType);
                return;
            }

            // Never clobber (AC3): re-check just before the write — the user may have
            // uploaded a picture between the mark and this async execution.
            Optional<User> current = userRepository.findByUsername(username);
            if (current.isEmpty() || current.get().getProfilePictureUrl() != null) {
                log.info("Skipping federated avatar store for user {} — picture appeared "
                        + "since the import was dispatched", username);
                return;
            }

            String extension = extensionFromContentType(contentType);
            String cloudFrontUrl = profilePictureService.uploadProfilePictureDirectly(
                    username, response.body(), "google-avatar." + extension, contentType);

            log.info("Imported Google avatar for user {} -> {}", username, cloudFrontUrl);

        } catch (Exception e) {
            // Includes oversize (FileSizeExceededException) and bad type
            // (InvalidFileTypeException) from ProfilePictureService — the attempt is
            // already recorded, so these are terminal by design.
            log.warn("Federated avatar import failed for user {} — one attempt ever, not retrying",
                    username, e);
        }
    }

    /**
     * SSRF guard + variant upgrade. Returns the URL to fetch, or {@code null} when the
     * claim must not be fetched. Accepts only {@code https} URLs on
     * {@code googleusercontent.com} (or a subdomain); upgrades a trailing {@code =s96-c}
     * style size suffix to {@code =s512-c}. Never throws.
     */
    static String normalizeAndValidateGoogleUrl(String claim) {
        if (claim == null || claim.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(claim.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!"https".equals(uri.getScheme())) {
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!host.equals("googleusercontent.com") && !host.endsWith(".googleusercontent.com")) {
            return null;
        }
        return claim.trim().replaceFirst(SIZED_VARIANT_PATTERN, UPGRADED_VARIANT);
    }

    /**
     * Map the response Content-Type to a file extension the existing
     * {@link ProfilePictureService} validation accepts (png/jpg/jpeg/svg). Mirrors the
     * admin upload-from-url endpoint in {@code UserController}.
     */
    private static String extensionFromContentType(String contentType) {
        String extension = contentType.substring(contentType.indexOf('/') + 1);
        if (extension.contains(";")) {
            extension = extension.substring(0, extension.indexOf(';'));
        }
        if (extension.equals("svg+xml")) {
            extension = "svg";
        }
        return extension;
    }
}
