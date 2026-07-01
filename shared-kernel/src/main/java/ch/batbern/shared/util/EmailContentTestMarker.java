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
 * <p>The markers are deliberately long, upper-case, hyphenated tokens that <b>never</b> occur
 * in genuine BATbern content, so a substring match carries effectively zero false-positive risk:
 * <ul>
 *   <li>{@value #PLAYWRIGHT_MARKER} — Playwright event-title token
 *       (web-frontend {@code test-data-factory.ts} {@code EVENT_TITLE_TOKEN}).</li>
 *   <li>{@value #BRUNO_MARKER} — Bruno API-contract suite {@code event_code} prefix.</li>
 * </ul>
 *
 * <p><b>Contract for test authors:</b> any test-created entity whose name/title/code can reach
 * an email MUST carry one of these markers. This is what makes the entity "clearly identifiable"
 * to both the cleanup sweeps and this guard. Pure utility — no Spring, no I/O.
 */
public final class EmailContentTestMarker {

    /** Playwright test-data marker — see web-frontend {@code EVENT_TITLE_TOKEN}. */
    public static final String PLAYWRIGHT_MARKER = "BATPW-E2E";

    /** Bruno API-contract suite event-code prefix. */
    public static final String BRUNO_MARKER = "BRUNO-TEST-";

    private static final List<String> MARKERS = List.of(PLAYWRIGHT_MARKER, BRUNO_MARKER);

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
