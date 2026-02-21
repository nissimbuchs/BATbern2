package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerMaterialConfirmRequest;
import ch.batbern.events.dto.SpeakerMaterialConfirmResponse;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerPortalMaterialsService.
 * Regression tests for S3 copy-on-confirm fix.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerPortalMaterialsServiceTest {

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionMaterialsRepository sessionMaterialsRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    private SpeakerPortalMaterialsService service;

    private UUID speakerPoolId;
    private UUID sessionId;
    private UUID eventId;
    private SpeakerPool testSpeaker;
    private Session testSession;
    private String validToken;

    @BeforeEach
    void setUp() {
        service = new SpeakerPortalMaterialsService(
                magicLinkService, speakerPoolRepository, sessionRepository,
                sessionMaterialsRepository, s3Presigner, s3Client
        );
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "cloudFrontDomain", "https://cdn.test.ch");

        speakerPoolId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        validToken = "valid-token-123";

        testSpeaker = SpeakerPool.builder()
                .id(speakerPoolId)
                .eventId(eventId)
                .speakerName("Test Speaker")
                .sessionId(sessionId)
                .build();

        testSession = new Session();
        testSession.setId(sessionId);
        testSession.setEventCode("BATbern99");
        testSession.setSessionSlug("test-session");
    }

    @Nested
    @DisplayName("confirmUpload - S3 copy regression")
    class ConfirmUploadTests {

        @Test
        @DisplayName("should copy S3 object from temp to final location on confirm")
        void shouldCopyS3Object_onConfirm() {
            // Given
            String uploadId = "abc-123";
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerPoolId, "test-user", "BATbern99",
                            ch.batbern.shared.types.TokenAction.VIEW));
            when(speakerPoolRepository.findById(speakerPoolId))
                    .thenReturn(Optional.of(testSpeaker));
            when(sessionRepository.findById(sessionId))
                    .thenReturn(Optional.of(testSession));
            when(s3Client.copyObject(any(CopyObjectRequest.class)))
                    .thenReturn(CopyObjectResponse.builder().build());
            when(sessionMaterialsRepository.save(any(SessionMaterial.class)))
                    .thenAnswer(inv -> {
                        SessionMaterial m = inv.getArgument(0);
                        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
                        return m;
                    });

            SpeakerMaterialConfirmRequest request = new SpeakerMaterialConfirmRequest(
                    validToken, uploadId, "slides.pptx", "pptx",
                    5_000_000L,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "PRESENTATION"
            );

            // When
            SpeakerMaterialConfirmResponse response = service.confirmUpload(request);

            // Then - verify S3 copy was called with correct keys
            ArgumentCaptor<CopyObjectRequest> copyCaptor =
                    ArgumentCaptor.forClass(CopyObjectRequest.class);
            verify(s3Client).copyObject(copyCaptor.capture());

            CopyObjectRequest copyRequest = copyCaptor.getValue();
            assertThat(copyRequest.sourceBucket()).isEqualTo("test-bucket");
            assertThat(copyRequest.destinationBucket()).isEqualTo("test-bucket");
            assertThat(copyRequest.sourceKey())
                    .isEqualTo("materials/temp/" + uploadId + "/file-" + uploadId + ".pptx");
            assertThat(copyRequest.destinationKey())
                    .contains("events/BATbern99/sessions/test-session/file-" + uploadId + ".pptx");

            // Verify response
            assertThat(response).isNotNull();
            assertThat(response.fileName()).isEqualTo("slides.pptx");
        }

        @Test
        @DisplayName("should throw when no session assigned")
        void shouldThrow_whenNoSessionAssigned() {
            testSpeaker.setSessionId(null);

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerPoolId, "test-user", "BATbern99",
                            ch.batbern.shared.types.TokenAction.VIEW));
            when(speakerPoolRepository.findById(speakerPoolId))
                    .thenReturn(Optional.of(testSpeaker));

            SpeakerMaterialConfirmRequest request = new SpeakerMaterialConfirmRequest(
                    validToken, "abc-123", "slides.pptx", "pptx",
                    5_000_000L,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "PRESENTATION"
            );

            assertThatThrownBy(() -> service.confirmUpload(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No session assigned");
        }
    }
}
