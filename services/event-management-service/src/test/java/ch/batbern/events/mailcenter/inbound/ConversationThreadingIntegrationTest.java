package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.InboundEmailRouter;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Story 16.2a AC1–AC7 against real PostgreSQL: which inbound mail opens or joins a conversation,
 * and which is only counted. Synthetic addresses only; nothing is sent.
 *
 * <p>Not {@code @Transactional}: the store and the counter commit in their own transaction, so this
 * class removes what it wrote.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Stories 16.2a, 16.2b: conversation threading")
class ConversationThreadingIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern916";
    private static final String MODERATOR = "batbern916-moderator@batbern.ch";
    private static final String SPEAKER = "success+speaker@simulator.amazonses.com";
    private static final String ORGANIZER_MAIL = "organizer-mail@example.com";

    @Autowired
    private InboundMailCenter mailCenter;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ForwardingDirectory directory;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT).title("BATbern Test Event #916").eventNumber(916)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("lead.organizer").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        when(directory.organizerEmails()).thenReturn(Set.of(ORGANIZER_MAIL));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM mail_message WHERE event_code = ? OR direction = 'IN'", EVENT);
        jdbc.update("DELETE FROM mail_conversation");
        jdbc.update("DELETE FROM mail_automatic_event");
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT);
    }

    // ── AC1: moderator alias ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC1: a speaker's mail to the moderator alias opens a conversation, assigned to the lead organizer")
    void should_openConversation_when_speakerWritesModerator() {
        alias(mail(SPEAKER, MODERATOR, "Frage zum Beamer", "q1@example.com", null, null, "Brauche HDMI."));

        Map<String, Object> conv = onlyConversation();
        assertThat(conv).containsEntry("event_code", EVENT).containsEntry("audience", "SPEAKERS")
                .containsEntry("status", "OPEN").containsEntry("assignee_username", "lead.organizer")
                .containsEntry("counterpart_address", SPEAKER).containsEntry("counterpart_name", "A Speaker")
                .containsEntry("subject", "Frage zum Beamer");
        assertThat((String) conv.get("conversation_key")).startsWith("conv-");
        assertThat((String) conv.get("reply_token")).matches("[a-z2-7]{16}");

        Map<String, Object> msg = jdbc.queryForMap("SELECT * FROM mail_message WHERE conversation_id = ?",
                conv.get("id"));
        assertThat(msg).containsEntry("direction", "IN").containsEntry("audience", "SPEAKERS")
                .containsEntry("content_source", "OWN_TEXT").containsEntry("event_code", EVENT)
                .containsEntry("from_address", SPEAKER).containsEntry("message_id_header", "q1@example.com")
                .containsEntry("inbound_path", "ALIAS").containsEntry("alias_local_part", "batbern916-moderator")
                .containsEntry("recipient_count", 0).containsEntry("body_text", "Brauche HDMI.");
        assertThat((String) msg.get("body_html")).contains("Brauche HDMI.").isNotBlank();
        assertThat((String) msg.get("send_group_id")).matches("in_[0-9a-f]{40}");
        assertThat((String) msg.get("mail_key")).startsWith(EVENT + "-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_recipient r JOIN mail_message m "
                + "ON m.id = r.mail_message_id WHERE m.direction = 'IN'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("AC1: without a lead organizer the conversation is unassigned")
    void should_leaveUnassigned_when_eventHasNoOrganizer() {
        jdbc.update("UPDATE events SET organizer_username = '' WHERE event_code = ?", EVENT);
        alias(mail(SPEAKER, MODERATOR, "Frage", "q1@example.com", null, null, "Hallo"));
        assertThat(onlyConversation().get("assignee_username")).isNull();
    }

    @Test
    @DisplayName("AC3: a second mail of the same speaker joins the open conversation")
    void should_joinConversation_when_sameSpeakerWritesAgain() {
        alias(mail(SPEAKER, MODERATOR, "Frage", "q1@example.com", null, null, "Eins"));
        alias(mail(SPEAKER, MODERATOR, "Noch was", "q2@example.com", null, null, "Zwei"));

        assertThat(conversationCount()).isEqualTo(1);
        assertThat(messagesOf(onlyConversation().get("id"))).isEqualTo(2);
    }

    @Test
    @DisplayName("AC3: a new mail on a DONE conversation reopens it")
    void should_reopen_when_newMailOnDoneConversation() {
        alias(mail(SPEAKER, MODERATOR, "Frage", "q1@example.com", null, null, "Eins"));
        Object id = onlyConversation().get("id");
        jdbc.update("UPDATE mail_conversation SET status = 'DONE' WHERE id = ?", id);

        alias(mail(SPEAKER, MODERATOR, "Re: Frage", "q2@example.com", "q1@example.com", null, "Doch noch"));

        assertThat(conversationCount()).isEqualTo(1);
        assertThat(onlyConversation()).containsEntry("status", "OPEN");
    }

    // ── AC2 / AC3: replies@ and Message-ID matching ─────────────────────────────────────────

    @Test
    @DisplayName("AC2: In-Reply-To naming a SPEAKERS mail by its SES id opens a conversation on that event")
    void should_open_when_replyMatchesSpeakerMailBySesId() {
        UUID out = outbound("SPEAKERS", EVENT);
        recipient(out, SPEAKER, "0107019aaaaa-0001-000000", null);

        reply(mail(SPEAKER, "replies@batbern.ch", "Re: Q&A", "r1@example.com",
                "0107019aaaaa-0001-000000@eu-central-1.amazonses.com", null, "Antwort"));

        assertThat(onlyConversation()).containsEntry("event_code", EVENT).containsEntry("status", "OPEN");
        assertThat(jdbc.queryForObject("SELECT inbound_path FROM mail_message WHERE direction = 'IN'", String.class))
                .isEqualTo("REPLIES");
    }

    @Test
    @DisplayName("AC3: a header captured from the SES event (16.1b) matches")
    void should_open_when_replyMatchesCapturedHeader() {
        UUID out = outbound("SPEAKERS", EVENT);
        recipient(out, SPEAKER, "0107019abbbb-0002-000000", "custom-id-42@mailer.example.com");

        reply(mail(SPEAKER, "replies@batbern.ch", "Re: x", "r1@example.com", "custom-id-42@mailer.example.com",
                null, "Antwort"));

        assertThat(conversationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC3: References only (newest first) find the conversation by a stored inbound header")
    void should_join_when_referencesNameStoredInboundMail() {
        alias(mail(SPEAKER, MODERATOR, "Frage", "q1@example.com", null, null, "Eins"));
        jdbc.update("UPDATE mail_conversation SET status = 'WAITING'");

        reply(mail(SPEAKER, "replies@batbern.ch", "Re: Frage", "q2@example.com", null,
                "<unknown@example.com> <q1@example.com>", "Zwei"));

        assertThat(conversationCount()).isEqualTo(1);
        assertThat(onlyConversation()).containsEntry("status", "OPEN");
        assertThat(messagesOf(onlyConversation().get("id"))).isEqualTo(2);
    }

    @Test
    @DisplayName("AC3: replies+{token}@ joins its conversation first")
    void should_join_when_tokenAddressed() {
        alias(mail(SPEAKER, MODERATOR, "Frage", "q1@example.com", null, null, "Eins"));
        String token = (String) onlyConversation().get("reply_token");

        reply(mail("other+person@simulator.amazonses.com", "replies+" + token + "@batbern.ch", "Re: Frage",
                "q9@example.com", null, null, "Vom Kollegen"));

        assertThat(conversationCount()).isEqualTo(1);
        assertThat(messagesOf(onlyConversation().get("id"))).isEqualTo(2);
    }

    @Test
    @DisplayName("16.2b AC2: an unrecognized reply that matches nothing opens a conversation without event")
    void should_openWithoutEvent_when_replyMatchesNothing() {
        reply(mail(SPEAKER, "replies@batbern.ch", "Re: x", "r1@example.com", "nothing@example.com", null, "Hi"));
        assertThat(onlyConversation()).containsEntry("event_code", null).containsEntry("audience", "PUBLIC")
                .containsEntry("assignee_username", null).containsEntry("status", "OPEN");
    }

    @Test
    @DisplayName("16.2b AC2: a reply to a registrant mail opens a conversation on its event, audience REGISTRANTS")
    void should_open_when_replyMatchesRegistrantMail() {
        UUID out = outbound("REGISTRANTS", EVENT);
        recipient(out, SPEAKER, "0107019acccc-0003-000000", null);
        reply(mail(SPEAKER, "replies@batbern.ch", "Re: x", "r1@example.com",
                "0107019acccc-0003-000000@eu-central-1.amazonses.com", null, "Hi"));
        assertThat(onlyConversation()).containsEntry("event_code", EVENT).containsEntry("audience", "REGISTRANTS")
                .containsEntry("assignee_username", null);
    }

    @Test
    @DisplayName("16.2b AC2: a reply to the newsletter opens a conversation on its event, audience NEWSLETTER")
    void should_open_when_replyMatchesNewsletter() {
        UUID out = outbound("NEWSLETTER", EVENT);
        recipient(out, SPEAKER, null, "nl-1@batbern.ch");
        reply(mail(SPEAKER, "replies@batbern.ch", "Re: Newsletter", "r1@example.com", "nl-1@batbern.ch", null,
                "Gerne"));
        assertThat(onlyConversation()).containsEntry("event_code", EVENT).containsEntry("audience", "NEWSLETTER");
    }

    // ── 16.2b AC1, AC6: contact aliases ─────────────────────────────────────────────────────

    @Test
    @DisplayName("16.2b AC1: info@ without a match opens an unassigned PUBLIC conversation without event")
    void should_openWithoutEvent_when_infoMailMatchesNothing() {
        alias(mail(SPEAKER, "info@batbern.ch", "Parkplätze?", "i1@example.com", null, null, "Gibt es Parkplätze?"));
        assertThat(onlyConversation()).containsEntry("event_code", null).containsEntry("audience", "PUBLIC")
                .containsEntry("assignee_username", null).containsEntry("status", "OPEN");
        assertThat(jdbc.queryForObject("SELECT alias_local_part FROM mail_message WHERE direction = 'IN'",
                String.class)).isEqualTo("info");
    }

    @Test
    @DisplayName("16.2b AC1: a second info@ mail of the same person joins the open no-event conversation")
    void should_joinNoEventConversation_when_samePersonWritesAgain() {
        alias(mail(SPEAKER, "info@batbern.ch", "Frage", "i1@example.com", null, null, "Eins"));
        alias(mail(SPEAKER, "events@batbern.ch", "Noch was", "i2@example.com", null, null, "Zwei"));
        assertThat(conversationCount()).isEqualTo(1);
        assertThat(messagesOf(onlyConversation().get("id"))).isEqualTo(2);
    }

    @Test
    @DisplayName("16.2b AC1: support@ with In-Reply-To of a registrant mail lands on that event")
    void should_putOnEvent_when_supportMailRepliesToRegistrantMail() {
        UUID out = outbound("REGISTRANTS", EVENT);
        recipient(out, SPEAKER, "0107019adddd-0004-000000", null);
        alias(mail(SPEAKER, "support@batbern.ch", "Re: Anmeldung", "s1@example.com",
                "0107019adddd-0004-000000@eu-central-1.amazonses.com", null, "Hilfe"));
        assertThat(onlyConversation()).containsEntry("event_code", EVENT).containsEntry("audience", "REGISTRANTS");
    }

    @Test
    @DisplayName("16.2b AC6: an organizer writing to info@ opens a conversation; the organizer is the counterpart")
    void should_open_when_organizerWritesInfo() {
        alias(mail(ORGANIZER_MAIL, "info@batbern.ch", "Weiterleitung", "o2@example.com", null, null, "FYI"));
        assertThat(onlyConversation()).containsEntry("counterpart_address", ORGANIZER_MAIL);
    }

    @Test
    @DisplayName("16.2b: ok@ and organizer broadcasts to -participants@ or partner@ open nothing")
    void should_notOpen_when_internalOrBroadcastAlias() {
        alias(mail(ORGANIZER_MAIL, "ok@batbern.ch", "Intern", "o3@example.com", null, null, "x"));
        alias(mail(ORGANIZER_MAIL, "batbern916-participants@batbern.ch", "Info", "o4@example.com", null, null, "x"));
        alias(mail(ORGANIZER_MAIL, "partner@batbern.ch", "Info", "o5@example.com", null, null, "x"));
        assertThat(conversationCount()).isZero();
        assertThat(inboundCount()).isZero();
    }

    @Test
    @DisplayName("16.2b AC5: an out-of-office to info@ is counted, nothing stored")
    void should_count_when_outOfOfficeToInfo() {
        alias(mail(SPEAKER, "info@batbern.ch", "Abwesenheitsnotiz", "a1@example.com", null, null, "Bin weg"));
        assertThat(conversationCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_automatic_event WHERE kind = 'OUT_OF_OFFICE'",
                Integer.class)).isEqualTo(1);
    }

    // ── 16.2b AC3: subject fallback ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("16.2b AC3: info@ naming exactly one existing event in the subject lands on that event")
    void should_putOnEvent_when_subjectNamesOneEvent() {
        alias(mail(SPEAKER, "info@batbern.ch", "Re: AW: BATbern916 Anmeldung", "f1@example.com", null, null, "?"));
        assertThat(onlyConversation()).containsEntry("event_code", EVENT).containsEntry("audience", "PUBLIC");
    }

    @Test
    @DisplayName("16.2b AC3: two different codes or an unknown code give no event")
    void should_notPutOnEvent_when_subjectAmbiguousOrUnknown() {
        alias(mail(SPEAKER, "info@batbern.ch", "BATbern916 und BATbern1", "f2@example.com", null, null, "?"));
        alias(mail("other+person@simulator.amazonses.com", "info@batbern.ch", "BATbern99999", "f3@example.com",
                null, null, "?"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_conversation WHERE event_code IS NULL",
                Integer.class)).isEqualTo(2);
    }

    // ── AC4, AC6 ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC4: the same mail delivered twice (two S3 keys) is stored once")
    void should_storeOnce_when_deliveredTwice() {
        byte[] raw = mail(SPEAKER, MODERATOR, "Frage", "dup@example.com", null, null, "Eins");
        mailCenter.ingestAlias("forwarding/a", raw, EmailForwardingService.Result.FORWARDED);
        mailCenter.ingestAlias("forwarding/b", raw, EmailForwardingService.Result.FORWARDED);

        assertThat(inboundCount()).isEqualTo(1);
        assertThat(messagesOf(onlyConversation().get("id"))).isEqualTo(1);
    }

    @Test
    @DisplayName("AC6: an organizer writing to the moderator alias opens nothing")
    void should_notOpen_when_organizerWrites() {
        alias(mail(ORGANIZER_MAIL, MODERATOR, "Info", "o1@example.com", null, null, "Intern"));
        assertThat(conversationCount()).isZero();
        assertThat(inboundCount()).isZero();
    }

    // ── AC7: counted, not opened ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC7: an out-of-office to the moderator alias is counted on the event, nothing stored")
    void should_count_when_outOfOffice() {
        byte[] raw = mail(SPEAKER, MODERATOR, "Abwesenheitsnotiz: Frage", "ooo@example.com", null, null, "Weg");
        alias(raw);
        alias(raw);

        assertThat(conversationCount()).isZero();
        assertThat(inboundCount()).isZero();
        assertThat(jdbc.queryForList("SELECT kind, event_code FROM mail_automatic_event"))
                .containsExactly(Map.of("kind", "OUT_OF_OFFICE", "event_code", EVENT));
    }

    @Test
    @DisplayName("AC7/AC8: a keyword that acted is counted on the subject's event")
    void should_count_when_keywordActed() {
        mailCenter.ingestReply("emails/k1", mail(SPEAKER, "replies@batbern.ch", "Re: " + EVENT + " Anmeldung",
                "k1@example.com", null, null, "cancel"), InboundEmailRouter.RouteResult.DEREGISTRATION_LINK);

        assertThat(jdbc.queryForList("SELECT kind, event_code FROM mail_automatic_event"))
                .containsExactly(Map.of("kind", "KEYWORD_CANCEL", "event_code", EVENT));
        assertThat(inboundCount()).isZero();
    }

    @Test
    @DisplayName("AC7/AC8: a bounce is attributed to the outbound mail it names")
    void should_attributeBounce_when_inReplyToMatches() {
        UUID out = outbound("SPEAKERS", EVENT);
        recipient(out, SPEAKER, "0107019adddd-0004-000000", null);
        String headers = "From: MAILER-DAEMON@example.com\r\nTo: replies@batbern.ch\r\nSubject: Undelivered\r\n"
                + "Message-ID: <b1@example.com>\r\n"
                + "In-Reply-To: <0107019adddd-0004-000000@eu-central-1.amazonses.com>\r\n"
                + "Content-Type: text/plain\r\n\r\nfailed\r\n";
        reply(headers.getBytes(StandardCharsets.UTF_8));

        assertThat(jdbc.queryForList("SELECT kind, event_code FROM mail_automatic_event"))
                .containsExactly(Map.of("kind", "BOUNCE", "event_code", EVENT));
    }

    @Test
    @DisplayName("A failure inside the Mail Center never reaches the caller")
    void should_notThrow_when_mailUnparseable() {
        mailCenter.ingestAlias("forwarding/x", new byte[] {0, 1, 2}, EmailForwardingService.Result.FORWARDED);
        assertThat(inboundCount()).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private void alias(byte[] raw) {
        mailCenter.ingestAlias("forwarding/" + UUID.randomUUID(), raw, EmailForwardingService.Result.FORWARDED);
    }

    private void reply(byte[] raw) {
        mailCenter.ingestReply("emails/" + UUID.randomUUID(), raw, InboundEmailRouter.RouteResult.UNRECOGNIZED);
    }

    private static byte[] mail(String from, String to, String subject, String messageId, String inReplyTo,
            String references, String body) {
        StringBuilder m = new StringBuilder()
                .append("From: \"A Speaker\" <").append(from).append(">\r\n")
                .append("To: ").append(to).append("\r\n")
                .append("Subject: ").append(subject).append("\r\n")
                .append("Message-ID: <").append(messageId).append(">\r\n");
        if (inReplyTo != null) {
            m.append("In-Reply-To: <").append(inReplyTo).append(">\r\n");
        }
        if (references != null) {
            m.append("References: ").append(references).append("\r\n");
        }
        m.append("MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n").append(body).append("\r\n");
        return m.toString().getBytes(StandardCharsets.UTF_8);
    }

    private UUID outbound(String audience, String eventCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mail_message (id, mail_key, send_group_id, event_code, audience, content_source, "
                + "subject, body_html) VALUES (?, ?, ?, ?, ?, 'TEMPLATE', 'Sent', '<p>x</p>')",
                id, eventCode + "-out-" + id, "grp-" + id, eventCode, audience);
        return id;
    }

    private void recipient(UUID message, String address, String sesId, String header) {
        jdbc.update("INSERT INTO mail_recipient (mail_message_id, address, kind, ses_message_id, status, "
                + "message_id_header) VALUES (?, ?, 'TO', ?, 'SENT', ?)", message, address, sesId, header);
    }

    private Map<String, Object> onlyConversation() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM mail_conversation");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private int conversationCount() {
        return jdbc.queryForObject("SELECT count(*) FROM mail_conversation", Integer.class);
    }

    private int inboundCount() {
        return jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE direction = 'IN'", Integer.class);
    }

    private int messagesOf(Object conversationId) {
        return jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE conversation_id = ?", Integer.class,
                conversationId);
    }
}
