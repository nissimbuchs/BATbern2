package ch.demo;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

// Counted (be-mock-email-service 3): the @Mock EmailService field and two verify(emailService ...).
// Not counted: the mentions in this comment, the Javadoc, the string and the text block.
class MailUnitTest {

    @Mock
    private EmailService emailService;

    /** verify(emailService).send(...) is what the rule forbids; @Mock EmailService too. */
    @Test
    void sends() {
        verify(emailService).sendHtmlEmail(null, null, null);
        verify(emailService, never()).sendHtmlEmail(null, null, null);
        // verify(emailService).commentedOut();
        String s = "verify(emailService) mock(EmailService.class)";
        String t = """
            @Mock EmailService inTextBlock;
            """;
    }
}
