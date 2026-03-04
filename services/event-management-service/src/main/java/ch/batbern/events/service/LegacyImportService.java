package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.export.AssetImportResult;
import ch.batbern.events.dto.export.BundleImportResult;
import ch.batbern.events.dto.export.LegacyAttendeeDto;
import ch.batbern.events.dto.export.LegacyEventDto;
import ch.batbern.events.dto.export.LegacyExportEnvelope;
import ch.batbern.events.dto.export.LegacyImportResult;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for importing BATbern data in the legacy BAT JSON format.
 * Story 10.20: AC3 — upsert import with idempotency guarantee.
 *
 * Note: companies[] in the import envelope are informational only.
 * Company records are owned by company-user-management-service and cannot
 * be upserted here. They are recorded in the skipped list.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyImportService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final RegistrationRepository registrationRepository;
    private final SpeakerRepository speakerRepository;
    private final LogoRepository logoRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.cloudfront.domain}")
    private String cloudFrontDomain;

    @Transactional
    public LegacyImportResult importAll(LegacyExportEnvelope envelope) {
        List<String> errors = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        if (envelope.getEvents() == null) {
            errors.add("Import envelope is invalid: events list is null");
            return LegacyImportResult.builder()
                    .imported(LegacyImportResult.ImportCounts.builder().build())
                    .skipped(List.of())
                    .errors(errors)
                    .build();
        }

        int eventCount = upsertEvents(envelope.getEvents(), errors);
        int sessionCount = upsertSessionsForEvents(envelope.getEvents(), errors);
        int speakerCount = upsertSpeakers(envelope.getSpeakers(), errors);
        int attendeeCount = upsertAttendees(envelope.getAttendees(), errors);

        // Companies are owned by company-user-management-service; cannot upsert here
        int companyCount = 0;
        if (envelope.getCompanies() != null && !envelope.getCompanies().isEmpty()) {
            skipped.add("companies: " + envelope.getCompanies().size()
                    + " record(s) skipped — managed by company-user-management-service");
        }

        return LegacyImportResult.builder()
                .imported(LegacyImportResult.ImportCounts.builder()
                        .events(eventCount)
                        .sessions(sessionCount)
                        .speakers(speakerCount)
                        .companies(companyCount)
                        .attendees(attendeeCount)
                        .build())
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    private static final long MAX_ENTRY_SIZE = 50 * 1024 * 1024; // 50 MB per ZIP entry

    public AssetImportResult importAssets(MultipartFile zipFile) throws IOException {
        String prefix = "imports/" + Instant.now().toEpochMilli() + "/";
        List<String> errors = new ArrayList<>();
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                // Sanitize entry name to prevent ZIP Slip path traversal
                String entryName = entry.getName()
                        .replace("\\", "/")
                        .replaceAll("\\.{2,}/", "")
                        .replaceAll("^/+", "");
                if (entryName.isBlank() || entryName.contains("..")) {
                    log.warn("Skipping suspicious ZIP entry: {}", entry.getName());
                    zis.closeEntry();
                    continue;
                }

                String s3Key = prefix + entryName;

                // Guard against ZIP bomb: cap per-entry read at MAX_ENTRY_SIZE
                byte[] bytes = readBounded(zis, MAX_ENTRY_SIZE, entryName);
                if (bytes == null) {
                    errors.add(entryName + ": exceeds " + (MAX_ENTRY_SIZE / 1024 / 1024) + " MB limit");
                    zis.closeEntry();
                    continue;
                }

                try {
                    s3Client.putObject(
                            PutObjectRequest.builder().bucket(bucketName).key(s3Key).build(),
                            RequestBody.fromBytes(bytes));
                    count++;
                } catch (Exception ex) {
                    log.warn("Failed to upload asset {}: {}", entryName, ex.getMessage());
                    errors.add(entryName + ": " + ex.getMessage());
                }
                zis.closeEntry();
            }
        }

        return AssetImportResult.builder()
                .importedAt(Instant.now())
                .importedCount(count)
                .s3Prefix(prefix)
                .errors(errors)
                .build();
    }

    /**
     * Imports a bundle ZIP produced by {@code GET /admin/export/bundle}.
     * <p>
     * Pass 1: extracts {@code export.json} and calls {@link #importAll} to restore data.
     * Pass 2: uploads binary assets to S3 and links them back to their entities.
     * Missing assets are logged and recorded in {@code assetErrors} — the bundle is still
     * considered partially successful.
     */
    public BundleImportResult importBundle(MultipartFile zipFile) throws IOException {
        // Pass 1 — find export.json, parse, and import data
        LegacyExportEnvelope envelope = extractEnvelope(zipFile);
        if (envelope == null) {
            LegacyImportResult empty = LegacyImportResult.builder()
                    .imported(LegacyImportResult.ImportCounts.builder().build())
                    .skipped(List.of())
                    .errors(List.of("Bundle ZIP does not contain export.json"))
                    .build();
            return BundleImportResult.builder()
                    .dataResult(empty)
                    .assetsImported(0)
                    .assetErrors(List.of())
                    .build();
        }
        LegacyImportResult dataResult = importAll(envelope);

        // Pass 2 — upload assets and re-link entities
        List<String> assetErrors = new ArrayList<>();
        int assetsImported = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = sanitizeEntryName(entry.getName());
                if (name.isBlank() || "export.json".equals(name) || "manifest.json".equals(name)) {
                    zis.closeEntry();
                    continue;
                }

                // name format: {assetType}/{entityId}/{filename}
                String[] parts = name.split("/", 3);
                if (parts.length < 3) {
                    zis.closeEntry();
                    continue;
                }
                String assetType = parts[0];
                UUID entityId;
                try {
                    entityId = UUID.fromString(parts[1]);
                } catch (IllegalArgumentException e) {
                    assetErrors.add(name + ": invalid entity ID");
                    zis.closeEntry();
                    continue;
                }

                byte[] bytes = readBounded(zis, MAX_ENTRY_SIZE, name);
                if (bytes == null) {
                    assetErrors.add(name + ": exceeds " + (MAX_ENTRY_SIZE / 1024 / 1024) + " MB limit");
                    zis.closeEntry();
                    continue;
                }

                try {
                    s3Client.putObject(
                            PutObjectRequest.builder().bucket(bucketName).key(name).build(),
                            RequestBody.fromBytes(bytes));
                    String cdnUrl = cloudFrontDomain + "/" + name;
                    linkAssetToEntity(assetType, entityId, name, cdnUrl, assetErrors);
                    assetsImported++;
                } catch (Exception ex) {
                    log.warn("Failed to upload bundle asset {}: {}", name, ex.getMessage());
                    assetErrors.add(name + ": " + ex.getMessage());
                }
                zis.closeEntry();
            }
        }

        return BundleImportResult.builder()
                .dataResult(dataResult)
                .assetsImported(assetsImported)
                .assetErrors(assetErrors)
                .build();
    }

    /** Reads the ZIP once to find and parse {@code export.json}. Returns {@code null} if not found. */
    private LegacyExportEnvelope extractEnvelope(MultipartFile zipFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("export.json".equals(entry.getName())) {
                    byte[] bytes = readBounded(zis, MAX_ENTRY_SIZE * 10L, "export.json");
                    if (bytes != null) {
                        try {
                            return objectMapper.readValue(bytes, LegacyExportEnvelope.class);
                        } catch (Exception e) {
                            log.warn("Failed to parse export.json in bundle: {}", e.getMessage());
                            return null;
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    /** Links an uploaded S3 asset back to its entity by updating the relevant URL/key field. */
    private void linkAssetToEntity(String assetType, UUID entityId, String s3Key, String cdnUrl,
                                   List<String> errors) {
        try {
            switch (assetType) {
                case "portraits" -> speakerRepository.findById(entityId).ifPresent(s -> {
                    s.setProfilePictureUrl(cdnUrl);
                    speakerRepository.save(s);
                });
                case "logos" -> logoRepository.findById(entityId).ifPresent(l -> {
                    l.setS3Key(s3Key);
                    l.setCloudFrontUrl(cdnUrl);
                    logoRepository.save(l);
                });
                case "materials" -> sessionMaterialsRepository.findById(entityId).ifPresent(m -> {
                    m.setS3Key(s3Key);
                    m.setCloudFrontUrl(cdnUrl);
                    sessionMaterialsRepository.save(m);
                });
                case "themes" -> eventRepository.findById(entityId).ifPresent(e -> {
                    e.setThemeImageUrl(cdnUrl);
                    eventRepository.save(e);
                });
                default -> {
                    log.warn("Unknown asset type '{}' for entity {}", assetType, entityId);
                    errors.add(s3Key + ": unknown asset type: " + assetType);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to link {} asset {} to entity {}: {}", assetType, s3Key, entityId, ex.getMessage());
            errors.add(s3Key + ": link failed: " + ex.getMessage());
        }
    }

    /** Sanitizes a ZIP entry name to prevent ZIP Slip path traversal. */
    private String sanitizeEntryName(String raw) {
        return raw.replace("\\", "/")
                .replaceAll("\\.{2,}/", "")
                .replaceAll("^/+", "");
    }

    private int upsertEvents(List<LegacyEventDto> dtos, List<String> errors) {
        int count = 0;
        for (LegacyEventDto dto : dtos) {
            try {
                Optional<Event> existing = eventRepository.findByEventCode(dto.getEventCode());
                if (existing.isPresent()) {
                    Event e = existing.get();
                    e.setTitle(dto.getTitle());
                    if (dto.getDate() != null) {
                        e.setDate(dto.getDate());
                    }
                    if (dto.getVenueName() != null) {
                        e.setVenueName(dto.getVenueName());
                    }
                    if (dto.getVenueAddress() != null) {
                        e.setVenueAddress(dto.getVenueAddress());
                    }
                    eventRepository.save(e);
                } else {
                    eventRepository.save(buildNewEvent(dto));
                }
                count++;
            } catch (Exception ex) {
                log.warn("Failed to upsert event {}: {}", dto.getEventCode(), ex.getMessage());
                errors.add("Event " + dto.getEventCode() + ": " + ex.getMessage());
            }
        }
        return count;
    }

    private int upsertAttendees(List<LegacyAttendeeDto> dtos, List<String> errors) {
        if (dtos == null || dtos.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (LegacyAttendeeDto dto : dtos) {
            try {
                Optional<Event> event = eventRepository.findByEventCode(dto.getEventCode());
                if (event.isEmpty()) {
                    log.warn("Event not found for attendee {}: eventCode={}", dto.getUsername(), dto.getEventCode());
                    continue;
                }
                UUID eventId = event.get().getId();
                Optional<Registration> existing =
                        registrationRepository.findByEventIdAndAttendeeUsername(eventId, dto.getUsername());
                if (existing.isEmpty()) {
                    registrationRepository.save(buildNewRegistration(dto, eventId));
                    count++;
                }
            } catch (Exception ex) {
                log.warn("Failed to upsert attendee {}: {}", dto.getUsername(), ex.getMessage());
                errors.add("Attendee " + dto.getUsername() + ": " + ex.getMessage());
            }
        }
        return count;
    }

    private int upsertSessionsForEvents(List<LegacyEventDto> eventDtos, List<String> errors) {
        int count = 0;
        for (LegacyEventDto eventDto : eventDtos) {
            if (eventDto.getSessions() == null || eventDto.getSessions().isEmpty()) {
                continue;
            }
            Optional<Event> event = eventRepository.findByEventCode(eventDto.getEventCode());
            if (event.isEmpty()) {
                log.warn("Cannot import sessions for event {}: event not found", eventDto.getEventCode());
                continue;
            }
            UUID eventId = event.get().getId();
            String eventCode = event.get().getEventCode();
            for (LegacySessionDto dto : eventDto.getSessions()) {
                try {
                    Optional<Session> existing = sessionRepository.findBySessionSlug(dto.getSessionSlug());
                    if (existing.isPresent()) {
                        Session s = existing.get();
                        if (dto.getTitle() != null) {
                            s.setTitle(dto.getTitle());
                        }
                        if (dto.getDescription() != null) {
                            s.setDescription(dto.getDescription());
                        }
                        if (dto.getSessionType() != null) {
                            s.setSessionType(dto.getSessionType());
                        }
                        sessionRepository.save(s);
                    } else {
                        sessionRepository.save(buildNewSession(dto, eventId, eventCode));
                    }
                    count++;
                } catch (Exception ex) {
                    log.warn("Failed to upsert session {}: {}", dto.getSessionSlug(), ex.getMessage());
                    errors.add("Session " + dto.getSessionSlug() + ": " + ex.getMessage());
                }
            }
        }
        return count;
    }

    private int upsertSpeakers(List<LegacySpeakerDto> dtos, List<String> errors) {
        if (dtos == null || dtos.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (LegacySpeakerDto dto : dtos) {
            if (dto.getSpeakerId() == null || dto.getSpeakerId().isBlank()) {
                log.warn("Skipping speaker with null speakerId");
                continue;
            }
            try {
                Optional<Speaker> existing = speakerRepository.findByUsername(dto.getSpeakerId());
                if (existing.isPresent()) {
                    Speaker s = existing.get();
                    if (dto.getBio() != null) {
                        s.setBio(dto.getBio());
                    }
                    if (dto.getPortrait() != null) {
                        s.setProfilePictureUrl(dto.getPortrait());
                    }
                    if (dto.getLinkedInUrl() != null) {
                        s.setLinkedInUrl(dto.getLinkedInUrl());
                    }
                    if (dto.getTwitterHandle() != null) {
                        s.setTwitterHandle(dto.getTwitterHandle());
                    }
                    speakerRepository.save(s);
                } else {
                    speakerRepository.save(buildNewSpeaker(dto));
                }
                count++;
            } catch (Exception ex) {
                log.warn("Failed to upsert speaker {}: {}", dto.getSpeakerId(), ex.getMessage());
                errors.add("Speaker " + dto.getSpeakerId() + ": " + ex.getMessage());
            }
        }
        return count;
    }

    private Session buildNewSession(LegacySessionDto dto, UUID eventId, String eventCode) {
        return Session.builder()
                .sessionSlug(dto.getSessionSlug())
                .eventId(eventId)
                .eventCode(eventCode)
                .title(dto.getTitle() != null ? dto.getTitle() : dto.getSessionSlug())
                .description(dto.getDescription())
                .sessionType(dto.getSessionType())
                .build();
    }

    private Speaker buildNewSpeaker(LegacySpeakerDto dto) {
        String firstName = null;
        String lastName = null;
        if (dto.getName() != null && !dto.getName().isBlank()) {
            int spaceIdx = dto.getName().indexOf(' ');
            if (spaceIdx > 0) {
                firstName = dto.getName().substring(0, spaceIdx);
                lastName = dto.getName().substring(spaceIdx + 1);
            } else {
                firstName = dto.getName();
            }
        }
        return Speaker.builder()
                .username(dto.getSpeakerId())
                .firstName(firstName)
                .lastName(lastName)
                .bio(dto.getBio())
                .profilePictureUrl(dto.getPortrait())
                .linkedInUrl(dto.getLinkedInUrl())
                .twitterHandle(dto.getTwitterHandle())
                .build();
    }

    private Event buildNewEvent(LegacyEventDto dto) {
        return Event.builder()
                .eventCode(dto.getEventCode())
                .title(dto.getTitle() != null ? dto.getTitle() : dto.getEventCode())
                .eventNumber(dto.getBat() != null ? dto.getBat() : 0)
                .date(dto.getDate() != null ? dto.getDate() : Instant.now())
                .registrationDeadline(dto.getDate() != null ? dto.getDate() : Instant.now())
                .venueName(dto.getVenueName() != null ? dto.getVenueName() : "Unknown")
                .venueAddress(dto.getVenueAddress() != null ? dto.getVenueAddress() : "Unknown")
                .venueCapacity(0)
                .build();
    }

    /**
     * Read up to maxBytes from the stream; return null if the entry exceeds the limit.
     */
    private byte[] readBounded(ZipInputStream zis, long maxBytes, String entryName) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = zis.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                log.warn("ZIP entry {} exceeds {} byte limit, skipping", entryName, maxBytes);
                return null;
            }
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private Registration buildNewRegistration(LegacyAttendeeDto dto, UUID eventId) {
        String status = dto.getStatus() != null ? dto.getStatus() : "registered";
        Instant regDate = dto.getRegisteredAt() != null ? dto.getRegisteredAt() : Instant.now();
        return Registration.builder()
                .registrationCode(UUID.randomUUID().toString())
                .eventId(eventId)
                .attendeeUsername(dto.getUsername())
                .status(status)
                .registrationDate(regDate)
                .build();
    }
}
