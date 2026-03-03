package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.export.AssetImportResult;
import ch.batbern.events.dto.export.LegacyAttendeeDto;
import ch.batbern.events.dto.export.LegacyEventDto;
import ch.batbern.events.dto.export.LegacyExportEnvelope;
import ch.batbern.events.dto.export.LegacyImportResult;
import ch.batbern.events.dto.export.LegacySessionDto;
import ch.batbern.events.dto.export.LegacySpeakerDto;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
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
    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

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

    public AssetImportResult importAssets(MultipartFile zipFile) throws IOException {
        String prefix = "imports/" + Instant.now().toEpochMilli() + "/";
        List<String> errors = new ArrayList<>();
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipFile.getBytes()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String s3Key = prefix + entry.getName();
                byte[] bytes = zis.readAllBytes();
                try {
                    s3Client.putObject(
                            PutObjectRequest.builder().bucket(bucketName).key(s3Key).build(),
                            RequestBody.fromBytes(bytes));
                    count++;
                } catch (Exception ex) {
                    log.warn("Failed to upload asset {}: {}", entry.getName(), ex.getMessage());
                    errors.add(entry.getName() + ": " + ex.getMessage());
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
