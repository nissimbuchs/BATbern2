package ch.batbern.events.forwarding;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.EmailForwardingLog;
import ch.batbern.events.domain.EmailForwardingOutcome;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.forwarding.EmailForwardingService.Result;
import ch.batbern.events.repository.EmailForwardingLogRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 16.0 AC3/AC4 (#1160) against real PostgreSQL: an unrecognized {@code replies@} mail is
 * forwarded to the organizers and logged; an out-of-office reply is logged as dropped (V131).
 * Fixtures are synthetic ({@code src/test/resources/mail/*.eml}, SES simulator addresses). SES
 * and CUMS are mocked: nothing is sent.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Unrecognized replies@ mail (PostgreSQL)")
class EmailForwardingRepliesIntegrationTest extends AbstractIntegrationTest {

    private static final String KEY_PREFIX = "emails/it-replies-";
    private static final String ORGANIZER = "success+organizer@simulator.amazonses.com";

    @Autowired
    private EmailForwardingService service;
    @Autowired
    private EmailForwardingLogRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private UserApiClient userApiClient;
    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void organizers() {
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("organizer"));
        when(userApiClient.getUserByUsername("organizer"))
                .thenReturn(new UserResponse().id("organizer").email(ORGANIZER));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM email_forwarding_log WHERE s3_key LIKE 'emails/it-replies-%'");
    }

    @Test
    @DisplayName("should_forwardToOrganizersAndLog_when_humanReplyMatchesNoKeyword")
    void should_forwardToOrganizersAndLog_when_humanReplyMatchesNoKeyword() throws Exception {
        String key = KEY_PREFIX + System.nanoTime();

        Result result = service.forwardUnrecognizedReply(key, fixture("mail/human-reply.eml"));

        assertThat(result).isEqualTo(Result.FORWARDED);
        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
        verify(emailService).sendRawMessage(sent.capture(), eq(List.of(ORGANIZER)), any());
        MimeMessage forwarded = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(sent.getValue()));
        assertThat(forwarded.getHeader("Reply-To", null))
                .isEqualTo("success+registrant@simulator.amazonses.com");
        assertThat(forwarded.getSubject()).isEqualTo("Re: BATbern60 Anmeldebestaetigung");

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
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
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
        verify(emailService).sendRawMessage(any(), eq(List.of(ORGANIZER)), any());
    }

    private static byte[] fixture(String path) throws Exception {
        return new ClassPathResource(path).getContentAsByteArray();
    }
}
