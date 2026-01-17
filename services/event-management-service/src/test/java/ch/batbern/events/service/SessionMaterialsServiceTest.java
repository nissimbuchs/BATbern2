package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.dto.SessionMaterialAssociationRequest;
import ch.batbern.events.dto.SessionMaterialResponse;
import ch.batbern.events.event.SessionMaterialsUploadedEvent;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.exception.MaterialNotFoundException;
import ch.batbern.events.exception.InvalidUploadIdException;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionMaterialsService - Story 5.9 Task 2a (RED Phase).
 *
 * Tests service logic with mocked repository and S3Client.
 * Validates ADR-002 compliance: Generic File Upload Service integration.
 *
 * TDD RED Phase: All tests should FAIL initially as service doesn't exist yet.
 */
@ExtendWith(MockitoExtension.class)
class SessionMaterialsServiceTest {

    @Mock
    private SessionMaterialsRepository sessionMaterialsRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private SessionMaterialsService sessionMaterialsService;

    private Session mockSession;
    private Event mockEvent;

    @BeforeEach
    void setUp() {
        mockEvent = Event.builder()
                .id(UUID.randomUUID())
                .eventCode("bat-bern-2026-spring")
                .title("BATbern Spring 2026")
                .eventNumber(42)
                .build();

        mockSession = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("keynote-digital-transformation")
                .title("Keynote: Digital Transformation")
                .eventId(mockEvent.getId())
                .eventCode("bat-bern-2026-spring")
                .materialsCount(0)
                .hasPresentation(false)
                .build();
    }

    // Test 2.1: associateMaterialWithSession() - Success Case
    @Test
    void should_associateMaterials_when_validUploadIdsProvided() {
        // Given
        String sessionSlug = "keynote-digital-transformation";
        List<String> uploadIds = Arrays.asList("upload-1", "upload-2");
        List<String> materialTypes = Arrays.asList("PRESENTATION", "DOCUMENT");
        String uploadedBy = "john.doe";

        SessionMaterialAssociationRequest request = SessionMaterialAssociationRequest.builder()
                .uploadIds(uploadIds)
                .materialTypes(materialTypes)
                .build();

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(mockSession));

        // Mock materials to be saved
        SessionMaterial material1 = SessionMaterial.builder()
                .id(UUID.randomUUID())
                .session(mockSession)
                .uploadId("upload-1")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/keynote-digital-transformation/file-1.pptx")
                .cloudFrontUrl("https://cdn.batbern.ch/materials/2026/events/bat-bern-2026-spring/sessions/keynote-digital-transformation/file-1.pptx")
                .fileName("presentation.pptx")
                .fileExtension("pptx")
                .fileSize(5242880L)
                .mimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .materialType("PRESENTATION")
                .uploadedBy(uploadedBy)
                .contentExtracted(false)
                .extractionStatus("PENDING")
                .createdAt(Instant.now())
                .build();

        when(sessionMaterialsRepository.saveAll(anyList())).thenReturn(Arrays.asList(material1));

        // When
        List<SessionMaterialResponse> responses = sessionMaterialsService.associateMaterialsWithSession(
                sessionSlug, request, uploadedBy);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getUploadId()).isEqualTo("upload-1");
        assertThat(responses.get(0).getMaterialType()).isEqualTo("PRESENTATION");

        verify(sessionRepository).findBySessionSlug(sessionSlug);
        verify(sessionMaterialsRepository).saveAll(anyList());
        verify(s3Client, times(2)).copyObject(any(CopyObjectRequest.class)); // Copy from temp to final location
        verify(domainEventPublisher).publish(any(SessionMaterialsUploadedEvent.class));
    }

    // Test 2.2: associateMaterialWithSession() - Session Not Found
    @Test
    void should_throwSessionNotFoundException_when_sessionSlugInvalid() {
        // Given
        String invalidSessionSlug = "non-existent-session";
        SessionMaterialAssociationRequest request = SessionMaterialAssociationRequest.builder()
                .uploadIds(Arrays.asList("upload-1"))
                .materialTypes(Arrays.asList("PRESENTATION"))
                .build();

        when(sessionRepository.findBySessionSlug(invalidSessionSlug)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() ->
                sessionMaterialsService.associateMaterialsWithSession(invalidSessionSlug, request, "john.doe"))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining(invalidSessionSlug);

        verify(sessionRepository).findBySessionSlug(invalidSessionSlug);
        verify(sessionMaterialsRepository, never()).saveAll(anyList());
        verify(domainEventPublisher, never()).publish(any());
    }

    // Test 2.3: associateMaterialWithSession() - Invalid Upload ID
    @Test
    void should_throwInvalidUploadIdException_when_uploadIdNotFound() {
        // Given - This test assumes we validate upload IDs against Logo/Upload table
        // For now, we'll skip this test as the story doesn't specify this validation
        // TODO: Revisit if upload ID validation is needed against GenericLogoService
    }

    // Test 2.4: Domain Event Emission
    @Test
    void should_emitSessionMaterialsUploadedEvent_when_materialAssociated() {
        // Given
        String sessionSlug = "keynote-digital-transformation";
        SessionMaterialAssociationRequest request = SessionMaterialAssociationRequest.builder()
                .uploadIds(Arrays.asList("upload-1"))
                .materialTypes(Arrays.asList("PRESENTATION"))
                .build();
        String uploadedBy = "john.doe";

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(mockSession));
        when(sessionMaterialsRepository.saveAll(anyList())).thenReturn(Arrays.asList(SessionMaterial.builder().build()));

        // When
        sessionMaterialsService.associateMaterialsWithSession(sessionSlug, request, uploadedBy);

        // Then
        ArgumentCaptor<SessionMaterialsUploadedEvent> eventCaptor =
                ArgumentCaptor.forClass(SessionMaterialsUploadedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());

        SessionMaterialsUploadedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getSessionSlug()).isEqualTo(sessionSlug);
        assertThat(capturedEvent.getEventCode()).isEqualTo("bat-bern-2026-spring");
        assertThat(capturedEvent.getUploadedBy()).isEqualTo(uploadedBy);
    }

    // Test 2.5: getMaterialsBySession() - Success
    @Test
    void should_returnMaterialsList_when_sessionHasMaterials() {
        // Given
        String sessionSlug = "keynote-digital-transformation";

        SessionMaterial material1 = SessionMaterial.builder()
                .id(UUID.randomUUID())
                .session(mockSession)
                .uploadId("upload-1")
                .fileName("slides.pptx")
                .materialType("PRESENTATION")
                .fileSize(5242880L)
                .build();

        SessionMaterial material2 = SessionMaterial.builder()
                .id(UUID.randomUUID())
                .session(mockSession)
                .uploadId("upload-2")
                .fileName("handout.pdf")
                .materialType("DOCUMENT")
                .fileSize(1048576L)
                .build();

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(mockSession));
        when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(mockSession.getId()))
                .thenReturn(Arrays.asList(material1, material2));

        // When
        List<SessionMaterialResponse> responses = sessionMaterialsService.getMaterialsBySession(sessionSlug);

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getFileName()).isEqualTo("slides.pptx");
        assertThat(responses.get(0).getMaterialType()).isEqualTo("PRESENTATION");
        assertThat(responses.get(1).getFileName()).isEqualTo("handout.pdf");
        assertThat(responses.get(1).getMaterialType()).isEqualTo("DOCUMENT");

        verify(sessionRepository).findBySessionSlug(sessionSlug);
        verify(sessionMaterialsRepository).findBySession_IdOrderByCreatedAtAsc(mockSession.getId());
    }

    // Test 2.6: getMaterialsBySession() - Empty List
    @Test
    void should_returnEmptyList_when_sessionHasNoMaterials() {
        // Given
        String sessionSlug = "keynote-digital-transformation";

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(mockSession));
        when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(mockSession.getId()))
                .thenReturn(Arrays.asList());

        // When
        List<SessionMaterialResponse> responses = sessionMaterialsService.getMaterialsBySession(sessionSlug);

        // Then
        assertThat(responses).isEmpty();
        verify(sessionRepository).findBySessionSlug(sessionSlug);
    }

    // Test 2.7: getMaterialsBySession() - Session Not Found
    @Test
    void should_throwSessionNotFoundException_when_gettingMaterialsForInvalidSession() {
        // Given
        String invalidSessionSlug = "non-existent-session";
        when(sessionRepository.findBySessionSlug(invalidSessionSlug)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() ->
                sessionMaterialsService.getMaterialsBySession(invalidSessionSlug))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining(invalidSessionSlug);

        verify(sessionRepository).findBySessionSlug(invalidSessionSlug);
        verify(sessionMaterialsRepository, never()).findBySession_IdOrderByCreatedAtAsc(any());
    }

    // Test 2.8: deleteMaterial() - Success
    @Test
    void should_deleteMaterial_when_validMaterialIdProvided() {
        // Given
        UUID materialId = UUID.randomUUID();
        String sessionSlug = "keynote-digital-transformation";
        String username = "john.doe";

        SessionMaterial material = SessionMaterial.builder()
                .id(materialId)
                .session(mockSession)
                .uploadId("upload-1")
                .s3Key("materials/2026/events/bat-bern-2026-spring/sessions/keynote-digital-transformation/file-1.pptx")
                .fileName("slides.pptx")
                .uploadedBy(username)
                .build();

        when(sessionMaterialsRepository.findById(materialId)).thenReturn(Optional.of(material));

        // When
        sessionMaterialsService.deleteMaterial(sessionSlug, materialId, username);

        // Then
        verify(sessionMaterialsRepository).findById(materialId);
        verify(sessionMaterialsRepository).delete(material);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class)); // S3 cleanup
    }

    // Test 2.9: deleteMaterial() - Material Not Found
    @Test
    void should_throwMaterialNotFoundException_when_materialIdInvalid() {
        // Given
        UUID invalidMaterialId = UUID.randomUUID();
        String sessionSlug = "keynote-digital-transformation";
        String username = "john.doe";

        when(sessionMaterialsRepository.findById(invalidMaterialId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() ->
                sessionMaterialsService.deleteMaterial(sessionSlug, invalidMaterialId, username))
                .isInstanceOf(MaterialNotFoundException.class)
                .hasMessageContaining(invalidMaterialId.toString());

        verify(sessionMaterialsRepository).findById(invalidMaterialId);
        verify(sessionMaterialsRepository, never()).delete(any());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    // Test 2.10: deleteMaterial() - Session Mismatch
    @Test
    void should_throwIllegalArgumentException_when_materialDoesNotBelongToSession() {
        // Given
        UUID materialId = UUID.randomUUID();
        String requestedSessionSlug = "keynote-digital-transformation";
        String username = "john.doe";

        Session differentSession = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("different-session")
                .build();

        SessionMaterial material = SessionMaterial.builder()
                .id(materialId)
                .session(differentSession)
                .uploadedBy(username)
                .build();

        when(sessionMaterialsRepository.findById(materialId)).thenReturn(Optional.of(material));

        // When/Then
        assertThatThrownBy(() ->
                sessionMaterialsService.deleteMaterial(requestedSessionSlug, materialId, username))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Material does not belong to the specified session");

        verify(sessionMaterialsRepository).findById(materialId);
        verify(sessionMaterialsRepository, never()).delete(any());
    }
}
