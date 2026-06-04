package ch.batbern.companyuser.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared fetch-an-image-from-a-URL pipeline: GET → status check → {@code image/*}
 * content-type check → size cap → content-type→extension mapping.
 *
 * <p>Story 12.12 review (finding #7): this logic previously existed as three hand-rolled
 * copies — {@code UserController.uploadProfilePictureFromUrl}, {@code LogoController
 * .uploadImageFromUrl}, and {@code FederatedAvatarImportService.fetchAndImport} — which
 * had already diverged on the fetch-time size cap (5MB / 10MB / none). All three now call
 * this single helper; the size cap is the only intentional per-caller difference and is a
 * parameter.
 *
 * <p>Failures are reported as {@link ImageFetchException} with a {@link
 * ImageFetchException.Reason} so callers can map them to HTTP responses (controllers) or
 * retry semantics (the federated avatar import). {@link InterruptedException} is re-set on
 * the thread before being wrapped.
 */
public final class ImageUrlFetcher {

    private static final Duration FETCH_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Default client for the admin upload-from-url endpoints (organizer-initiated, so
     * following redirects is acceptable there — unlike the federated avatar import, which
     * supplies its own redirect-refusing client as part of its SSRF guard).
     */
    private static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ImageUrlFetcher() {
    }

    /** A successfully fetched and validated image. */
    public record FetchedImage(byte[] body, String contentType, String extension) {
    }

    /** Fetch failure with enough context for HTTP mapping and retry classification. */
    public static final class ImageFetchException extends Exception {

        public enum Reason {
            /** Non-200 response; {@link #getStatusCode()} carries the upstream status. */
            HTTP_STATUS,
            /** Response Content-Type is not {@code image/*}. */
            NOT_AN_IMAGE,
            /** Body exceeds the caller's size cap. */
            TOO_LARGE,
            /** Network/IO failure (connect, timeout, interrupt). */
            IO
        }

        private final Reason reason;
        private final int statusCode;

        ImageFetchException(Reason reason, int statusCode, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
            this.statusCode = statusCode;
        }

        public Reason getReason() {
            return reason;
        }

        /** Upstream HTTP status for {@link Reason#HTTP_STATUS}, otherwise 0. */
        public int getStatusCode() {
            return statusCode;
        }

        /**
         * Whether a retry could plausibly succeed: network/IO failures and upstream
         * 5xx / 429 are retryable; 3xx/4xx, non-image responses, and oversize bodies
         * are not (the URL itself is the problem).
         */
        public boolean isRetryable() {
            return reason == Reason.IO
                    || (reason == Reason.HTTP_STATUS && (statusCode >= 500 || statusCode == 429));
        }
    }

    /** Fetch with the shared default (redirect-following) client. */
    public static FetchedImage fetch(String url, long maxBytes) throws ImageFetchException {
        return fetch(DEFAULT_CLIENT, url, maxBytes);
    }

    /**
     * Fetch {@code url} with the given client and validate it as an image of at most
     * {@code maxBytes}.
     */
    public static FetchedImage fetch(HttpClient client, String url, long maxBytes)
            throws ImageFetchException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(FETCH_REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ImageFetchException(ImageFetchException.Reason.IO, 0,
                    "Failed to fetch image from URL", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageFetchException(ImageFetchException.Reason.IO, 0,
                    "Interrupted while fetching image from URL", e);
        }

        if (response.statusCode() != 200) {
            throw new ImageFetchException(ImageFetchException.Reason.HTTP_STATUS,
                    response.statusCode(), "Image fetch returned HTTP " + response.statusCode(), null);
        }

        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("application/octet-stream");
        if (!contentType.startsWith("image/")) {
            throw new ImageFetchException(ImageFetchException.Reason.NOT_AN_IMAGE, 0,
                    "URL does not point to an image: " + contentType, null);
        }

        byte[] body = response.body();
        if (body.length > maxBytes) {
            throw new ImageFetchException(ImageFetchException.Reason.TOO_LARGE, 0,
                    "Image too large: " + body.length + " bytes (max " + maxBytes + ")", null);
        }

        return new FetchedImage(body, contentType, extensionFromContentType(contentType));
    }

    /**
     * Map a Content-Type to a file extension ({@code image/svg+xml} → {@code svg};
     * {@code ; charset=...} parameters stripped). Single home for the mapping that was
     * previously copied into both admin controllers and the avatar importer.
     */
    static String extensionFromContentType(String contentType) {
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
