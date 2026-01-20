package ch.batbern.events.controller;

import ch.batbern.events.dto.BulkInvitationResponse;
import ch.batbern.events.dto.BulkSendInvitationRequest;
import ch.batbern.events.dto.InvitationResponse;
import ch.batbern.events.dto.RespondToInvitationRequest;
import ch.batbern.events.dto.SendInvitationRequest;
import ch.batbern.events.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for speaker invitations - Story 6.1.
 *
 * Endpoints:
 * - POST /api/v1/events/{eventCode}/invitations - Send invitation (ORGANIZER)
 * - GET /api/v1/events/{eventCode}/invitations - List invitations for event (ORGANIZER)
 * - GET /api/v1/speakers/{username}/invitations - List invitations for speaker (ORGANIZER/SPEAKER)
 * - GET /api/v1/invitations/respond/{token} - Get invitation details (PUBLIC)
 * - POST /api/v1/invitations/respond/{token} - Respond to invitation (PUBLIC)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Speaker Invitations", description = "Manage speaker invitations")
public class InvitationController {

    private final InvitationService invitationService;

    /**
     * Send an invitation to a speaker for an event.
     * Requires ORGANIZER role.
     */
    @PostMapping("/events/{eventCode}/invitations")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "Send speaker invitation",
            description = "Send an invitation to a speaker for a specific event",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponse(responseCode = "201", description = "Invitation sent successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Active invitation already exists")
    public ResponseEntity<InvitationResponse> sendInvitation(
            @PathVariable String eventCode,
            @Valid @RequestBody SendInvitationRequest request,
            Authentication authentication) {

        // Ensure event code matches request
        request.setEventCode(eventCode);

        String organizerUsername = authentication.getName();
        log.info("Organizer {} sending invitation to {} for event {}",
                organizerUsername, request.getUsername(), eventCode);

        InvitationResponse response = invitationService.sendInvitation(request, organizerUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Send bulk invitations to multiple speakers for an event.
     * Requires ORGANIZER role. Story 6.1 AC7.
     */
    @PostMapping("/events/{eventCode}/invitations/bulk")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "Send bulk speaker invitations",
            description = "Send invitations to multiple speakers for a specific event in one request",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponse(responseCode = "200", description = "Bulk operation completed (check response for individual results)")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    public ResponseEntity<BulkInvitationResponse> sendBulkInvitations(
            @PathVariable String eventCode,
            @Valid @RequestBody BulkSendInvitationRequest request,
            Authentication authentication) {

        String organizerUsername = authentication.getName();
        log.info("Organizer {} sending bulk invitations to {} speakers for event {}",
                organizerUsername, request.getUsernames().size(), eventCode);

        BulkInvitationResponse response = invitationService.sendBulkInvitations(eventCode, request, organizerUsername);
        return ResponseEntity.ok(response);
    }

    /**
     * List all invitations for an event.
     * Requires ORGANIZER role.
     */
    @GetMapping("/events/{eventCode}/invitations")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(
            summary = "List event invitations",
            description = "Get all invitations for a specific event",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<Page<InvitationResponse>> listEventInvitations(
            @PathVariable String eventCode,
            Pageable pageable) {

        Page<InvitationResponse> invitations = invitationService.listInvitationsByEvent(eventCode, pageable);
        return ResponseEntity.ok(invitations);
    }

    /**
     * List all invitations for a speaker.
     * Requires ORGANIZER or SPEAKER role.
     */
    @GetMapping("/speakers/{username}/invitations")
    @PreAuthorize("hasRole('ORGANIZER') or (hasRole('SPEAKER') and #username == authentication.name)")
    @Operation(
            summary = "List speaker invitations",
            description = "Get all invitations for a specific speaker",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<List<InvitationResponse>> listSpeakerInvitations(
            @PathVariable String username) {

        List<InvitationResponse> invitations = invitationService.listInvitationsBySpeaker(username);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Get invitation details by response token.
     * Public endpoint - used for passwordless speaker response.
     */
    @GetMapping("/invitations/respond/{token}")
    @Operation(
            summary = "Get invitation by token",
            description = "Get invitation details using the unique response token (no authentication required)"
    )
    public ResponseEntity<InvitationResponse> getInvitationByToken(
            @PathVariable String token) {

        log.debug("Fetching invitation by token");
        InvitationResponse response = invitationService.getInvitationByToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Respond to an invitation using the response token.
     * Public endpoint - used for passwordless speaker response.
     */
    @PostMapping("/invitations/respond/{token}")
    @Operation(
            summary = "Respond to invitation",
            description = "Submit a response to an invitation using the unique response token"
    )
    @ApiResponse(responseCode = "200", description = "Response recorded successfully")
    @ApiResponse(responseCode = "404", description = "Invitation not found")
    @ApiResponse(responseCode = "422", description = "Invitation expired or already responded")
    public ResponseEntity<InvitationResponse> respondToInvitation(
            @PathVariable String token,
            @Valid @RequestBody RespondToInvitationRequest request) {

        log.info("Processing invitation response: {}", request.getResponseType());
        InvitationResponse response = invitationService.respondToInvitation(token, request);
        return ResponseEntity.ok(response);
    }
}
