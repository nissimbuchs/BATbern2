package ch.batbern.events.mailcenter;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The new-mail notice names who wrote. A From header without a display name ("x@y.ch" instead of
 * "Name <x@y.ch>") used to give "Neue Mail von ?": the notice now names the person CUMS knows for
 * that address, else the address itself. CUMS is the {@link CumsContractHarness}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail Center notice: who wrote")
class ConversationNoticeSenderIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER_ADDRESS = "success+olga.sender@simulator.amazonses.com";
    private static final String SENDER = "success+Anna.Bruennimann@simulator.amazonses.com";

    @Autowired
    private ConversationNotices conversationNotices;
    @Autowired
    private CumsContractHarness cums;

    @AfterEach
    void tearDown() {
        cums.reset();
    }

    private EmailForwardingService.Notice noticeFor(String fromName, String fromAddress) {
        return conversationNotices.forStored(new InboundMailCenter.Stored("conv-sender", null, fromName,
                fromAddress, "Referent für FinOps")).apply(ORGANIZER_ADDRESS);
    }

    @Test
    void should_nameTheDisplayName_when_theFromHeaderHasOne() {
        cums.emailOwners(SENDER, "anna.bruennimann");
        cums.user("anna.bruennimann", "Anna", "Brünnimann-Other", SENDER);

        EmailForwardingService.Notice notice = noticeFor("Hông Ân", SENDER);

        assertThat(notice.subject()).isEqualTo("Neue Mail von Hông Ân: Referent für FinOps");
        assertThat(cums.requestedUsers()).isEmpty();
    }

    @Test
    void should_nameTheKnownPerson_when_theFromHeaderHasNoDisplayName() {
        cums.emailOwners(SENDER, "anna.bruennimann");
        cums.user("anna.bruennimann", "Anna", "Brünnimann", SENDER);

        EmailForwardingService.Notice notice = noticeFor(null, SENDER);

        assertThat(notice.subject()).isEqualTo("Neue Mail von Anna Brünnimann: Referent für FinOps");
        assertThat(notice.html()).contains("Anna Brünnimann").doesNotContain("?:").doesNotContain(">?<");
    }

    @Test
    void should_nameTheAddress_when_nobodyOwnsIt() {
        EmailForwardingService.Notice notice = noticeFor("  ", SENDER);

        assertThat(notice.subject()).isEqualTo("Neue Mail von " + SENDER + ": Referent für FinOps");
        assertThat(notice.html()).contains(SENDER);
    }

    @Test
    void should_nameTheAddress_when_twoPeopleShareIt() {
        cums.emailOwners(SENDER, "anna.bruennimann", "anna.bruennimann.2");

        EmailForwardingService.Notice notice = noticeFor(null, SENDER);

        assertThat(notice.subject()).isEqualTo("Neue Mail von " + SENDER + ": Referent für FinOps");
    }

    @Test
    void should_nameTheAddress_when_theUserLookupFails() {
        cums.emailOwners(SENDER, "anna.bruennimann");
        cums.unknownUser("anna.bruennimann");

        EmailForwardingService.Notice notice = noticeFor(null, SENDER);

        assertThat(notice.subject()).isEqualTo("Neue Mail von " + SENDER + ": Referent für FinOps");
    }
}
