package ch.batbern.events.mailcenter.render;

import ch.batbern.events.mailcenter.variables.MailVariableFactory;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.events.service.EventTimeResolver;
import ch.batbern.shared.service.EmailService;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;

/** Story 16.11: the real pipeline over a unit test's mocks. */
public final class MailPipelines {

    private MailPipelines() {
    }

    /** Pipeline over the given mocks; event start time 18:00. */
    public static MailPipeline over(EmailService emailService, EmailTemplateService templates) {
        EventTimeResolver resolver = Mockito.mock(EventTimeResolver.class);
        Mockito.lenient().when(resolver.formatStartTime(any())).thenReturn("18:00");
        return new MailPipeline(emailService, templates, new MailVariableFactory(resolver));
    }
}
