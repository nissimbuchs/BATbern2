package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.CompanyBasicDto;
import ch.batbern.events.dto.export.AssetEntry;
import ch.batbern.events.dto.export.AssetManifestResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.dto.export.LegacyAttendeeDto;
import ch.batbern.events.dto.export.LegacyCompanyDto;
import ch.batbern.events.dto.export.LegacyEventDto;
import ch.batbern.events.dto.export.LegacyExportEnvelope;
import ch.batbern.events.dto.export.LegacySessionDto;
import ch.batbern.events.dto.export.LegacySpeakerDto;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LogoRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for exporting all BATbern data in the legacy BAT JSON format.
 * Story 10.20: AC1 (JSON export envelope) and AC2 (asset manifest).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyExportService {

    private static final String EXPORT_VERSION = "2.0";

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final RegistrationRepository registrationRepository;
    private final SpeakerRepository speakerRepository;
    private final LogoRepository logoRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final UserApiClient userApiClient;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public LegacyExportEnvelope exportAll() {
        List<Event> events = eventRepository.findAll();
        List<Speaker> speakers = speakerRepository.findAll();
        List<CompanyBasicDto> companies = userApiClient.getAllCompanies();

        // Pre-build session material presigned URL map (avoids N+1 in session mapping)
        Map<UUID, List<String>> materialUrlsBySessionId = buildMaterialUrlMap();

        List<LegacyEventDto> legacyEvents = events.stream()
                .map(e -> mapEvent(e, materialUrlsBySessionId))
                .collect(Collectors.toList());

        List<LegacyAttendeeDto> attendees = events.stream()
                .flatMap(e -> registrationRepository.findByEventId(e.getId()).stream()
                        .map(r -> mapAttendee(r, e.getEventCode())))
                .collect(Collectors.toList());

        return LegacyExportEnvelope.builder()
                .version(EXPORT_VERSION)
                .exportedAt(Instant.now())
                .events(legacyEvents)
                .companies(companies.stream().map(this::mapCompany).collect(Collectors.toList()))
                .speakers(speakers.stream().map(this::mapSpeaker).collect(Collectors.toList()))
                .attendees(attendees)
                .build();
    }

    private Map<UUID, List<String>> buildMaterialUrlMap() {
        Map<UUID, List<String>> result = new HashMap<>();
        sessionMaterialsRepository.findAll().forEach(m -> {
            if (m.getSessionId() == null || m.getS3Key() == null) {
                return;
            }
            String url = presignMaterialUrl(m);
            if (url != null) {
                result.computeIfAbsent(m.getSessionId(), k -> new ArrayList<>()).add(url);
            }
        });
        return result;
    }

    private String presignMaterialUrl(SessionMaterial material) {
        try {
            return buildAssetEntry("material", material.getId(), material.getS3Key()).getPresignedUrl();
        } catch (Exception e) {
            log.warn("Failed to presign material {}: {}", material.getId(), e.getMessage());
            return null;
        }
    }

    public AssetManifestResponse exportAssetManifest() {
        List<AssetEntry> assets = new ArrayList<>();

        speakerRepository.findAll().stream()
                .filter(s -> s.getProfilePictureUrl() != null)
                .forEach(s -> {
                    String s3Key = extractS3KeyFromUrl(s.getProfilePictureUrl());
                    safeAddAsset(assets, "portrait", s.getId(), s3Key);
                });

        logoRepository.findAll().stream()
                .filter(l -> l.getS3Key() != null)
                .forEach(l -> safeAddAsset(assets, "logo", l.getId(), l.getS3Key()));

        sessionMaterialsRepository.findAll().stream()
                .filter(m -> m.getS3Key() != null)
                .forEach(m -> safeAddAsset(assets, "material", m.getId(), m.getS3Key()));

        eventRepository.findAll().stream()
                .filter(e -> e.getThemeImageUrl() != null)
                .forEach(e -> {
                    String s3Key = extractS3KeyFromUrl(e.getThemeImageUrl());
                    safeAddAsset(assets, "theme", e.getId(), s3Key);
                });

        return AssetManifestResponse.builder()
                .exportedAt(Instant.now())
                .assetCount(assets.size())
                .assets(assets)
                .build();
    }

    /**
     * Exports all data + binary assets as a single ZIP bundle.
     * ZIP structure:
     *   export.json          – LegacyExportEnvelope (same as /export/legacy)
     *   manifest.json        – AssetManifestResponse (presigned URL listing for reference)
     *   portraits/{id}/{filename}
     *   logos/{id}/{filename}
     *   materials/{id}/{filename}
     *   themes/{id}/{filename}
     *
     * S3 download failures are logged and skipped (partial bundle still valid).
     */
    public byte[] exportBundle() throws IOException {
        LegacyExportEnvelope envelope = exportAll();
        AssetManifestResponse manifest = exportAssetManifest();

        // PDFs are not embedded — collect presigned links for the dedicated file
        List<AssetEntry> materialLinks = manifest.getAssets().stream()
                .filter(a -> "material".equals(a.getType()))
                .collect(Collectors.toList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addZipEntry(zos, "export.json", objectMapper.writeValueAsBytes(envelope));
            addZipEntry(zos, "manifest.json", objectMapper.writeValueAsBytes(manifest));
            addZipEntry(zos, "material-links.json", objectMapper.writeValueAsBytes(materialLinks));
            addBundleAssets(zos);
        }
        return baos.toByteArray();
    }

    private void addBundleAssets(ZipOutputStream zos) {
        speakerRepository.findAll().stream()
                .filter(s -> s.getProfilePictureUrl() != null)
                .forEach(s -> {
                    String s3Key = extractS3KeyFromUrl(s.getProfilePictureUrl());
                    if (!s3Key.contains("://")) {
                        String filename = s3Key.substring(s3Key.lastIndexOf('/') + 1);
                        safeAddS3ToZip(zos, "portraits/" + s.getId() + "/" + filename, s3Key);
                    }
                });

        logoRepository.findAll().stream()
                .filter(l -> l.getS3Key() != null)
                .forEach(l -> {
                    String filename = l.getS3Key().substring(l.getS3Key().lastIndexOf('/') + 1);
                    safeAddS3ToZip(zos, "logos/" + l.getId() + "/" + filename, l.getS3Key());
                });

        eventRepository.findAll().stream()
                .filter(e -> e.getThemeImageUrl() != null)
                .forEach(e -> {
                    String s3Key = extractS3KeyFromUrl(e.getThemeImageUrl());
                    if (!s3Key.contains("://")) {
                        String filename = s3Key.substring(s3Key.lastIndexOf('/') + 1);
                        safeAddS3ToZip(zos, "themes/" + e.getId() + "/" + filename, s3Key);
                    }
                });
    }

    private void safeAddS3ToZip(ZipOutputStream zos, String zipPath, String s3Key) {
        try {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucketName).key(s3Key).build()
            ).asByteArray();
            addZipEntry(zos, zipPath, bytes);
        } catch (Exception e) {
            log.warn("Skipping asset {} in bundle: {}", zipPath, e.getMessage());
        }
    }

    private void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private LegacyEventDto mapEvent(Event event, Map<UUID, List<String>> materialUrlsBySessionId) {
        List<Session> sessions = sessionRepository.findByEventCode(event.getEventCode());
        List<LegacySessionDto> legacySessions = sessions.stream()
                .map(s -> mapSession(s, materialUrlsBySessionId))
                .collect(Collectors.toList());

        return LegacyEventDto.builder()
                .bat(event.getEventNumber())
                .eventCode(event.getEventCode())
                .title(event.getTitle())
                .date(event.getDate())
                .venueName(event.getVenueName())
                .venueAddress(event.getVenueAddress())
                .sessions(legacySessions)
                .build();
    }

    private LegacySessionDto mapSession(Session session, Map<UUID, List<String>> materialUrlsBySessionId) {
        List<LegacySpeakerDto> referenten = session.getSessionUsers() == null ? List.of()
                : session.getSessionUsers().stream()
                        .map(this::mapSessionUserToSpeaker)
                        .collect(Collectors.toList());

        List<String> materialUrls = materialUrlsBySessionId.getOrDefault(session.getId(), List.of());

        return LegacySessionDto.builder()
                .sessionSlug(session.getSessionSlug())
                .title(session.getTitle())
                .description(session.getDescription())
                .sessionType(session.getSessionType())
                .materialUrls(materialUrls.isEmpty() ? null : materialUrls)
                .referenten(referenten)
                .build();
    }

    private LegacySpeakerDto mapSessionUserToSpeaker(SessionUser su) {
        String firstName = su.getSpeakerFirstName() != null ? su.getSpeakerFirstName() : "";
        String lastName = su.getSpeakerLastName() != null ? " " + su.getSpeakerLastName() : "";
        return LegacySpeakerDto.builder()
                .speakerId(su.getUsername())
                .name((firstName + lastName).trim())
                .build();
    }

    private LegacySpeakerDto mapSpeaker(Speaker speaker) {
        String firstName = speaker.getFirstName() != null ? speaker.getFirstName() : "";
        String lastName = speaker.getLastName() != null ? " " + speaker.getLastName() : "";
        String bio = speaker.getBio();
        String portrait = speaker.getProfilePictureUrl();
        String company = null;

        // Enrich with User service data (company always from there; bio/portrait as fallback)
        try {
            UserResponse user = userApiClient.getUserByUsername(speaker.getUsername());
            if (bio == null) {
                bio = user.getBio();
            }
            if (portrait == null && user.getProfilePictureUrl() != null) {
                portrait = user.getProfilePictureUrl().toString();
            }
            if (user.getCompany() != null) {
                company = user.getCompany().getDisplayName();
            }
        } catch (Exception e) {
            log.warn("Could not enrich speaker {} from User service: {}", speaker.getUsername(), e.getMessage());
        }

        return LegacySpeakerDto.builder()
                .speakerId(speaker.getUsername())
                .name((firstName + lastName).trim())
                .bio(bio)
                .company(company)
                .portrait(portrait)
                .linkedInUrl(speaker.getLinkedInUrl())
                .twitterHandle(speaker.getTwitterHandle())
                .build();
    }

    private LegacyCompanyDto mapCompany(CompanyBasicDto dto) {
        return LegacyCompanyDto.builder()
                .id(dto.getName())
                .displayName(dto.getDisplayName())
                .url(dto.getWebsite())
                .build();
    }

    private LegacyAttendeeDto mapAttendee(Registration registration, String eventCode) {
        return LegacyAttendeeDto.builder()
                .eventCode(eventCode)
                .username(registration.getAttendeeUsername())
                .status(registration.getStatus())
                .registeredAt(registration.getRegistrationDate())
                .build();
    }

    private void safeAddAsset(List<AssetEntry> assets, String type, UUID entityId, String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            log.warn("Skipping {} asset for entity {}: s3Key is blank", type, entityId);
            return;
        }
        if (s3Key.contains("://")) {
            log.warn("Skipping {} asset for entity {}: s3Key looks like a URL, not a key: {}", type, entityId, s3Key);
            return;
        }
        try {
            assets.add(buildAssetEntry(type, entityId, s3Key));
        } catch (Exception e) {
            log.warn("Skipping {} asset for entity {}: presign failed: {}", type, entityId, e.getMessage());
        }
    }

    private AssetEntry buildAssetEntry(String type, UUID entityId, String s3Key) {
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(req -> req.bucket(bucketName).key(s3Key).build())
                .build();
        String presignedUrl = s3Presigner.presignGetObject(presign).url().toString();
        String filename = s3Key.substring(s3Key.lastIndexOf('/') + 1);
        return AssetEntry.builder()
                .type(type)
                .entityId(entityId)
                .filename(filename)
                .presignedUrl(presignedUrl)
                .build();
    }

    private String extractS3KeyFromUrl(String cloudFrontUrl) {
        try {
            String path = URI.create(cloudFrontUrl).getPath();
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (Exception e) {
            log.warn("Failed to extract S3 key from CloudFront URL: {}", cloudFrontUrl);
            return cloudFrontUrl;
        }
    }
}
