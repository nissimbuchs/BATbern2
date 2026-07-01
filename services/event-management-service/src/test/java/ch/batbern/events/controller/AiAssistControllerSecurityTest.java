package ch.batbern.events.controller;

import ch.batbern.events.ai.dto.generated.ApplyThemeImageRequest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.events.service.BatbernAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Story 15.9 — OWASP-LLM LLM08 (excessive agency) guard for {@link AiAssistController}.
 *
 * <p>An AI-generated theme-image URL must not be persisted unless it points at our own CloudFront
 * domain — AI output never drives an arbitrary side effect. This locks the
 * {@code applyThemeImage} domain-prefix check.
 */
@ExtendWith(MockitoExtension.class)
@Tag("ai-security")
class AiAssistControllerSecurityTest {

    private static final String CLOUDFRONT = "https://cdn.batbern.ch";

    @Mock
    private BatbernAiService aiService;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    private AiAssistController controller;

    @BeforeEach
    void setUp() {
        controller = new AiAssistController(aiService, eventRepository,
                topicRepository, sessionRepository, speakerPoolRepository);
        ReflectionTestUtils.setField(controller, "cloudFrontDomain", CLOUDFRONT);
    }

    @Test
    @DisplayName("applyThemeImage rejects a URL outside the CloudFront domain (no persistence)")
    void should_rejectAndNotPersist_when_imageUrlNotCloudFront() {
        ResponseEntity<Void> response = controller.applyThemeImage(
                "BATbern99",
                new ApplyThemeImageRequest("https://evil.example.com/x.png"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyThemeImage rejects a look-alike host that only prefix-matches CloudFront")
    void should_rejectAndNotPersist_when_imageUrlIsLookalikeHost() {
        ResponseEntity<Void> response = controller.applyThemeImage(
                "BATbern99",
                new ApplyThemeImageRequest("https://cdn.batbern.ch.evil.com/x.png"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyThemeImage rejects a null URL (no persistence)")
    void should_rejectAndNotPersist_when_imageUrlNull() {
        ResponseEntity<Void> response = controller.applyThemeImage(
                "BATbern99", new ApplyThemeImageRequest(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyThemeImage persists a valid CloudFront URL")
    void should_persist_when_imageUrlIsCloudFront() {
        Event event = new Event();
        org.mockito.Mockito.when(eventRepository.findByEventCode("BATbern99"))
                .thenReturn(java.util.Optional.of(event));

        ResponseEntity<Void> response = controller.applyThemeImage(
                "BATbern99",
                new ApplyThemeImageRequest(CLOUDFRONT + "/ai-themes/abc.png"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(eventRepository).save(event);
        assertThat(event.getThemeImageUrl()).isEqualTo(CLOUDFRONT + "/ai-themes/abc.png");
    }
}
