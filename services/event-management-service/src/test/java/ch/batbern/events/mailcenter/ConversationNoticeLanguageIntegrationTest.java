package ch.batbern.events.mailcenter;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1222: the Mail Center notices to an organizer follow the one language rule: German for German,
 * English for every other language (before, every language but {@code en} got German). CUMS is the
 * {@link CumsContractHarness}, so the real {@code UserApiClient} reads the organizer's language.
 * The assignment notice is asserted on the {@link SesRecorder}; the new-mail notice is built here
 * and sent by the forwarding path, so its rendered text is asserted.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail Center notices in the organizer's language")
class ConversationNoticeLanguageIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "olga.organizer";
    private static final String ADDRESS = "success+olga.notice@simulator.amazonses.com";

    @Autowired
    private ConversationAssignmentNotifier assignmentNotifier;
    @Autowired
    private ConversationNotices conversationNotices;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
    }

    @AfterEach
    void tearDown() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
    }

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @CsvSource({"fr, Assigned to you:", "en, Assigned to you:", "de, Dir zugewiesen:"})
    void should_notifyAssignmentInOrganizerLanguage(String language, String subjectStart) {
        cums.userWithSettings(ORGANIZER, ADDRESS, Map.of("language", language));
        MailConversation conversation = new MailConversation();
        conversation.setConversationKey("conv-1222");
        conversation.setSubject("Frage zu den Folien");
        conversation.setCounterpartName("Sara Speaker");
        conversation.setCounterpartAddress("success+sara1222@simulator.amazonses.com");

        assignmentNotifier.notifyAssigned(conversation, ORGANIZER);

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(ADDRESS)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).startsWith(subjectStart));
    }

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @CsvSource({"fr, New mail", "en, New mail", "de, Neue Mail"})
    void should_buildNewMailNoticeInOrganizerLanguage(String language, String subjectStart) {
        cums.usersWithRole("ORGANIZER", ORGANIZER);
        cums.userWithSettings(ORGANIZER, ADDRESS, Map.of("language", language));

        EmailForwardingService.Notice notice = conversationNotices.forStored(
                new InboundMailCenter.Stored("conv-1222", null, "Sara Speaker",
                        "success+sara1222@simulator.amazonses.com", "Frage")).apply(ADDRESS);

        assertThat(notice.subject()).startsWith(subjectStart);
    }
}
