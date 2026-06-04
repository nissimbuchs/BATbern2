package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.ImageUrlFetcher.FetchedImage;
import ch.batbern.companyuser.service.ImageUrlFetcher.ImageFetchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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
 * <p><b>One-terminal-attempt semantics (AC3, refined by the 12.12 review):</b> the attempt
 * is claimed with an atomic conditional UPDATE ({@link
 * UserRepository#claimPictureImportAttempt}) BEFORE the fetch is dispatched, so concurrent
 * first requests cannot double-dispatch. A <i>terminal</i> outcome (success, upstream
 * 3xx/4xx, non-image response, oversize, SSRF-rejected claim) keeps the claim forever —
 * no clobbering user uploads, no re-import-after-delete loops, no repeated fetches of
 * broken Google URLs. A <i>transient</i> failure (upstream 5xx/429, network timeout,
 * executor rejection) releases the claim so a later federated request retries — a one-off
 * Google blip must not permanently cost the user their avatar.
 *
 * <p><b>Latency budget (AC3):</b> the network fetch runs on a dedicated small executor
 * ({@code avatarImportExecutor}); the triggering request thread only pays for one
 * conditional UPDATE (and no extra SELECT when the JIT interceptor already resolved the
 * user — see {@code FederatedAvatarImportInterceptor}).
 *
 * <p><b>SSRF guard:</b> the claim is attacker-influenceable in principle, so only
 * {@code https} URLs whose host is exactly {@code googleusercontent.com} or a subdomain
 * of it are ever fetched, and the injected {@code avatarFetchHttpClient} refuses
 * redirects ({@code Redirect.NEVER}) so the validated host cannot 3xx the fetch onto a
 * different one.
 */
@Service
@Slf4j
public class FederatedAvatarImportService {

    /**
     * Google sizes avatar URLs with an {@code =s96-c} style suffix; request a larger
     * variant before fetching so the stored original is not a 96px thumbnail. The
     * lookahead also matches a size token followed by a query string or fragment, and
     * {@link #PATH_SIZED_VARIANT_PATTERN} covers the legacy path-segment form
     * ({@code …/s96-c/photo.jpg}) — both shapes previously slipped through and stored a
     * 96px thumbnail as the permanent original (12.12 review, finding #9).
     */
    private static final String SIZED_VARIANT_PATTERN = "=s\\d+(-c)?(?=[?#]|$)";
    private static final String PATH_SIZED_VARIANT_PATTERN = "/s\\d+(-c)?/";
    private static final String UPGRADED_VARIANT = "=s512-c";
    private static final String UPGRADED_PATH_VARIANT = "/s512-c/";

    /** Same cap ProfilePictureService enforces; checked at fetch time to avoid buffering more. */
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

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
     * Resolve the user by Cognito sub and delegate to {@link #importIfNeeded(User, String)}.
     * Fallback path for requests where the JIT interceptor did not stash the resolved user
     * (12.12 review, finding #6 — the stashed user avoids a duplicate per-request SELECT).
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
        importIfNeeded(maybeUser.get(), pictureClaim);
    }

    /**
     * Import the Google avatar for this user if — and only if — they have no profile
     * picture and no import attempt is on record. Called per request by
     * {@code FederatedAvatarImportInterceptor}; the in-memory guard exits with zero DB
     * cost on every request except the very first eligible one, and the atomic claim
     * guarantees a single dispatch even under concurrent first requests.
     *
     * @param user         the resolved user row (may be the JIT interceptor's instance)
     * @param pictureClaim the raw {@code picture} claim value
     */
    public void importIfNeeded(User user, String pictureClaim) {
        if (user.getProfilePictureUrl() != null || user.getPictureImportAttemptedAt() != null) {
            return;
        }

        // Atomic compare-and-set BEFORE dispatching (and before validating the URL):
        // the database serialises concurrent first requests — exactly one wins. The
        // UPDATE runs in its own transaction and commits immediately, so async-thread
        // reads cannot resurrect a NULL timestamp.
        int claimed = userRepository.claimPictureImportAttempt(user.getId(), Instant.now());
        if (claimed == 0) {
            return; // another request (or instance) holds the claim
        }

        String fetchUrl = normalizeAndValidateGoogleUrl(pictureClaim);
        if (fetchUrl == null) {
            // Terminal: the claim itself is unusable — keep the claim, never refetch.
            log.warn("Refusing federated avatar fetch for user {} — picture claim is not a "
                    + "googleusercontent.com https URL", user.getUsername());
            return;
        }

        UUID userId = user.getId();
        String username = user.getUsername();
        try {
            avatarImportExecutor.execute(() -> fetchAndImport(userId, username, fetchUrl));
        } catch (RejectedExecutionException e) {
            // Transient: the fetch never started — release the claim so a later
            // federated request retries.
            userRepository.releasePictureImportAttempt(userId);
            log.warn("Avatar import executor rejected the fetch for user {} — claim released, "
                    + "will retry on a later request", username, e);
        }
    }

    /**
     * Fetch the avatar via {@link ImageUrlFetcher} and store it through
     * {@link ProfilePictureService} (extension + 5MB validation, S3 put, CloudFront URL
     * update). Runs on {@code avatarImportExecutor}. Transient fetch failures release the
     * claim for a later retry; everything else is terminal and logged.
     */
    void fetchAndImport(UUID userId, String username, String url) {
        try {
            FetchedImage image = ImageUrlFetcher.fetch(avatarFetchHttpClient, url, MAX_AVATAR_BYTES);

            // Never clobber (AC3): re-check just before the write — the user may have
            // uploaded a picture between the claim and this async execution.
            Optional<User> current = userRepository.findByUsername(username);
            if (current.isEmpty() || current.get().getProfilePictureUrl() != null) {
                log.info("Skipping federated avatar store for user {} — picture appeared "
                        + "since the import was dispatched", username);
                return;
            }

            String cloudFrontUrl = profilePictureService.uploadProfilePictureDirectly(
                    username, image.body(), "google-avatar." + image.extension(), image.contentType());

            log.info("Imported Google avatar for user {} -> {}", username, cloudFrontUrl);

        } catch (ImageFetchException e) {
            if (e.isRetryable()) {
                userRepository.releasePictureImportAttempt(userId);
                log.warn("Transient federated avatar fetch failure for user {} ({}) — claim "
                        + "released, will retry on a later request", username, e.getMessage());
            } else {
                // Terminal: 3xx (redirects are refused by design), 4xx, non-image,
                // oversize — the URL itself is the problem, never refetch.
                log.warn("Federated avatar fetch for user {} failed terminally ({}) — not retrying",
                        username, e.getMessage());
            }
        } catch (Exception e) {
            // Validation failures from ProfilePictureService (InvalidFileTypeException,
            // FileSizeExceededException) and anything unexpected — terminal by design.
            log.warn("Federated avatar import failed for user {} — not retrying", username, e);
        }
    }

    /**
     * SSRF guard + variant upgrade. Returns the URL to fetch, or {@code null} when the
     * claim must not be fetched. Accepts only {@code https} URLs on
     * {@code googleusercontent.com} (or a subdomain); upgrades an {@code =s96-c} style
     * size suffix (also when followed by a query/fragment) or a legacy {@code /s96-c/}
     * path segment to the 512px variant. Never throws.
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
        String url = claim.trim();
        String upgraded = url.replaceFirst(SIZED_VARIANT_PATTERN, UPGRADED_VARIANT);
        if (upgraded.equals(url)) {
            upgraded = url.replaceFirst(PATH_SIZED_VARIANT_PATTERN, UPGRADED_PATH_VARIANT);
        }
        return upgraded;
    }
}
