package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.forwarding.AliasMailProcessor;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.events.forwarding.InboundEmailRouter;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 16.6 AC4: a reply to an organizer's answer ({@code replies+{token}@}) lands in its
 * conversation on both SES paths, and never triggers a keyword action, even when its first line is
 * "accept". An unknown token is treated like a plain {@code replies@} mail. Nothing is sent: the
 * forward to the organizers is stubbed.
 *
 * <p>Not {@code @Transactional}: the store commits in its own transaction; this class cleans up.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Story 16.6: token replies on both paths")
class TokenReplyRoutingIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern919";
    private static final String SPEAKER = "success+speaker@simulator.amazonses.com";
    private static final String TOKEN = "qrstuvwxyzabcdef";

    @Autowired
    private AliasMailProcessor aliasProcessor;
    @Autowired
    private InboundReplyProcessor replyProcessor;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ForwardingDirectory directory;
    @MockitoSpyBean
    private InboundEmailRouter router;
    @MockitoSpyBean
    private EmailForwardingService forwardingService;

    private UUID conversation;

    @BeforeEach
    void setUp() {
        when(directory.organizerEmails()).thenReturn(Set.of("organizer-mail@example.com"));
        doReturn(EmailForwardingService.Result.FORWARDED).when(forwardingService)
                .forwardUnrecognizedReply(anyString(), any());
        conversation = UUID.randomUUID();
        jdbc.update("INSERT INTO mail_conversation (id, conversation_key, event_code, audience, subject, "
                + "counterpart_address, status, reply_token, last_message_at) "
                + "VALUES (?, 'conv-9201', ?, 'SPEAKERS', 'Slides', ?, 'WAITING', ?, ?)",
                conversation, EVENT, SPEAKER, TOKEN, Timestamp.from(Instant.now()));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM mail_message WHERE direction = 'IN'");
        jdbc.update("DELETE FROM mail_conversation");
        jdbc.update("DELETE FROM mail_automatic_event");
    }

    @Test
    @DisplayName("replies path: 'accept' to replies+{token}@ runs no router action and joins the conversation")
    void should_joinConversation_when_tokenReplyOnRepliesPath() {
        replyProcessor.process("emails/" + UUID.randomUUID(),
                mail("replies+" + TOKEN + "@batbern.ch", "Re: BATbern919 Slides", "Accept"));

        verify(router, never()).route(any());
        verify(forwardingService).forwardUnrecognizedReply(anyString(), any());
        assertJoined();
    }

    @Test
    @DisplayName("alias path: replies+{token}@ is not dropped as unresolved; it joins the conversation")
    void should_joinConversation_when_tokenReplyOnAliasPath() {
        EmailForwardingService.Result result = aliasProcessor.process("forwarding/" + UUID.randomUUID(),
                mail("replies+" + TOKEN + "@batbern.ch", "Re: BATbern919 Slides", "Accept"));

        assertThat(result).isEqualTo(EmailForwardingService.Result.CONVERSATION_REPLY);
        verify(router, never()).route(any());
        verify(forwardingService).forwardUnrecognizedReply(anyString(), any());
        assertJoined();
    }

    @Test
    @DisplayName("unknown token: handled like a plain replies@ mail (router first)")
    void should_routeLikeReplies_when_tokenUnknown() {
        replyProcessor.process("emails/" + UUID.randomUUID(),
                mail("replies+aaaaaaaaaaaaaaaa@batbern.ch", "Frage", "Wie komme ich hin?"));
        verify(router).route(any());

        aliasProcessor.process("forwarding/" + UUID.randomUUID(),
                mail("replies+bbbbbbbbbbbbbbbb@batbern.ch", "Frage 2", "Und parken?"));
        verify(router, org.mockito.Mockito.times(2)).route(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE conversation_id = ?",
                Integer.class, conversation)).isZero();
    }

    private void assertJoined() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE conversation_id = ?",
                Integer.class, conversation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM mail_conversation WHERE id = ?", String.class,
                conversation)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_automatic_event", Integer.class)).isZero();
    }

    private static byte[] mail(String to, String subject, String body) {
        String m = "From: \"A Speaker\" <" + SPEAKER + ">\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "Message-ID: <" + UUID.randomUUID() + "@mail.batbern-test.ch>\r\n"
                + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + body + "\r\n";
        return m.getBytes(StandardCharsets.UTF_8);
    }
}
