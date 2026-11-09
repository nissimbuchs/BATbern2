package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.forwarding.AliasMailProcessor;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.mail.SesRecorder.RecordedMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 16.6 AC4: a reply to an organizer's answer ({@code replies+{token}@}) lands in its
 * conversation on both SES paths, is forwarded to the organizers, and never triggers a keyword
 * action. An unknown token is treated like a plain {@code replies@} mail: the router runs first.
 *
 * <p>Everything in the process runs for real (router, forwarding, Mail Center); SES is the
 * {@link SesRecorder} and CUMS the {@link CumsContractHarness}. The keyword is "Abmelden" because its
 * action is observable: the router sends the sender an unsubscribe confirmation. The unknown-token
 * test is the positive control: the same keyword there does produce that confirmation.
 *
 * <p>Not {@code @Transactional}: the store commits in its own transaction; this class cleans up.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Story 16.6: token replies on both paths")
class TokenReplyRoutingIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern919";
    private static final String SPEAKER = "success+speaker@simulator.amazonses.com";
    private static final String TOKEN = "qrstuvwxyzabcdef";
    private static final String ORGANIZER_USERNAME = "orga.token";
    private static final String ORGANIZER = "success+organizer@simulator.amazonses.com";
    private static final String KEYWORD = "Abmelden";

    @Autowired
    private AliasMailProcessor aliasProcessor;
    @Autowired
    private InboundReplyProcessor replyProcessor;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;

    private UUID conversation;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
        cums.usersWithRole("ORGANIZER", ORGANIZER_USERNAME);
        cums.user(ORGANIZER_USERNAME, "Orga", "Token", ORGANIZER);
        conversation = UUID.randomUUID();
        jdbc.update("INSERT INTO mail_conversation (id, conversation_key, event_code, audience, subject, "
                + "counterpart_address, status, reply_token, last_message_at) "
                + "VALUES (?, 'conv-9201', ?, 'SPEAKERS', 'Slides', ?, 'WAITING', ?, ?)",
                conversation, EVENT, SPEAKER, TOKEN, Timestamp.from(Instant.now()));
    }

    @AfterEach
    void tearDown() {
        cums.reset();
        jdbc.update("DELETE FROM mail_message WHERE direction = 'IN'");
        jdbc.update("DELETE FROM mail_conversation");
        jdbc.update("DELETE FROM mail_automatic_event");
        jdbc.update("DELETE FROM email_forwarding_log WHERE s3_key LIKE 'emails/it-token-%' "
                + "OR s3_key LIKE 'forwarding/it-token-%'");
    }

    @Test
    @DisplayName("replies path: a keyword to replies+{token}@ runs no router action, is forwarded and joins the conversation")
    void should_joinConversation_when_tokenReplyOnRepliesPath() {
        replyProcessor.process("emails/it-token-" + UUID.randomUUID(),
                mail(SPEAKER, "replies+" + TOKEN + "@batbern.ch", "Re: BATbern919 Slides"));

        assertNoRouterActionAndForwarded(SPEAKER);
        assertJoined();
    }

    @Test
    @DisplayName("alias path: replies+{token}@ is not dropped as unresolved; it is forwarded and joins the conversation")
    void should_joinConversation_when_tokenReplyOnAliasPath() {
        EmailForwardingService.Result result = aliasProcessor.process("forwarding/it-token-" + UUID.randomUUID(),
                mail(SPEAKER, "replies+" + TOKEN + "@batbern.ch", "Re: BATbern919 Slides"));

        assertThat(result).isEqualTo(EmailForwardingService.Result.CONVERSATION_REPLY);
        assertNoRouterActionAndForwarded(SPEAKER);
        assertJoined();
    }

    @Test
    @DisplayName("unknown token: handled like a plain replies@ mail (router first), on both paths")
    void should_routeLikeReplies_when_tokenUnknown() {
        // A sender of its own: the router's rate limiter counts per sender for the whole context.
        String sender = "success+unknown-token-" + UUID.randomUUID() + "@simulator.amazonses.com";

        replyProcessor.process("emails/it-token-" + UUID.randomUUID(),
                mail(sender, "replies+aaaaaaaaaaaaaaaa@batbern.ch", "Frage"));
        sesRecorder.awaitAsyncSends(); // the confirmation goes out on the @Async EmailService path
        assertThat(sesRecorder.mailsTo(sender)).as("router acted on the replies path").hasSize(1);

        aliasProcessor.process("forwarding/it-token-" + UUID.randomUUID(),
                mail(sender, "replies+bbbbbbbbbbbbbbbb@batbern.ch", "Frage 2"));
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(sender)).as("router acted on the alias path").hasSize(2);

        assertThat(forwardedToOrganizers()).as("an acted-on keyword is not forwarded").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE conversation_id = ?",
                Integer.class, conversation)).isZero();
    }

    private void assertNoRouterActionAndForwarded(String sender) {
        sesRecorder.awaitAsyncSends(); // an absent async confirmation must have had its chance
        assertThat(sesRecorder.mailsTo(sender)).as("no unsubscribe confirmation: the router did not run").isEmpty();
        assertThat(forwardedToOrganizers()).singleElement()
                .satisfies(copy -> assertThat(copy.subject()).isEqualTo("Re: BATbern919 Slides"));
    }

    private List<RecordedMail> forwardedToOrganizers() {
        return sesRecorder.mails().stream().filter(m -> m.envelope().contains(ORGANIZER)).toList();
    }

    private void assertJoined() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE conversation_id = ?",
                Integer.class, conversation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM mail_conversation WHERE id = ?", String.class,
                conversation)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_automatic_event", Integer.class)).isZero();
    }

    private static byte[] mail(String from, String to, String subject) {
        String m = "From: \"A Speaker\" <" + from + ">\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Message-ID: <" + UUID.randomUUID() + "@mail.batbern-test.ch>\r\n"
                + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + KEYWORD + "\r\n";
        return m.getBytes(StandardCharsets.UTF_8);
    }
}
