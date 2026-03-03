package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.Logo;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.dto.export.AssetManifestResponse;
import ch.batbern.events.dto.export.LegacyExportEnvelope;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LogoRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LegacyExportService.
 * Story 10.20: AC7 — TDD, written before implementation (RED phase).
 */
@ExtendWith(MockitoExtension.class)
class LegacyExportServiceTest {

    @Mock
    EventRepository eventRepository;
    @Mock
    SessionRepository sessionRepository;
    @Mock
    RegistrationRepository registrationRepository;
    @Mock
    SpeakerRepository speakerRepository;
    @Mock
    LogoRepository logoRepository;
    @Mock
    SessionMaterialsRepository sessionMaterialsRepository;
    @Mock
    UserApiClient userApiClient;
    @Mock
    S3Presigner s3Presigner;

    @InjectMocks
    LegacyExportService service;

    @BeforeEach
    void setUp() {
        // Set bucketName via reflection (Spring @Value not available in unit tests)
        try {
            var field = LegacyExportService.class.getDeclaredField("bucketName");
            field.setAccessible(true);
            field.set(service, "test-bucket");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("exportAll() with 1 event and 2 sessions → envelope contains correct events and sessions")
    void exportAll_withOneEventAndTwoSessions_containsCorrectData() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .eventCode("BATbern57")
                .eventNumber(57)
                .title("BATbern 57")
                .date(Instant.parse("2024-11-14T17:00:00Z"))
                .venueName("Hotel Bern")
                .venueAddress("Zeughausgasse 9, 3011 Bern")
                .build();

        Session session1 = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("cloud-native-arch-abc")
                .eventCode("BATbern57")
                .title("Cloud-Native Architecture")
                .sessionUsers(List.of())
                .build();
        Session session2 = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("security-patterns-def")
                .eventCode("BATbern57")
                .title("Security Patterns")
                .sessionUsers(List.of())
                .build();

        when(eventRepository.findAll()).thenReturn(List.of(event));
        when(sessionRepository.findByEventCode("BATbern57")).thenReturn(List.of(session1, session2));
        when(registrationRepository.findByEventId(eventId)).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of());
        when(userApiClient.getAllCompanies()).thenReturn(List.of());

        // Act
        LegacyExportEnvelope envelope = service.exportAll();

        // Assert
        assertThat(envelope).isNotNull();
        assertThat(envelope.getVersion()).isEqualTo("2.0");
        assertThat(envelope.getExportedAt()).isNotNull();
        assertThat(envelope.getEvents()).hasSize(1);
        assertThat(envelope.getEvents().get(0).getEventCode()).isEqualTo("BATbern57");
        assertThat(envelope.getEvents().get(0).getSessions()).hasSize(2);
        assertThat(envelope.getEvents().get(0).getSessions())
                .extracting("title")
                .containsExactlyInAnyOrder("Cloud-Native Architecture", "Security Patterns");
    }

    @Test
    @DisplayName("exportAll() with 1 speaker with portrait URL → speaker dto contains portrait")
    void exportAll_withSpeakerPortrait_containsPortraitUrl() {
        // Arrange
        UUID speakerId = UUID.randomUUID();
        Speaker speaker = Speaker.builder()
                .id(speakerId)
                .username("thomas.goetz")
                .firstName("Thomas")
                .lastName("Goetz")
                .bio("Swiss enterprise architect")
                .profilePictureUrl("https://d123.cloudfront.net/speakers/thomas.goetz.jpg")
                .build();

        when(eventRepository.findAll()).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of(speaker));
        when(userApiClient.getAllCompanies()).thenReturn(List.of());

        // Act
        LegacyExportEnvelope envelope = service.exportAll();

        // Assert
        assertThat(envelope.getSpeakers()).hasSize(1);
        assertThat(envelope.getSpeakers().get(0).getSpeakerId()).isEqualTo("thomas.goetz");
        assertThat(envelope.getSpeakers().get(0).getName()).isEqualTo("Thomas Goetz");
        assertThat(envelope.getSpeakers().get(0).getPortrait())
                .isEqualTo("https://d123.cloudfront.net/speakers/thomas.goetz.jpg");
    }

    @Test
    @DisplayName("exportAll() with no data → envelope has version, exportedAt, and empty lists (no NPE)")
    void exportAll_withNoData_returnsEmptyEnvelope() {
        // Arrange
        when(eventRepository.findAll()).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of());
        when(userApiClient.getAllCompanies()).thenReturn(List.of());

        // Act
        LegacyExportEnvelope envelope = service.exportAll();

        // Assert — no NPE, all lists empty, metadata present
        assertThat(envelope).isNotNull();
        assertThat(envelope.getVersion()).isEqualTo("2.0");
        assertThat(envelope.getExportedAt()).isNotNull();
        assertThat(envelope.getEvents()).isEmpty();
        assertThat(envelope.getCompanies()).isEmpty();
        assertThat(envelope.getSpeakers()).isEmpty();
        assertThat(envelope.getAttendees()).isEmpty();
    }

    @Test
    @DisplayName("exportAssetManifest() with 1 speaker portrait, 1 logo, 1 material → returns manifest with 3 entries")
    void exportAssetManifest_withVariousAssets_returnsCompleteManifest() throws MalformedURLException {
        // Arrange — mock S3Presigner to return a fake URL
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://presigned.s3.amazonaws.com/test"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        UUID speakerId = UUID.randomUUID();
        Speaker speaker = Speaker.builder()
                .id(speakerId)
                .username("john.doe")
                .profilePictureUrl("https://d123.cloudfront.net/speakers/john.doe.jpg")
                .build();

        UUID logoId = UUID.randomUUID();
        Logo logo = new Logo();
        logo.setId(logoId);
        logo.setS3Key("logos/companies/sbb-logo.png");
        logo.setAssociatedEntityType("company");
        logo.setAssociatedEntityId("SBB");

        UUID materialId = UUID.randomUUID();
        SessionMaterial material = new SessionMaterial();
        material.setId(materialId);
        material.setS3Key("materials/BATbern57/cloud-native-arch.pdf");

        when(speakerRepository.findAll()).thenReturn(List.of(speaker));
        when(logoRepository.findAll()).thenReturn(List.of(logo));
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of(material));

        // Act
        AssetManifestResponse manifest = service.exportAssetManifest();

        // Assert
        assertThat(manifest).isNotNull();
        assertThat(manifest.getExportedAt()).isNotNull();
        assertThat(manifest.getAssetCount()).isEqualTo(3);
        assertThat(manifest.getAssets()).hasSize(3);
        assertThat(manifest.getAssets()).allMatch(a -> a.getPresignedUrl() != null);
        assertThat(manifest.getAssets()).extracting("type")
                .containsExactlyInAnyOrder("portrait", "logo", "material");
    }

    @Test
    @DisplayName("exportAssetManifest() with speaker missing portrait → speaker is skipped gracefully")
    void exportAssetManifest_withMissingPortrait_skipsGracefully() throws MalformedURLException {
        // Arrange — speaker with no portrait URL
        Speaker speakerNoPortrait = Speaker.builder()
                .id(UUID.randomUUID())
                .username("anonymous.speaker")
                .profilePictureUrl(null)
                .build();

        when(speakerRepository.findAll()).thenReturn(List.of(speakerNoPortrait));
        when(logoRepository.findAll()).thenReturn(List.of());
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of());

        // Act
        AssetManifestResponse manifest = service.exportAssetManifest();

        // Assert — no error, speaker with null portrait is skipped
        assertThat(manifest).isNotNull();
        assertThat(manifest.getAssetCount()).isEqualTo(0);
        assertThat(manifest.getAssets()).isEmpty();
    }
}
