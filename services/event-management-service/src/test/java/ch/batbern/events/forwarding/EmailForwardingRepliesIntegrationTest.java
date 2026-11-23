package ch.batbern.events.forwarding;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.EmailForwardingLog;
import ch.batbern.events.domain.EmailForwardingOutcome;
import ch.batbern.events.forwarding.EmailForwardingService.Result;
import ch.batbern.events.repository.EmailForwardingLogRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.mail.SesRecorder.RecordedMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 16.0 AC3/AC4 (#1160) against real PostgreSQL: an unrecognized {@code replies@} mail is
 * forwarded to the organizers and logged; an out-of-office reply is logged as dropped (V131).
 * Fixtures are synthetic ({@code src/test/resources/mail/*.eml}, SES simulator addresses). SES is
 * the {@link SesRecorder} and CUMS the {@link CumsContractHarness} behind the real
 * {@code UserApiClientImpl} (17.0 deferred work): nothing is sent.
 *
 * <p>Also the size regression of 2026-11-22: a 10.9 MB mail to {@code ok@} failed for all 12
 * organizers because SES API v1 refuses a message over 10 MB. The recorder enforces the SES API v2
 * cap (40 MB), so an 11 MB mail must now reach every organizer.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Unrecognized replies@ mail (PostgreSQL)")
class EmailForwardingRepliesIntegrationTest extends AbstractIntegrationTest {

    private static final String KEY_PREFIX = "emails/it-replies-";
    private static final String ORGANIZER = "success+organizer@simulator.amazonses.com";
    private static final String ORGANIZER_USERNAME = "replies.organizer";

    @Autowired
    private EmailForwardingService service;
    @Autowired
    private EmailForwardingLogRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;

    @BeforeEach
    void organizers() {
        sesRecorder.reset();
        cums.usersWithRole("ORGANIZER", ORGANIZER_USERNAME);
        cums.user(ORGANIZER_USERNAME, "Replies", "Organizer", ORGANIZER);
    }

    @AfterEach
    void cleanUp() {
        cums.reset();
        jdbcTemplate.update("DELETE FROM email_forwarding_log WHERE s3_key LIKE 'emails/it-replies-%'");
    }

    @Test
    @DisplayName("should_forwardToOrganizersAndLog_when_humanReplyMatchesNoKeyword")
    void should_forwardToOrganizersAndLog_when_humanReplyMatchesNoKeyword() throws Exception {
        String key = KEY_PREFIX + System.nanoTime();

        Result result = service.forwardUnrecognizedReply(key, fixture("mail/human-reply.eml"));

        assertThat(result).isEqualTo(Result.FORWARDED);
        RecordedMail forwarded = sesRecorder.single();
        assertThat(forwarded.raw()).isTrue();
        assertThat(forwarded.envelope()).containsExactly(ORGANIZER);
        assertThat(forwarded.replyTo()).containsExactly("success+registrant@simulator.amazonses.com");
        assertThat(forwarded.subject()).isEqualTo("Re: BATbern60 Anmeldebestaetigung");

        EmailForwardingLog entry = repository.findByS3Key(key).orElseThrow();
        assertThat(entry.getOutcome()).isEqualTo(EmailForwardingOutcome.FORWARDED);
        assertThat(entry.getAliases()).isEqualTo("replies");
        assertThat(entry.getRecipientCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_logDroppedAndSendNothing_when_outOfOfficeReply")
    void should_logDroppedAndSendNothing_when_outOfOfficeReply() throws Exception {
        String key = KEY_PREFIX + System.nanoTime();

        Result result = service.forwardUnrecognizedReply(key, fixture("mail/ooo-reply.eml"));

        assertThat(result).isEqualTo(Result.DROPPED_AUTO_REPLY);
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(repository.findByS3Key(key).orElseThrow().getOutcome())
                .isEqualTo(EmailForwardingOutcome.DROPPED_AUTO_REPLY);
    }

    @Test
    @DisplayName("should_forwardOnce_when_sameReplyRedelivered")
    void should_forwardOnce_when_sameReplyRedelivered() throws Exception {
        String key = KEY_PREFIX + System.nanoTime();
        byte[] raw = fixture("mail/human-reply.eml");

        service.forwardUnrecognizedReply(key, raw);
        Result second = service.forwardUnrecognizedReply(key, raw);

        assertThat(second).isEqualTo(Result.DUPLICATE);
        assertThat(sesRecorder.single().envelope()).containsExactly(ORGANIZER);
    }

    @Test
    @DisplayName("should_forwardToEveryOrganizer_when_11MbMailToOk (10.9 MB failed on SES v1, 2026-11-22)")
    void should_forwardToEveryOrganizer_when_11MbMailToOk() {
        String second = "success+organizer2@simulator.amazonses.com";
        String third = "success+organizer3@simulator.amazonses.com";
        cums.usersWithRole("ORGANIZER", ORGANIZER_USERNAME, "second.organizer", "third.organizer");
        cums.user("second.organizer", "Second", "Organizer", second);
        cums.user("third.organizer", "Third", "Organizer", third);
        String key = KEY_PREFIX + "ok-" + System.nanoTime();
        byte[] raw = largeOrganizerMail(ORGANIZER, 11 * 1024 * 1024);
        assertThat(raw.length).isGreaterThan(10 * 1024 * 1024);

        Result result = service.forward(key, raw);

        assertThat(result).isEqualTo(Result.FORWARDED);
        List<RecordedMail> copies = sesRecorder.mails();
        assertThat(copies).hasSize(3);
        assertThat(copies).flatExtracting(RecordedMail::envelope).containsExactlyInAnyOrder(ORGANIZER, second, third);
        assertThat(copies).allSatisfy(copy -> {
            assertThat(copy.raw()).isTrue();
            assertThat(copy.attachmentNames()).containsExactly("plan.pdf");
            assertThat(copy.subject()).isEqualTo("Grosse Beilage");
        });
        EmailForwardingLog entry = repository.findByS3Key(key).orElseThrow();
        assertThat(entry.getOutcome()).isEqualTo(EmailForwardingOutcome.FORWARDED);
        assertThat(entry.getAliases()).isEqualTo("ok");
        assertThat(entry.getRecipientCount()).isEqualTo(3);
    }

    /**
     * An organizer's mail to {@code ok@} with a base64 attachment, at least {@code minBytes} long,
     * DMARC-authenticated as SES receipt would mark it (ok@ is organizer-only).
     */
    private static byte[] largeOrganizerMail(String from, int minBytes) {
        byte[] attachment = new byte[minBytes * 3 / 4];
        new Random(42).nextBytes(attachment);
        String encoded = Base64.getMimeEncoder().encodeToString(attachment);
        String mime = "Authentication-Results: amazonses.com; spf=pass; dkim=pass header.i=@simulator.amazonses.com;"
                + " dmarc=pass header.from=simulator.amazonses.com;\r\n"
                + "From: \"Test Organizer\" <" + from + ">\r\n"
                + "To: ok@batbern.ch\r\n"
                + "Subject: Grosse Beilage\r\n"
                + "Message-ID: <large-ok-1@simulator.amazonses.com>\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"b1\"\r\n\r\n"
                + "--b1\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nDer Plan im Anhang.\r\n"
                + "--b1\r\nContent-Type: application/pdf; name=\"plan.pdf\"\r\n"
                + "Content-Disposition: attachment; filename=\"plan.pdf\"\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n"
                + encoded + "\r\n--b1--\r\n";
        return mime.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] fixture(String path) throws Exception {
        return new ClassPathResource(path).getContentAsByteArray();
    }
}
