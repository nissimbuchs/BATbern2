package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.Logo;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.dto.export.AssetManifestResponse;
import ch.batbern.events.dto.export.LegacyExportEnvelope;
import ch.batbern.events.dto.generated.users.Company;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LogoRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

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
    @Mock
    S3Client s3Client;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

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
    @DisplayName("exportAll() with speaker → company name populated from User service")
    void exportAll_withSpeaker_enrichesCompanyFromUserService() throws MalformedURLException {
        // Arrange
        Speaker speaker = Speaker.builder()
                .id(UUID.randomUUID())
                .username("anna.schmidt")
                .firstName("Anna")
                .lastName("Schmidt")
                .build();

        Company company = new Company();
        company.setDisplayName("SBB AG");
        UserResponse userResponse = new UserResponse();
        userResponse.setCompany(company);
        userResponse.setBio("Cloud architect at SBB");

        when(eventRepository.findAll()).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of(speaker));
        when(userApiClient.getAllCompanies()).thenReturn(List.of());
        when(userApiClient.getUserByUsername("anna.schmidt")).thenReturn(userResponse);

        // Act
        LegacyExportEnvelope envelope = service.exportAll();

        // Assert
        assertThat(envelope.getSpeakers()).hasSize(1);
        assertThat(envelope.getSpeakers().get(0).getCompany()).isEqualTo("SBB AG");
        assertThat(envelope.getSpeakers().get(0).getBio()).isEqualTo("Cloud architect at SBB");
    }

    @Test
    @DisplayName("exportAll() with session having materials → session dto contains materialUrls")
    void exportAll_withSessionMaterials_includesMaterialUrlsInSession() throws MalformedURLException {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .eventCode("BATbern57")
                .eventNumber(57)
                .title("BATbern 57")
                .date(Instant.now())
                .build();
        Session session = Session.builder()
                .id(sessionId)
                .sessionSlug("cloud-arch")
                .eventCode("BATbern57")
                .title("Cloud Architecture")
                .sessionUsers(List.of())
                .build();
        SessionMaterial material = new SessionMaterial();
        material.setId(UUID.randomUUID());
        material.setSessionId(sessionId);
        material.setS3Key("materials/BATbern57/cloud-arch.pdf");

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://presigned.s3.amazonaws.com/cloud-arch.pdf"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        when(eventRepository.findAll()).thenReturn(List.of(event));
        when(sessionRepository.findByEventCode("BATbern57")).thenReturn(List.of(session));
        when(registrationRepository.findByEventId(any())).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of());
        when(userApiClient.getAllCompanies()).thenReturn(List.of());
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of(material));

        // Act
        LegacyExportEnvelope envelope = service.exportAll();

        // Assert
        assertThat(envelope.getEvents()).hasSize(1);
        var sessions = envelope.getEvents().get(0).getSessions();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getMaterialUrls())
                .containsExactly("https://presigned.s3.amazonaws.com/cloud-arch.pdf");
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
        when(eventRepository.findAll()).thenReturn(List.of());

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
        when(eventRepository.findAll()).thenReturn(List.of());

        // Act
        AssetManifestResponse manifest = service.exportAssetManifest();

        // Assert — no error, speaker with null portrait is skipped
        assertThat(manifest).isNotNull();
        assertThat(manifest.getAssetCount()).isEqualTo(0);
        assertThat(manifest.getAssets()).isEmpty();
    }

    @Test
    @DisplayName("exportAssetManifest() with S3 presigner failure for one entry → skips that entry, returns rest")
    void exportAssetManifest_withPresignerFailureForOneEntry_skipsGracefully() throws MalformedURLException {
        // Arrange — presigner throws for any call (simulates S3 unavailable or bad key)
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("S3 presign failed"));

        UUID logoId = UUID.randomUUID();
        Logo logo = new Logo();
        logo.setId(logoId);
        logo.setS3Key("logos/companies/sbb-logo.png");

        when(speakerRepository.findAll()).thenReturn(List.of());
        when(logoRepository.findAll()).thenReturn(List.of(logo));
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of());
        when(eventRepository.findAll()).thenReturn(List.of());

        // Act — should NOT throw, should return manifest with 0 entries (skipped)
        AssetManifestResponse manifest = service.exportAssetManifest();

        // Assert
        assertThat(manifest).isNotNull();
        assertThat(manifest.getAssetCount()).isEqualTo(0);
        assertThat(manifest.getAssets()).isEmpty();
    }

    @Test
    @DisplayName("exportAssetManifest() with event theme image → manifest contains theme entry")
    void exportAssetManifest_withEventThemeImage_includesThemeEntry() throws MalformedURLException {
        // Arrange
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://presigned.s3.amazonaws.com/theme"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .eventCode("BATbern57")
                .themeImageUrl("https://d123.cloudfront.net/themes/batbern57.jpg")
                .build();

        when(speakerRepository.findAll()).thenReturn(List.of());
        when(logoRepository.findAll()).thenReturn(List.of());
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of());
        when(eventRepository.findAll()).thenReturn(List.of(event));

        // Act
        AssetManifestResponse manifest = service.exportAssetManifest();

        // Assert
        assertThat(manifest.getAssetCount()).isEqualTo(1);
        assertThat(manifest.getAssets()).hasSize(1);
        assertThat(manifest.getAssets().get(0).getType()).isEqualTo("theme");
        assertThat(manifest.getAssets().get(0).getEntityId()).isEqualTo(eventId);
        assertThat(manifest.getAssets().get(0).getFilename()).isEqualTo("batbern57.jpg");
    }

    @Test
    @DisplayName("exportAssetManifest() with event without theme image → event is skipped gracefully")
    void exportAssetManifest_withEventWithoutThemeImage_skipsGracefully() {
        // Arrange
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .eventCode("BATbern57")
                .themeImageUrl(null)
                .build();

        when(speakerRepository.findAll()).thenReturn(List.of());
        when(logoRepository.findAll()).thenReturn(List.of());
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of());
        when(eventRepository.findAll()).thenReturn(List.of(event));

        // Act
        AssetManifestResponse manifest = service.exportAssetManifest();

        // Assert
        assertThat(manifest.getAssetCount()).isEqualTo(0);
        assertThat(manifest.getAssets()).isEmpty();
    }

    // ── Bundle export tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("exportBundle() with no data → ZIP contains export.json, manifest.json, and material-links.json")
    void exportBundle_withNoData_producesZipWithJsonFiles() throws IOException {
        // Arrange — empty repos, no assets
        when(eventRepository.findAll()).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of());
        when(userApiClient.getAllCompanies()).thenReturn(List.of());
        when(logoRepository.findAll()).thenReturn(List.of());
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of());

        // Act
        byte[] zip = service.exportBundle();

        // Assert — valid ZIP, contains all three JSON files
        assertThat(zip).isNotEmpty();
        List<String> entries = listZipEntries(zip);
        assertThat(entries).contains("export.json", "manifest.json", "material-links.json");
    }

    @Test
    @DisplayName("exportBundle() with assets → ZIP contains asset files in typed subdirectories, no materials/ dir")
    void exportBundle_withAssets_includesAssetFilesInZip() throws IOException {
        // Arrange
        UUID speakerId = UUID.randomUUID();
        Speaker speaker = Speaker.builder()
                .id(speakerId)
                .username("anna.mueller")
                .firstName("Anna")
                .lastName("Mueller")
                .profilePictureUrl("https://d123.cloudfront.net/portraits/anna.jpg")
                .build();

        UUID logoId = UUID.randomUUID();
        Logo logo = new Logo();
        logo.setId(logoId);
        logo.setS3Key("logos/companies/sbb-logo.png");

        ResponseBytes<GetObjectResponse> fakeBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), "img-bytes".getBytes());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(fakeBytes);

        when(eventRepository.findAll()).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of(speaker));
        when(userApiClient.getAllCompanies()).thenReturn(List.of());
        when(logoRepository.findAll()).thenReturn(List.of(logo));
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of());

        // Act
        byte[] zip = service.exportBundle();

        // Assert
        List<String> entries = listZipEntries(zip);
        assertThat(entries).contains("export.json", "manifest.json", "material-links.json");
        // portrait entry should be under portraits/{speakerId}/
        assertThat(entries).anyMatch(e -> e.startsWith("portraits/") && e.endsWith("anna.jpg"));
        // logo entry under logos/
        assertThat(entries).anyMatch(e -> e.startsWith("logos/") && e.endsWith("sbb-logo.png"));
        // PDFs must NOT be embedded
        assertThat(entries).noneMatch(e -> e.startsWith("materials/"));
    }

    @Test
    @DisplayName("exportBundle() with S3 download failure → skips asset, export.json still present")
    void exportBundle_withS3Failure_skipsAssetAndContinues() throws IOException {
        // Arrange
        UUID logoId = UUID.randomUUID();
        Logo logo = new Logo();
        logo.setId(logoId);
        logo.setS3Key("logos/companies/failing-logo.png");

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 download failed"));

        when(eventRepository.findAll()).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of());
        when(userApiClient.getAllCompanies()).thenReturn(List.of());
        when(logoRepository.findAll()).thenReturn(List.of(logo));
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of());

        // Act — should NOT throw
        byte[] zip = service.exportBundle();

        // Assert — export.json present, but asset skipped
        List<String> entries = listZipEntries(zip);
        assertThat(entries).contains("export.json");
        assertThat(entries).noneMatch(e -> e.contains("failing-logo.png"));
    }

    @Test
    @DisplayName("exportBundle() with 1 material → material-links.json contains the material's presigned URL")
    void exportBundle_includesMaterialLinksJson() throws Exception {
        // Arrange
        UUID materialId = UUID.randomUUID();
        SessionMaterial material = new SessionMaterial();
        material.setId(materialId);
        material.setS3Key("materials/BATbern57/cloud-native-arch.pdf");

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://presigned.s3.amazonaws.com/material-pdf"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        when(eventRepository.findAll()).thenReturn(List.of());
        when(speakerRepository.findAll()).thenReturn(List.of());
        when(userApiClient.getAllCompanies()).thenReturn(List.of());
        when(logoRepository.findAll()).thenReturn(List.of());
        when(sessionMaterialsRepository.findAll()).thenReturn(List.of(material));

        // Act
        byte[] zip = service.exportBundle();

        // Assert — material-links.json is present
        List<String> entries = listZipEntries(zip);
        assertThat(entries).contains("material-links.json");
        // PDFs are NOT embedded
        assertThat(entries).noneMatch(e -> e.startsWith("materials/"));

        // Parse material-links.json and verify it contains the presigned URL
        byte[] materialLinksBytes = readZipEntry(zip, "material-links.json");
        String json = new String(materialLinksBytes);
        assertThat(json).contains("https://presigned.s3.amazonaws.com/material-pdf");
        assertThat(json).contains("material");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private List<String> listZipEntries(byte[] zip) throws IOException {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }

    private byte[] readZipEntry(byte[] zip, String entryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        throw new java.util.NoSuchElementException("Entry not found in ZIP: " + entryName);
    }
}
