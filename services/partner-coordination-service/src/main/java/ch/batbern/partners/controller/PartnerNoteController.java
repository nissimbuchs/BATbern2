package ch.batbern.partners.controller;

import ch.batbern.partners.dto.CreateNoteRequest;
import ch.batbern.partners.dto.PartnerNoteDTO;
import ch.batbern.partners.dto.UpdateNoteRequest;
import ch.batbern.partners.service.PartnerNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for partner notes — Story 8.4.
 *
 * All endpoints require ORGANIZER role (AC1).
 * Standalone controller — does NOT implement generated OpenAPI interface
 * (same pattern as PartnerMeetingController, avoids regressions in PartnerController).
 */
@RestController
@RequestMapping("/api/v1/partners/{companyName}/notes")
@RequiredArgsConstructor
@Slf4j
public class PartnerNoteController {

    private final PartnerNoteService noteService;

    /**
     * GET /api/v1/partners/{companyName}/notes
     * List all notes sorted by created_at descending (AC2).
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<PartnerNoteDTO>> listNotes(@PathVariable String companyName) {
        return ResponseEntity.ok(noteService.getNotes(companyName));
    }

    /**
     * POST /api/v1/partners/{companyName}/notes
     * Create a note — title and content required. Returns 201 (AC3).
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerNoteDTO> createNote(
            @PathVariable String companyName,
            @Valid @RequestBody CreateNoteRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String authorUsername = extractUsername(jwt);
        PartnerNoteDTO created = noteService.createNote(companyName, request, authorUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PATCH /api/v1/partners/{companyName}/notes/{noteId}
     * Partial update — only provided non-null fields changed (AC4).
     */
    @PatchMapping("/{noteId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerNoteDTO> updateNote(
            @PathVariable String companyName,
            @PathVariable UUID noteId,
            @RequestBody UpdateNoteRequest request
    ) {
        return ResponseEntity.ok(noteService.updateNote(noteId, request));
    }

    /**
     * DELETE /api/v1/partners/{companyName}/notes/{noteId}
     * Delete a note. Returns 204 No Content (AC5).
     */
    @DeleteMapping("/{noteId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteNote(
            @PathVariable String companyName,
            @PathVariable UUID noteId
    ) {
        noteService.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }

    private String extractUsername(Jwt jwt) {
        if (jwt == null) {
            return "system";
        }
        String username = jwt.getClaimAsString("custom:username");
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        return username != null ? username : "system";
    }
}
