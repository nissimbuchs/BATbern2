package ch.batbern.events.mailcenter;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.MailMessage;
import ch.batbern.events.domain.MailRecipient;
import ch.batbern.events.repository.MailMessageRepository;
import ch.batbern.events.repository.MailRecipientRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.9 AC3/AC4 against real PostgreSQL: partner-coordination posts what it sent to
 * {@code POST /internal/mails}; EMS records it like its own mail. No token (service to service in
 * the VPC); the production chain's permit is covered by {@code InternalPathSecurityTest}.
 *
 * <p>Not {@code @Transactional}: the recorder commits in its own transaction.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Internal mail record endpoint (Story 16.9)")
class InternalMailControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern917";
    private static final String GROUP = "run-1_BATbern917_partner-meeting-invite";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MailMessageRepository messages;
    @Autowired
    private MailRecipientRepository recipients;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
    }

    @Test
    @DisplayName("AC1/AC3/AC4: a PARTNERS mail with five To addresses is one message with five recipient rows")
    void partnersMail_recordedWithSplitRecipients() throws Exception {
        Map<String, Object> body = payload("PARTNERS");
        body.put("to", String.join(",", addresses(5)));

        mockMvc.perform(post("/internal/mails").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());

        MailMessage message = messages.findBySendGroupId(GROUP).orElseThrow();
        assertThat(message.getEventCode()).isEqualTo(EVENT_CODE);
        assertThat(message.getTemplateKey()).isEqualTo("partner-meeting-invite");
        assertThat(message.getSenderUsername()).isEqualTo("organizer1");
        assertThat(message.getReference()).isEqualTo("partner-meeting:6f1c");
        assertThat(message.getRecipientCount()).isEqualTo(5);
        assertThat(recipients.findByMailMessageId(message.getId()))
                .hasSize(5)
                .allSatisfy(r -> {
                    assertThat(r.getKind()).isEqualTo(MailRecipient.Kind.TO);
                    assertThat(r.getSesMessageId()).isEqualTo("ses-abc");
                    assertThat(r.getStatus()).isEqualTo(MailRecipient.Status.SENT);
                });
    }

    @Test
    @DisplayName("AC4: any audience other than PARTNERS is refused with 400")
    void otherAudience_rejected() throws Exception {
        mockMvc.perform(post("/internal/mails").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("SPEAKERS"))))
                .andExpect(status().isBadRequest());

        assertThat(messages.findBySendGroupId(GROUP)).isEmpty();
    }

    @Test
    @DisplayName("AC4: an oversized subject, body or address list is refused with 400")
    void oversized_rejected() throws Exception {
        Map<String, Object> longSubject = payload("PARTNERS");
        longSubject.put("subject", "s".repeat(1001));
        Map<String, Object> bigBody = payload("PARTNERS");
        bigBody.put("htmlBody", "b".repeat(2 * 1024 * 1024 + 1));
        Map<String, Object> manyAddresses = payload("PARTNERS");
        manyAddresses.put("to", String.join(",", addresses(501)));

        for (Map<String, Object> body : List.of(longSubject, bigBody, manyAddresses)) {
            mockMvc.perform(post("/internal/mails").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(messages.findBySendGroupId(GROUP)).isEmpty();
    }

    @Test
    @DisplayName("AC4: missing send group or audience is refused with 400")
    void missingFields_rejected() throws Exception {
        Map<String, Object> body = payload("PARTNERS");
        body.remove("sendGroupId");

        mockMvc.perform(post("/internal/mails").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private static Map<String, Object> payload(String audience) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sendGroupId", GROUP);
        body.put("eventCode", EVENT_CODE);
        body.put("audience", audience);
        body.put("templateKey", "partner-meeting-invite");
        body.put("contentSource", "TEMPLATE");
        body.put("senderUsername", "organizer1");
        body.put("test", false);
        body.put("reference", "partner-meeting:6f1c");
        body.put("to", "success+p1@simulator.amazonses.com");
        body.put("cc", List.of());
        body.put("subject", "Partner meeting");
        body.put("htmlBody", "<p>Invite</p>");
        body.put("sesMessageId", "ses-abc");
        body.put("failureReason", null);
        return body;
    }

    private static List<String> addresses(int count) {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            result.add("success+p" + i + "@simulator.amazonses.com");
        }
        return result;
    }
}
