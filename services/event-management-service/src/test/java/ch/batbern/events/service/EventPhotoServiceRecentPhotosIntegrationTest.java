package ch.batbern.events.service;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventPhoto;
import ch.batbern.events.media.dto.generated.EventPhotoResponse;
import ch.batbern.events.repository.EventPhotoRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Homepage photo row: recent photos come from events that have already taken place.
 * Bug 2026-10-24: BATbern61–66 were created ahead of time; as the five events with the latest
 * date they filled the "last N events" window, none had photos, and the homepage row vanished.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@Transactional
class EventPhotoServiceRecentPhotosIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventPhotoService photoService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPhotoRepository photoRepository;

    @Test
    void should_returnPhotosOfPastEvents_when_moreFutureEventsThanWindowExist() {
        Instant now = Instant.now();
        saveEvent("BATphoto901", 9901, now.minus(1, ChronoUnit.DAYS));
        savePhoto("BATphoto901");
        savePhoto("BATphoto901");
        saveEvent("BATphoto902", 9902, now.minus(2, ChronoUnit.DAYS));
        savePhoto("BATphoto902");
        for (int i = 0; i < 6; i++) {
            saveEvent("BATphoto91" + i, 9910 + i, now.plus(100 + i * 100L, ChronoUnit.DAYS));
        }

        List<EventPhotoResponse> photos = photoService.getRecentPhotos(20, 5);

        assertThat(photos)
                .extracting(EventPhotoResponse::getEventCode)
                .contains("BATphoto901", "BATphoto902")
                .doesNotContain("BATphoto910", "BATphoto911", "BATphoto912",
                        "BATphoto913", "BATphoto914", "BATphoto915");
    }

    private void saveEvent(String eventCode, int eventNumber, Instant date) {
        eventRepository.save(Event.builder()
                .eventCode(eventCode)
                .eventNumber(eventNumber)
                .title("Photo window test " + eventCode)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(EventType.EVENING)
                .workflowState(date.isBefore(Instant.now())
                        ? EventWorkflowState.ARCHIVED
                        : EventWorkflowState.CREATED)
                .organizerUsername("test.organizer")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void savePhoto(String eventCode) {
        UUID id = UUID.randomUUID();
        photoRepository.save(EventPhoto.builder()
                .id(id)
                .newEntity(true)
                .eventCode(eventCode)
                .s3Key("events/" + eventCode + "/photos/" + id + ".jpg")
                .displayUrl("https://cdn.batbern.ch/events/" + eventCode + "/photos/" + id + ".jpg")
                .filename(id + ".jpg")
                .uploadedAt(Instant.now())
                .uploadedBy("test.organizer")
                .build());
    }
}
