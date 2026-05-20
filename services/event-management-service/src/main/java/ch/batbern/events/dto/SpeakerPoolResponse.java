package ch.batbern.events.dto;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.service.ContentStatusDeriver;
import ch.batbern.shared.types.SpeakerWorkflowState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for speaker pool entries (Story 5.2 AC9-13, Story 6.2a).
 */
public class SpeakerPoolResponse {

    private UUID id;
    private UUID eventId;
    private String speakerName;
    private String company;
    private String expertise;
    private String assignedOrganizerId;
    private String status;
    private UUID sessionId;
    // 2026-05-20 — sessionSlug surfaced alongside sessionId so the organizer drawer's
    // content tab can call PATCH /events/{code}/sessions/{slug} directly for READY-state
    // draft writes (the slug is the addressable identifier on that endpoint).
    private String sessionSlug;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    // Story 6.1b: Speaker Invitation System fields
    private String username;
    private String email;
    private Instant invitedAt;
    private LocalDate responseDeadline;
    private LocalDate contentDeadline;

    // Story 6.2a: Speaker Response Portal fields
    private Instant acceptedAt;
    private Instant declinedAt;
    private String declineReason;
    private String preferredTimeSlot;
    private String travelRequirements;
    private String technicalRequirements;
    private String initialPresentationTitle;
    private String preferenceComments;

    // Story 11.B.3: Derived flags per ADR-009 §0.1 (NOT stored on speaker_pool —
    // computed at read time). Populated by the fromEntity(SpeakerPool, Session) factory;
    // the SpeakerPool-only factory uses the weaker sessionId-based fallback.
    private Boolean isSlotAssigned;
    private Boolean isPublishable;

    // Story 6.5: Automated Deadline Reminders
    private Boolean remindersDisabled;

    // Story 6.3: Speaker Content Submission Portal fields
    private String contentStatus;
    private Instant contentSubmittedAt;
    private String submittedTitle;
    private String submittedAbstract;
    private String materialFileName;
    private String materialCloudFrontUrl;

    // Constructors

    public SpeakerPoolResponse() {
    }

    /**
     * Create response DTO from SpeakerPool entity. Delegates to
     * {@link #fromEntity(SpeakerPool, Session)} with {@code session = null}.
     *
     * <p>In the {@code session == null} fallback the derived {@code isSlotAssigned} flag
     * is computed from {@code speakerPool.sessionId != null} only — a weaker predicate
     * than the strict {@code session.start_time IS NOT NULL} check. This can over-report
     * {@code isSlotAssigned} when a session is assigned but its {@code start_time} has
     * not yet been set. Use the {@code (SpeakerPool, Session)} overload when the session
     * is loadable (e.g., the caller has already fetched it for a batch lookup).
     *
     * @param speakerPool the speaker pool entity
     * @return the response DTO
     */
    public static SpeakerPoolResponse fromEntity(SpeakerPool speakerPool) {
        return fromEntity(speakerPool, null);
    }

    /**
     * Create response DTO from SpeakerPool entity with the speaker's assigned session for
     * accurate derived-flag computation (ADR-009 §0.1).
     *
     * <p>When {@code session != null}, the derived {@code isSlotAssigned} flag is set to
     * {@code session.startTime != null}. When {@code session == null} the flag falls back
     * to {@code speakerPool.sessionId != null} — see the caveat on {@link #fromEntity(SpeakerPool)}.
     *
     * <p>{@code isPublishable = status == QUALITY_REVIEWED AND isSlotAssigned}.
     *
     * @param speakerPool the speaker pool entity
     * @param session the speaker's assigned session (nullable); when non-null the strict
     *                {@code session.startTime IS NOT NULL} predicate is used.
     * @return the response DTO
     */
    public static SpeakerPoolResponse fromEntity(SpeakerPool speakerPool, Session session) {
        SpeakerPoolResponse response = new SpeakerPoolResponse();
        response.id = speakerPool.getId();
        response.eventId = speakerPool.getEventId();
        response.speakerName = speakerPool.getSpeakerName();
        response.company = speakerPool.getCompany();
        response.expertise = speakerPool.getExpertise();
        response.assignedOrganizerId = speakerPool.getAssignedOrganizerId();
        response.status = speakerPool.getStatus() != null ? speakerPool.getStatus().name() : null;
        response.sessionId = speakerPool.getSessionId();
        response.notes = speakerPool.getNotes();
        response.createdAt = speakerPool.getCreatedAt();
        response.updatedAt = speakerPool.getUpdatedAt();

        // Story 6.1b: Speaker Invitation System fields
        response.username = speakerPool.getUsername();
        response.email = speakerPool.getEmail();
        response.invitedAt = speakerPool.getInvitedAt();
        response.responseDeadline = speakerPool.getResponseDeadline();
        response.contentDeadline = speakerPool.getContentDeadline();

        // Story 6.2a: Speaker Response Portal fields
        response.acceptedAt = speakerPool.getAcceptedAt();
        response.declinedAt = speakerPool.getDeclinedAt();
        response.declineReason = speakerPool.getDeclineReason();
        response.preferredTimeSlot = speakerPool.getPreferredTimeSlot();
        response.travelRequirements = speakerPool.getTravelRequirements();
        response.technicalRequirements = speakerPool.getTechnicalRequirements();
        // Story 11.E.8 consolidation: initialPresentationTitle column was dropped (V102).
        // sessions.title is canonical now; FE's display fallback chain handles a null value.
        response.preferenceComments = speakerPool.getPreferenceComments();

        // Story 6.5: Automated Deadline Reminders
        response.remindersDisabled = speakerPool.getRemindersDisabled();

        // Story 11.E.8 consolidation: contentStatus and contentSubmittedAt are derived from
        // session_content_history at read time. fromEntity() has no version context, so the
        // defaults below (PENDING, null) are correct for the no-content case. Callers with
        // access to a session and its latest history row should use fromEntityWithContent(...).
        response.contentStatus = ContentStatusDeriver.derive(speakerPool.getStatus(), java.util.Optional.empty());
        response.contentSubmittedAt = null;

        // Story 11.B.3: derived flags per ADR-009 §0.1 (computed at read time, not stored).
        boolean slotAssigned;
        if (session != null) {
            slotAssigned = session.getStartTime() != null;
            response.sessionSlug = session.getSessionSlug();
        } else {
            slotAssigned = speakerPool.getSessionId() != null;
        }
        response.isSlotAssigned = slotAssigned;
        response.isPublishable = speakerPool.getStatus() == SpeakerWorkflowState.QUALITY_REVIEWED
                && slotAssigned;

        return response;
    }

    /**
     * Create response DTO from SpeakerPool entity with the speaker's session AND the
     * latest {@link ch.batbern.events.domain.SessionContentVersion}. Use when the caller
     * has both available — derived flags + content fields + the derived {@code contentStatus}
     * are populated in one shot.
     *
     * <p>Story 11.E.8 consolidation: this is the canonical "rich" factory. The latest
     * version is the source for both {@code submittedTitle/submittedAbstract} (mirroring
     * what's on {@code sessions.title}/{@code .description}) and the derived
     * {@code contentStatus} + {@code contentSubmittedAt}.
     *
     * @param speakerPool the speaker pool entity
     * @param session the speaker's assigned session (nullable)
     * @param latestVersion the latest content version for this session (nullable)
     * @return the response DTO
     */
    public static SpeakerPoolResponse fromEntityWithContent(
            SpeakerPool speakerPool,
            Session session,
            ch.batbern.events.domain.SessionContentVersion latestVersion) {
        SpeakerPoolResponse response = fromEntity(speakerPool, session);
        // Story 11.E.8 §2.9: sessions.title / sessions.description are the canonical
        // "current" for every read surface (kanban card, public archive, speaker portal,
        // speaker dashboard). The submittedTitle / submittedAbstract response fields
        // therefore mirror the session row — not the latest history row — so that an
        // organizer's session-edit modal propagates everywhere immediately. The latest
        // history row still drives the derived contentStatus + contentSubmittedAt for
        // the audit/timeline view.
        if (session != null) {
            response.submittedTitle = session.getTitle();
            response.submittedAbstract = session.getDescription();
        }
        if (latestVersion != null) {
            response.contentSubmittedAt = latestVersion.getSubmittedAt();
            response.contentStatus = ContentStatusDeriver.derive(
                    speakerPool.getStatus(), java.util.Optional.of(latestVersion));
        }
        return response;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public String getAssignedOrganizerId() {
        return assignedOrganizerId;
    }

    public void setAssignedOrganizerId(String assignedOrganizerId) {
        this.assignedOrganizerId = assignedOrganizerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionSlug() {
        return sessionSlug;
    }

    public void setSessionSlug(String sessionSlug) {
        this.sessionSlug = sessionSlug;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Story 6.1b: Speaker Invitation System getters and setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public void setInvitedAt(Instant invitedAt) {
        this.invitedAt = invitedAt;
    }

    public LocalDate getResponseDeadline() {
        return responseDeadline;
    }

    public void setResponseDeadline(LocalDate responseDeadline) {
        this.responseDeadline = responseDeadline;
    }

    public LocalDate getContentDeadline() {
        return contentDeadline;
    }

    public void setContentDeadline(LocalDate contentDeadline) {
        this.contentDeadline = contentDeadline;
    }

    // Story 6.2a: Speaker Response Portal getters and setters

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getDeclinedAt() {
        return declinedAt;
    }

    public void setDeclinedAt(Instant declinedAt) {
        this.declinedAt = declinedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public String getPreferredTimeSlot() {
        return preferredTimeSlot;
    }

    public void setPreferredTimeSlot(String preferredTimeSlot) {
        this.preferredTimeSlot = preferredTimeSlot;
    }

    public String getTravelRequirements() {
        return travelRequirements;
    }

    public void setTravelRequirements(String travelRequirements) {
        this.travelRequirements = travelRequirements;
    }

    public String getTechnicalRequirements() {
        return technicalRequirements;
    }

    public void setTechnicalRequirements(String technicalRequirements) {
        this.technicalRequirements = technicalRequirements;
    }

    public String getInitialPresentationTitle() {
        return initialPresentationTitle;
    }

    public void setInitialPresentationTitle(String initialPresentationTitle) {
        this.initialPresentationTitle = initialPresentationTitle;
    }

    public String getPreferenceComments() {
        return preferenceComments;
    }

    public void setPreferenceComments(String preferenceComments) {
        this.preferenceComments = preferenceComments;
    }

    // Story 6.5: Automated Deadline Reminders getter and setter

    public Boolean getRemindersDisabled() {
        return remindersDisabled;
    }

    public void setRemindersDisabled(Boolean remindersDisabled) {
        this.remindersDisabled = remindersDisabled;
    }

    // Story 6.3: Speaker Content Submission Portal getters and setters

    public String getContentStatus() {
        return contentStatus;
    }

    public void setContentStatus(String contentStatus) {
        this.contentStatus = contentStatus;
    }

    public Instant getContentSubmittedAt() {
        return contentSubmittedAt;
    }

    public void setContentSubmittedAt(Instant contentSubmittedAt) {
        this.contentSubmittedAt = contentSubmittedAt;
    }

    public String getSubmittedTitle() {
        return submittedTitle;
    }

    public void setSubmittedTitle(String submittedTitle) {
        this.submittedTitle = submittedTitle;
    }

    public String getSubmittedAbstract() {
        return submittedAbstract;
    }

    public void setSubmittedAbstract(String submittedAbstract) {
        this.submittedAbstract = submittedAbstract;
    }

    public String getMaterialFileName() {
        return materialFileName;
    }

    public void setMaterialFileName(String materialFileName) {
        this.materialFileName = materialFileName;
    }

    public String getMaterialCloudFrontUrl() {
        return materialCloudFrontUrl;
    }

    public void setMaterialCloudFrontUrl(String materialCloudFrontUrl) {
        this.materialCloudFrontUrl = materialCloudFrontUrl;
    }

    // Story 11.B.3: Derived flags per ADR-009 §0.1.

    public Boolean getIsSlotAssigned() {
        return isSlotAssigned;
    }

    public void setIsSlotAssigned(Boolean isSlotAssigned) {
        this.isSlotAssigned = isSlotAssigned;
    }

    public Boolean getIsPublishable() {
        return isPublishable;
    }

    public void setIsPublishable(Boolean isPublishable) {
        this.isPublishable = isPublishable;
    }
}
