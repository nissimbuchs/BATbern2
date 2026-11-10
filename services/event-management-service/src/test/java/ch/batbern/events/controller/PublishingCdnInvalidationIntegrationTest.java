package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.Invalidation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publishing and unpublishing a phase invalidate the CDN paths of that phase (BAT-16 AC6, kept by
 * Story 4.9b). CloudFront is the process boundary: the real {@code CdnInvalidationService} runs with
 * the CDN enabled, and the test reads the invalidation request CloudFront received. In the other
 * tests the CDN is disabled, so {@code cdnInvalidated} there says nothing about the paths.
 * No class-level {@code @Transactional} (test fidelity rules): the requests commit, the event is
 * removed after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "aws.cloudfront.enabled=true",
    "aws.cloudfront.distribution-id=E2TESTDISTRIBUTION"
})
class PublishingCdnInvalidationIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern2031";

    @MockitoBean
    private CloudFrontClient cloudFrontClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    private String eventCode;

    @AfterEach
    void tearDown() {
        eventRepository.findByEventCode(EVENT_CODE).ifPresent(eventRepository::delete);
    }

    @BeforeEach
    void setUp() {
        tearDown();
        reset(cloudFrontClient);
        when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
                .thenReturn(CreateInvalidationResponse.builder()
                        .invalidation(Invalidation.builder().id("I2TESTINVALIDATION").build())
                        .build());
        Instant date = Instant.now().plus(60, ChronoUnit.DAYS);
        Event event = eventRepository.save(Event.builder()
                .eventCode(EVENT_CODE)
                .title("Brünnimann-Abend 2031")
                .eventNumber(2031)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(150)
                .organizerUsername("test.organizer")
                .eventType(ch.batbern.events.core.dto.generated.EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.SPEAKER_IDENTIFICATION)
                .topicCode("cloud-architecture")
                .build());
        eventCode = event.getEventCode();
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_invalidateSpeakersPaths_when_speakersPhasePublished() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/speakers/publish", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cdnInvalidated").value(true));

        assertThat(invalidatedPaths()).containsExactlyInAnyOrder(
                "/api/public/events/" + eventCode,
                "/api/public/events/" + eventCode + "/speakers",
                "/events/" + eventCode + "/*");
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_invalidateTopicPaths_when_topicPhaseUnpublished() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode))
                .andExpect(status().isOk());
        reset(cloudFrontClient);
        when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
                .thenReturn(CreateInvalidationResponse.builder()
                        .invalidation(Invalidation.builder().id("I2TESTINVALIDATION").build())
                        .build());

        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/unpublish", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cdnInvalidated").value(true));

        assertThat(invalidatedPaths()).contains("/api/public/events/" + eventCode + "/topic");
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_reportNotInvalidated_when_cloudFrontRefuses() throws Exception {
        when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
                .thenThrow(new IllegalStateException("CloudFront unavailable"));

        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cdnInvalidated").value(false));

        assertThat(eventRepository.findByEventCode(eventCode).orElseThrow().getCurrentPublishedPhase())
                .isEqualTo("topic");
    }

    private List<String> invalidatedPaths() {
        ArgumentCaptor<CreateInvalidationRequest> request = ArgumentCaptor.forClass(CreateInvalidationRequest.class);
        verify(cloudFrontClient).createInvalidation(request.capture());
        assertThat(request.getValue().distributionId()).isEqualTo("E2TESTDISTRIBUTION");
        return request.getValue().invalidationBatch().paths().items();
    }
}
