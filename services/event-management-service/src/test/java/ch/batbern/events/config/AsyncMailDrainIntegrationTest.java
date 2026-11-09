package ch.batbern.events.config;

import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 17.0 deferred work: the context's {@code SesRecorder} waits for the {@code @Async} executor, so a
 * send still running when a test ends is finished before the next test's {@code reset()} forgets
 * it, and a test can await an async send before asserting it.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("SesRecorder drains the @Async executor")
class AsyncMailDrainIntegrationTest extends AbstractIntegrationTest {

    private static final String TO = "success+drain@simulator.amazonses.com";

    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private EmailService emailService;
    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
    }

    @Test
    @DisplayName("awaitAsyncSends returns after a slow async send has reached SES")
    void should_recordSlowAsyncSend_when_awaited() {
        taskExecutor.execute(() -> {
            pause(400);
            emailService.sendHtmlEmailSync(TO, "late", "<p>late</p>");
        });
        assertThat(sesRecorder.mails()).as("positive control: not sent yet").isEmpty();

        sesRecorder.awaitAsyncSends();

        assertThat(sesRecorder.single().subject()).isEqualTo("late");
    }

    @Test
    @DisplayName("reset finishes a send still running, then forgets it; nothing arrives afterwards")
    void should_notLeakLateSend_when_resetWhileSending() {
        taskExecutor.execute(() -> {
            pause(400);
            emailService.sendHtmlEmailSync(TO, "late", "<p>late</p>");
        });

        sesRecorder.reset();
        pause(600);

        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @DisplayName("the @Async EmailService.sendHtmlEmail is awaited too")
    void should_recordAsyncEmailServiceSend_when_awaited() {
        emailService.sendHtmlEmail(TO, "async", "<p>async</p>");

        sesRecorder.awaitAsyncSends();

        assertThat(sesRecorder.single().subject()).isEqualTo("async");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
