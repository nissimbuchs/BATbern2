package ch.batbern.events.service;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionMaterialType;
import ch.batbern.events.event.SessionMaterialsUploadedEvent;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.upload.UploadException;
import ch.batbern.shared.upload.UploadPurposeHandler;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadRules;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Purpose {@code session-material} (Story 17.11d): a presentation, document, video or archive of a
 * session. The target has two keys, {@code eventCode} and {@code sessionSlug}. Organizers may attach
 * to any session, a speaker only to a session they are on ({@code session_users}). The rules depend on
 * the caller: organizers 100 MB and many types, speakers 50 MB and slides, as the two upload paths
 * this purpose replaces ({@code /materials/presigned-url} + {@code …/confirm} and the speaker portal's
 * {@code …/materials/presigned-url} + {@code …/materials/confirm}).
 *
 * <p>The confirm with the target creates the {@code session_materials} row. Without a target (the
 * organizer's session editor, before the session exists) the upload stays CONFIRMED and
 * {@code POST /events/{eventCode}/sessions/{sessionSlug}/materials} takes it
 * ({@link SessionMaterialsService#associateMaterialsWithSession}). Listing, download and delete stay
 * in {@link SessionMaterialsService}.
 */
@Component
@RequiredArgsConstructor
public class SessionMaterialUploadHandler implements UploadPurposeHandler {

    public static final String PURPOSE = "session-material";
    public static final String TARGET_TYPE = "SESSION";

    /** The organizer's types up to 100 MB, as {@code MaterialsUploadService} before Story 17.11d. */
    static final UploadRules ORGANIZER_RULES = new UploadRules(100L * 1024 * 1024,
            Set.of(
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.apple.keynote",
                    "application/vnd.oasis.opendocument.presentation",
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "video/mp4",
                    "video/quicktime",
                    "video/x-msvideo",
                    "video/x-matroska",
                    "video/webm",
                    "application/zip",
                    "application/gzip",
                    "application/x-gzip",
                    "application/x-tar"),
            Set.of("pptx", "ppt", "key", "odp", "pdf", "doc", "docx", "txt", "mp4", "mov", "avi", "mkv", "webm",
                    "zip", "gz", "tar"));

    /** Slides up to 50 MB for speakers, as {@code SpeakerPortalMaterialsService} before Story 17.11d. */
    static final UploadRules SPEAKER_RULES = new UploadRules(50L * 1024 * 1024,
            Set.of(
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.apple.keynote",
                    "application/pdf"),
            Set.of("pptx", "ppt", "key", "pdf"));

    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final SecurityContextHelper securityContextHelper;
    private final DomainEventPublisher domainEventPublisher;
    private final CacheManager cacheManager;

    @Override
    public String purpose() {
        return PURPOSE;
    }

    /** The organizer's rules for an organizer, else the speaker's (the same context as {@code callerHasRole}). */
    @Override
    public UploadRules rules() {
        return isOrganizer() ? ORGANIZER_RULES : SPEAKER_RULES;
    }

    @Override
    public String targetKey() {
        return "eventCode";
    }

    @Override
    public List<String> targetKeys() {
        return List.of("eventCode", "sessionSlug");
    }

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    @Override
    public void checkMayUpload() {
        if (!isOrganizer() && !UploadPurposeHandler.callerHasRole("SPEAKER")) {
            throw UploadException.forbidden("Only organizers and speakers upload session materials");
        }
    }

    /** The session exists in the event (404) and the caller may change its materials (403). */
    @Override
    public void checkTarget(String targetId) {
        checkMayChangeMaterials(session(eventCode(targetId), sessionSlug(targetId)));
    }

    @Override
    public String tempKey(String uploadId, String extension) {
        return String.format("materials/temp/%s/file-%s.%s", uploadId, uploadId, extension);
    }

    @Override
    public String finalKey(UploadRecord upload, String targetId) {
        return SessionMaterialsService.finalS3Key(eventCode(targetId), sessionSlug(targetId), upload.getUploadId(),
                upload.getFileExtension());
    }

    /**
     * Creates the material from the confirm with the target: PRESENTATION for a speaker (as the
     * speaker portal before), else derived from the content type.
     */
    @Override
    public void attach(UploadRecord upload, String targetId) {
        Session session = session(eventCode(targetId), sessionSlug(targetId));
        String by = securityContextHelper.getCurrentUsername();
        SessionMaterial material = createMaterial(session, upload, null, by);
        published(session, List.of(material), by);
    }

    /**
     * The session of a material change: it exists and belongs to the event (404 otherwise, as an
     * unknown session).
     */
    public Session session(String eventCode, String sessionSlug) {
        return sessionRepository.findBySessionSlug(sessionSlug)
                .filter(s -> eventCode == null || eventCode.equals(s.getEventCode()))
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));
    }

    /**
     * Organizers may change the materials of any session, a speaker only of a session they are on
     * ({@code session_users}); 403 otherwise. Shared by the upload, association and delete.
     */
    public void checkMayChangeMaterials(Session session) {
        if (isOrganizer()) {
            return;
        }
        String username = securityContextHelper.getCurrentUsernameOrNull();
        if (username == null || !sessionUserRepository.existsBySessionIdAndUsername(session.getId(), username)) {
            throw UploadException.forbidden("Speakers may only change the materials of their own sessions");
        }
    }

    /**
     * The {@code session_materials} row of an ASSOCIATED upload: name, size and type come from the
     * upload. The material type: for an organizer the one chosen ({@code materialType}), else derived
     * from the content type; for a speaker always PRESENTATION, whatever the client sends.
     */
    SessionMaterial createMaterial(Session session, UploadRecord upload, String materialType, String uploadedBy) {
        String type;
        if (!isOrganizer()) {
            type = SessionMaterialType.PRESENTATION.name();
        } else if (materialType != null) {
            type = materialType;
        } else {
            type = materialTypeOf(upload.getContentType()).name();
        }
        String fileName = upload.getFileName() != null ? upload.getFileName()
                : "material." + upload.getFileExtension();
        return sessionMaterialsRepository.save(SessionMaterial.builder()
                .session(session)
                .uploadId(upload.getUploadId())
                .s3Key(upload.getS3Key())
                .cloudFrontUrl(upload.getFileUrl())
                .fileName(fileName)
                .fileExtension(upload.getFileExtension())
                .fileSize(upload.getFileSize())
                .mimeType(upload.getContentType())
                .materialType(type)
                .uploadedBy(uploadedBy)
                .contentExtracted(false)
                .extractionStatus("PENDING")
                .build());
    }

    /** {@link SessionMaterialsUploadedEvent} and, after the commit, the event cache (sessions carry materials). */
    void published(Session session, List<SessionMaterial> materials, String uploadedBy) {
        domainEventPublisher.publish(new SessionMaterialsUploadedEvent(
                session.getSessionSlug(),
                session.getEventCode(),
                materials.stream().map(SessionMaterial::getUploadId).toList(),
                materials.stream().map(SessionMaterial::getMaterialType).toList(),
                uploadedBy));
        CacheConfig.clearAfterCommit(cacheManager, CacheConfig.EVENT_WITH_INCLUDES_CACHE);
    }

    /** The material type of a content type (formerly {@code MaterialsUploadService.getMaterialTypeFromMimeType}). */
    public static SessionMaterialType materialTypeOf(String mimeType) {
        if (mimeType.startsWith("video/")) {
            return SessionMaterialType.VIDEO;
        } else if (mimeType.contains("powerpoint") || mimeType.contains("presentation")
                || mimeType.contains("keynote")) {
            return SessionMaterialType.PRESENTATION;
        } else if (mimeType.equals("application/pdf") || mimeType.contains("document")
                || mimeType.equals("text/plain")) {
            return SessionMaterialType.DOCUMENT;
        } else if (mimeType.contains("zip") || mimeType.contains("gzip") || mimeType.contains("tar")) {
            return SessionMaterialType.ARCHIVE;
        }
        return SessionMaterialType.OTHER;
    }

    /** The upload target of a session, as {@link ch.batbern.shared.upload.UploadService} joins it. */
    public static String targetId(String eventCode, String sessionSlug) {
        return eventCode + "/" + sessionSlug;
    }

    private static String eventCode(String targetId) {
        return targetId.substring(0, targetId.indexOf('/'));
    }

    private static String sessionSlug(String targetId) {
        return targetId.substring(targetId.indexOf('/') + 1);
    }

    private boolean isOrganizer() {
        return UploadPurposeHandler.callerHasRole("ORGANIZER");
    }
}
