package ch.batbern.events.service;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.upload.UploadException;
import ch.batbern.shared.upload.UploadPurposeHandler;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadRules;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Purpose {@code event-theme-image} (Story 17.11): the theme image of an event, organizers only. It
 * was a {@code company-logo}-style upload through company-user-management before; EMS now owns its
 * files ({@code event_management_uploads}). Without a target the event create or edit takes the
 * upload ({@code themeImageUploadId}); with a target {@code eventCode} it is set at once.
 */
@Component
@RequiredArgsConstructor
public class EventThemeImageUploadHandler implements UploadPurposeHandler {

    public static final String PURPOSE = "event-theme-image";
    public static final String TARGET_TYPE = "EVENT";

    private final EventRepository eventRepository;
    private final CacheManager cacheManager;

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public UploadRules rules() {
        return UploadRules.images5Mb();
    }

    @Override
    public String targetKey() {
        return "eventCode";
    }

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    @Override
    public void checkMayUpload() {
        if (!UploadPurposeHandler.callerHasRole("ORGANIZER")) {
            throw UploadException.forbidden("Only organizers upload event theme images");
        }
    }

    @Override
    public void checkTarget(String eventCode) {
        if (eventRepository.findByEventCode(eventCode).isEmpty()) {
            throw UploadException.notFound("Event not found: " + eventCode);
        }
    }

    @Override
    public String tempKey(String uploadId, String extension) {
        return String.format("logos/temp/%s/theme-%s.%s", uploadId, uploadId, extension);
    }

    /**
     * {@code logos/events/{eventCode}/theme-{uploadId}.{ext}}: no year (Story 17.11a, the upload year
     * means nothing and caused a rollover bug). Theme images stored earlier keep their
     * {@code logos/{year}/…} key and URL.
     */
    @Override
    public String finalKey(UploadRecord upload, String eventCode) {
        return String.format("logos/events/%s/theme-%s.%s", eventCode, upload.getUploadId(),
                upload.getFileExtension());
    }

    @Override
    public void attach(UploadRecord upload, String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));
        event.setThemeImageUploadId(upload.getUploadId());
        event.setThemeImageUrl(upload.getFileUrl());
        eventRepository.save(event);
        evict(CacheConfig.EVENT_WITH_INCLUDES_CACHE);
        evict(CacheConfig.ARCHIVE_EVENTS_CACHE);
    }

    private void evict(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }
}
