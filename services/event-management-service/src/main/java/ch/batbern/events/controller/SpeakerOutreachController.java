package ch.batbern.events.controller;

import ch.batbern.events.api.generated.SpeakerOutreachApi;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.dto.generated.OutreachHistoryResponse;
import ch.batbern.events.dto.generated.RecordOutreachRequest;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SpeakerOutreachService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for Speaker Outreach Tracking (Story 5.3).
 *
 * Implements generated SpeakerOutreachApi interface from OpenAPI spec.
 * Contract-first approach (ADR-006) ensures API consistency.
 *
 * Endpoints:
 * - POST /api/v1/events/{eventCode}/speakers/{speakerId}/outreach - Record outreach attempt (AC1-4)
 * - GET /api/v1/events/{eventCode}/speakers/{speakerId}/outreach - Get outreach history (AC5-6)
 *
 * Authorization:
 * - All endpoints require ORGANIZER role (@PreAuthorize)
 *
 * Business Logic:
 * - Records contact attempts with speakers during outreach phase
 * - Automatically transitions speaker from IDENTIFIED → CONTACTED on first outreach
 * - Validates speaker state (must be IDENTIFIED, CONTACTED, or READY)
 * - Captures organizer username from JWT authentication context
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SpeakerOutreachController implements SpeakerOutreachApi {

    private final SpeakerOutreachService outreachService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * POST /api/v1/events/{eventCode}/speakers/{speakerId}/outreach
     * Record a speaker outreach attempt.
     *
     * @param eventCode Event code (not currently used, but part of REST resource hierarchy)
     * @param speakerId UUID of the speaker in the speaker pool
     * @param recordOutreachRequest Request containing contact details
     * @return 201 CREATED with outreach history record
     *         400 BAD REQUEST if validation fails (e.g., invalid contact method)
     *         404 NOT FOUND if speaker doesn't exist
     *         409 CONFLICT if speaker not in valid state for outreach
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<OutreachHistoryResponse> recordSpeakerOutreach(
            String eventCode,
            UUID speakerId,
            RecordOutreachRequest recordOutreachRequest) {

        log.debug("POST /api/v1/events/{}/speakers/{}/outreach - Record outreach attempt",
                eventCode, speakerId);

        // Get organizer username from JWT authentication context
        String organizerUsername = securityContextHelper.getCurrentUserEmail();
        log.debug("Organizer username from JWT: {}", organizerUsername);

        // Convert OffsetDateTime to Instant (DTO uses OffsetDateTime, service uses Instant)
        Instant contactDate = recordOutreachRequest.getContactDate().toInstant();

        // Convert enum to string for service layer
        String contactMethod = recordOutreachRequest.getContactMethod().getValue();

        // Call service layer
        OutreachHistory outreach = outreachService.recordOutreach(
                speakerId,
                contactDate,
                contactMethod,
                recordOutreachRequest.getNotes(),
                organizerUsername
        );

        // Convert domain entity to response DTO
        OutreachHistoryResponse response = toResponse(outreach);

        log.info("Successfully recorded outreach attempt {} for speaker {}",
                response.getId(), speakerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/events/{eventCode}/speakers/{speakerId}/outreach
     * Get speaker outreach history.
     *
     * @param eventCode Event code (not currently used, but part of REST resource hierarchy)
     * @param speakerId UUID of the speaker in the speaker pool
     * @return 200 OK with list of outreach attempts (most recent first)
     *         404 NOT FOUND if speaker doesn't exist
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<OutreachHistoryResponse>> getSpeakerOutreachHistory(
            String eventCode,
            UUID speakerId) {

        log.debug("GET /api/v1/events/{}/speakers/{}/outreach - Get outreach history",
                eventCode, speakerId);

        // Call service layer
        List<OutreachHistory> history = outreachService.getOutreachHistory(speakerId);

        // Convert domain entities to response DTOs
        List<OutreachHistoryResponse> responses = history.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        log.debug("Returning {} outreach attempts for speaker {}", responses.size(), speakerId);

        return ResponseEntity.ok(responses);
    }

    /**
     * Convert OutreachHistory domain entity to OutreachHistoryResponse DTO.
     *
     * @param outreach Domain entity
     * @return Response DTO
     */
    private OutreachHistoryResponse toResponse(OutreachHistory outreach) {
        OutreachHistoryResponse response = new OutreachHistoryResponse();
        response.setId(outreach.getId());
        response.setSpeakerPoolId(outreach.getSpeakerPoolId());

        // Convert Instant to OffsetDateTime (DTO uses OffsetDateTime, domain uses Instant)
        response.setContactDate(outreach.getContactDate().atOffset(ZoneOffset.UTC));

        // Convert contact method string to enum
        response.setContactMethod(
                OutreachHistoryResponse.ContactMethodEnum.fromValue(outreach.getContactMethod())
        );

        response.setNotes(outreach.getNotes());
        response.setOrganizerUsername(outreach.getOrganizerUsername());

        // Convert Instant to OffsetDateTime for timestamps
        response.setCreatedAt(outreach.getCreatedAt().atOffset(ZoneOffset.UTC));
        response.setUpdatedAt(outreach.getUpdatedAt().atOffset(ZoneOffset.UTC));

        return response;
    }
}
