package ch.batbern.events.mapper;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionContentVersion;
import ch.batbern.events.domain.SessionProposal;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.speakers.dto.generated.SpeakerPoolResponse;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Pure mapper from {@link SpeakerPool} entities to the generated
 * {@link SpeakerPoolResponse} wire DTO (API-consolidation Phase 7, 2026-06-28).
 *
 * <p>Replaces the hand-written {@code dto.SpeakerPoolResponse} static factories
 * ({@code fromEntity}, {@code fromEntityWithContent}, {@code applyProposal}). The wire
 * shape is preserved byte-for-byte with the exception of the deliberately-trimmed dead
 * fields ({@code isPublishable}, {@code contentStatus}, {@code materialFileName},
 * {@code materialCloudFrontUrl}, {@code remindersDisabled}) and the corrected 8-state
 * {@code status} enum (ADR-009 §0.1); see the plan's Shared-DTO consolidation table.
 *
 * <p>Type conversions vs the entity: {@code SpeakerWorkflowState} → {@code StatusEnum}
 * (by enum name — UPPER_CASE wire), {@code String source} → {@code SourceEnum},
 * {@code Instant} timestamps → {@code OffsetDateTime} (UTC); {@code LocalDate} deadlines
 * pass through unchanged.
 *
 * <p>Pattern: Pure Mapper (mirrors {@link SessionMapper}/{@link TimetableMapper}) — field
 * mapping + type conversion only, NO repository access. The live-identity overlay
 * (username/email/displayName) is applied separately by
 * {@code PrimarySpeakerResolver.applyOverlay} / {@code SpeakerPoolService} after mapping.
 */
@Component
public class SpeakerPoolMapper {

    /**
     * Map a pool entity with no session context. The derived {@code isSlotAssigned} flag
     * falls back to {@code sessionId != null} (weaker than the strict
     * {@code session.startTime != null} predicate — use {@link #toResponse(SpeakerPool, Session)}
     * when the session is loadable).
     */
    public SpeakerPoolResponse toResponse(SpeakerPool pool) {
        return toResponse(pool, null);
    }

    /**
     * Map a pool entity with its assigned session for accurate derived-flag computation
     * (ADR-009 §0.1): {@code isSlotAssigned = session.startTime != null}.
     */
    public SpeakerPoolResponse toResponse(SpeakerPool pool, Session session) {
        SpeakerPoolResponse response = new SpeakerPoolResponse();
        response.setId(pool.getId());
        response.setEventId(pool.getEventId());
        response.setSpeakerName(pool.getSpeakerName());
        response.setCompany(pool.getCompany());
        response.setExpertise(pool.getExpertise());
        response.setAssignedOrganizerId(pool.getAssignedOrganizerId());
        response.setStatus(toStatusEnum(pool.getStatus()));
        response.setSessionId(pool.getSessionId());
        response.setNotes(pool.getNotes());
        response.setCreatedAt(toOffset(pool.getCreatedAt()));
        response.setUpdatedAt(toOffset(pool.getUpdatedAt()));

        // Story 7.2 / ADR-012: `source` is the only self-nomination field on speaker_pool;
        // the proposed talk is layered on later via applyProposal(...).
        response.setSource(toSourceEnum(pool.getSource()));

        // Story 6.1b: invitation fields. username/email are populated by the identity overlay
        // (PrimarySpeakerResolver / SpeakerPoolService), not here — pre-READY rows have no User.
        response.setInvitedAt(toOffset(pool.getInvitedAt()));
        response.setResponseDeadline(pool.getResponseDeadline());
        response.setContentDeadline(pool.getContentDeadline());

        // Story 6.2a: Speaker Response Portal fields. (initialPresentationTitle column was
        // dropped in V102 — sessions.title is canonical — so it stays null on the wire.)
        response.setAcceptedAt(toOffset(pool.getAcceptedAt()));
        response.setDeclinedAt(toOffset(pool.getDeclinedAt()));
        response.setDeclineReason(pool.getDeclineReason());
        response.setPreferredTimeSlot(pool.getPreferredTimeSlot());
        response.setTravelRequirements(pool.getTravelRequirements());
        response.setTechnicalRequirements(pool.getTechnicalRequirements());
        response.setPreferenceComments(pool.getPreferenceComments());

        // Story 11.B.3: derived isSlotAssigned per ADR-009 §0.1 (computed at read time).
        if (session != null) {
            response.setIsSlotAssigned(session.getStartTime() != null);
            response.setSessionSlug(session.getSessionSlug());
        } else {
            response.setIsSlotAssigned(pool.getSessionId() != null);
        }

        return response;
    }

    /**
     * Map a pool entity with its session AND latest content version. The canonical
     * "current" title/abstract mirror {@code sessions.title}/{@code .description}
     * (Story 11.E.8 §2.9); the latest history row drives {@code contentSubmittedAt}.
     */
    public SpeakerPoolResponse toResponseWithContent(
            SpeakerPool pool, Session session, SessionContentVersion latestVersion) {
        SpeakerPoolResponse response = toResponse(pool, session);
        if (session != null) {
            response.setSubmittedTitle(session.getTitle());
            response.setSubmittedAbstract(session.getDescription());
        }
        if (latestVersion != null) {
            response.setContentSubmittedAt(toOffset(latestVersion.getSubmittedAt()));
        }
        return response;
    }

    /**
     * Story 7.2 / ADR-012: layer a self-nomination pitch ({@code session_proposals}) onto an
     * already-mapped response. No-op for null inputs (organizer-added rows have no proposal).
     */
    public void applyProposal(SpeakerPoolResponse response, SessionProposal proposal) {
        if (response == null || proposal == null) {
            return;
        }
        response.setProposedByUsername(proposal.getProposedByUsername());
        response.setProposedSessionTitle(proposal.getProposedTitle());
        response.setProposedAbstract(proposal.getProposedAbstract());
    }

    private static SpeakerPoolResponse.StatusEnum toStatusEnum(SpeakerWorkflowState state) {
        // Wire values are the UPPER_CASE enum name (e.g. "CONTENT_SUBMITTED") — matches the
        // legacy hand DTO's getStatus().name() and the FE's uppercase status comparisons.
        return state == null ? null : SpeakerPoolResponse.StatusEnum.fromValue(state.name());
    }

    private static SpeakerPoolResponse.SourceEnum toSourceEnum(String source) {
        return source == null ? null : SpeakerPoolResponse.SourceEnum.fromValue(source);
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
