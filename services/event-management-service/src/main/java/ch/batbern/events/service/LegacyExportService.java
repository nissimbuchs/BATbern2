package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.CompanyBasicDto;
import ch.batbern.events.dto.export.AssetEntry;
import ch.batbern.events.dto.export.AssetManifestResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public LegacyExportEnvelope exportAll() {
        List<Event> events = eventRepository.findAll();
        List<Speaker> speakers = speakerRepository.findAll();
        List<CompanyBasicDto> companies = userApiClient.getAllCompanies();

        List<LegacyEventDto> legacyEvents = events.stream()
                .map(this::mapEvent)
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

    public AssetManifestResponse exportAssetManifest() {
        List<AssetEntry> assets = new ArrayList<>();

        speakerRepository.findAll().stream()
                .filter(s -> s.getProfilePictureUrl() != null)
                .forEach(s -> {
                    String s3Key = extractS3KeyFromUrl(s.getProfilePictureUrl());
                    assets.add(buildAssetEntry("portrait", s.getId(), s3Key));
                });

        logoRepository.findAll().stream()
                .filter(l -> l.getS3Key() != null)
                .forEach(l -> assets.add(buildAssetEntry("logo", l.getId(), l.getS3Key())));

        sessionMaterialsRepository.findAll().stream()
                .filter(m -> m.getS3Key() != null)
                .forEach(m -> assets.add(buildAssetEntry("material", m.getId(), m.getS3Key())));

        return AssetManifestResponse.builder()
                .exportedAt(Instant.now())
                .assetCount(assets.size())
                .assets(assets)
                .build();
    }

    private LegacyEventDto mapEvent(Event event) {
        List<Session> sessions = sessionRepository.findByEventCode(event.getEventCode());
        List<LegacySessionDto> legacySessions = sessions.stream()
                .map(this::mapSession)
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

    private LegacySessionDto mapSession(Session session) {
        List<LegacySpeakerDto> referenten = session.getSessionUsers() == null ? List.of()
                : session.getSessionUsers().stream()
                        .map(this::mapSessionUserToSpeaker)
                        .collect(Collectors.toList());

        return LegacySessionDto.builder()
                .sessionSlug(session.getSessionSlug())
                .title(session.getTitle())
                .description(session.getDescription())
                .sessionType(session.getSessionType())
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
        return LegacySpeakerDto.builder()
                .speakerId(speaker.getUsername())
                .name((firstName + lastName).trim())
                .bio(speaker.getBio())
                .portrait(speaker.getProfilePictureUrl())
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
