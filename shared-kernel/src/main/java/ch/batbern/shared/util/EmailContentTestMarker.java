package ch.batbern.shared.util;

import java.util.List;
import java.util.Locale;

/**
 * Recogniser for the canonical <b>test-content markers</b> that the automated test suites
 * stamp into the names / titles / codes of the entities they create. Those markers surface
 * verbatim in any email rendered from that data (a newsletter subject/body includes the
 * event title, a speaker invite includes the event name, …), so matching them at the SES
 * send boundary lets {@code EmailService} refuse to deliver a test-generated email.
 *
 * <p>This is the <b>second wall of defence</b> added after the 2026-07-01 incident, where a
 * Playwright spec ({@code event-newsletter-send.spec.ts}) tagged {@code @gate} ran against
 * staging (= production) and mailed ~1,050 subscribers three real "BATPW-E2E" test
 * newsletters. The first wall (Playwright tag/env guards) stops the test from running there;
 * this wall stops the <i>email itself</i> from leaving even if some future test slips through.
 *
 * <p>The markers are deliberately long, distinctive tokens that <b>never</b> occur in genuine
 * BATbern content, so a case-insensitive substring match carries effectively zero false-positive
 * risk. Matching is case-insensitive, so each token also subsumes its lower-case / suffixed
 * variants — {@value #BRUNO_MARKER} covers {@code bruno-test-session-}, {@code bruno-test-topic-},
 * {@code bruno-test-portal-}, the additional-email prefix {@code bruno-test-}, …; and
 * {@value #PLAYWRIGHT_MARKER} covers {@code BATPW-E2E-TPL-} / {@code batpw-e2e-tpl-}:
 * <ul>
 *   <li>{@value #PLAYWRIGHT_MARKER} — Playwright event/session-title token
 *       (web-frontend {@code test-data-factory.ts} {@code EVENT_TITLE_TOKEN}).</li>
 *   <li>{@value #BRUNO_MARKER} — Bruno {@code event_code} prefix + every {@code bruno-test-*}
 *       slug/topic/email prefix.</li>
 *   <li>{@value #BRUNO_COMPANY_MARKER} — Bruno/Playwright company-name prefix.</li>
 *   <li>{@value #BRUNO_USER_MARKER} — Bruno/Playwright username stem (firstName "Bruno" +
 *       lastName "Test" → username {@code bruno.test[.N]}).</li>
 * </ul>
 *
 * <p><b>Deliberately NOT matched here</b> (too short / too generic → false-positive risk):
 * the partner company-name prefix {@code brtest}, the bare human names {@code "Bruno"} / {@code "Test"},
 * and the reserved test-event number range ({@code >= 10000}). Entities whose only test identity is
 * one of those (e.g. a partner, a test speaker's display name) are instead caught by the
 * <b>recipient-domain guard</b> ({@code ReservedEmailDomain} — their email is on {@code .invalid} /
 * {@code .test} / {@code .local} / example.com). See ADR-015 for the full two-guard contract.
 *
 * <p><b>Contract for test authors (ADR-015):</b> any test-created entity whose name/title/code can
 * render into an email MUST carry one of these markers, AND/OR the email recipient MUST be on a
 * reserved domain — so every test email is caught by at least one guard. Pure utility — no Spring,
 * no I/O.
 */
public final class EmailContentTestMarker {

    /** Playwright test-data marker — see web-frontend {@code EVENT_TITLE_TOKEN}. */
    public static final String PLAYWRIGHT_MARKER = "BATPW-E2E";

    /** Bruno event-code prefix; case-insensitively subsumes every {@code bruno-test-*} prefix. */
    public static final String BRUNO_MARKER = "BRUNO-TEST-";

    /** Bruno / Playwright company-name prefix (e.g. {@code BRUNOTESTCO1719878234512}). */
    public static final String BRUNO_COMPANY_MARKER = "BRUNOTESTCO";

    /** Bruno / Playwright username stem (firstName "Bruno" + lastName "Test" → {@code bruno.test[.N]}). */
    public static final String BRUNO_USER_MARKER = "bruno.test";

    // Stored upper-cased so matching is case-insensitive regardless of each literal's own case
    // (BRUNO_USER_MARKER "bruno.test" is intentionally lower-case to mirror the username style).
    private static final List<String> MARKERS = List.of(
            PLAYWRIGHT_MARKER.toUpperCase(Locale.ROOT),
            BRUNO_MARKER.toUpperCase(Locale.ROOT),
            BRUNO_COMPANY_MARKER.toUpperCase(Locale.ROOT),
            BRUNO_USER_MARKER.toUpperCase(Locale.ROOT));

    private EmailContentTestMarker() {
        // utility class
    }

    /**
     * @param contents any number of email-content fragments (subject, body, title, code, …);
     *                 null fragments are ignored.
     * @return true if <em>any</em> fragment contains a known test marker (case-insensitive).
     */
    public static boolean containsMarker(String... contents) {
        if (contents == null) {
            return false;
        }
        for (String content : contents) {
            if (content == null) {
                continue;
            }
            String upper = content.toUpperCase(Locale.ROOT);
            for (String marker : MARKERS) {
                if (upper.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Human-readable list of the recognised markers, for log/error messages. */
    public static String describe() {
        return String.join(", ", MARKERS);
    }
}
