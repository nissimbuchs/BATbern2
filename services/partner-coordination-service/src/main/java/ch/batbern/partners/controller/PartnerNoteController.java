package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnerNotesApi;
import ch.batbern.partners.dto.generated.CreateNoteRequest;
import ch.batbern.partners.dto.generated.PartnerNoteDTO;
import ch.batbern.partners.dto.generated.UpdateNoteRequest;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.partners.service.PartnerNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for partner notes — Story 8.4.
 *
 * Implements the generated {@link PartnerNotesApi} interface from the consolidated
 * partners-api OpenAPI spec (Phase 5). All endpoints require ORGANIZER role (AC1).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PartnerNoteController implements PartnerNotesApi {

    private final PartnerNoteService noteService;
    private final SecurityContextHelper securityContextHelper;

    /** List all notes sorted by created_at descending (AC2). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<PartnerNoteDTO>> listPartnerNotes(String companyName) {
        return ResponseEntity.ok(noteService.getNotes(companyName));
    }

    /** Create a note — title and content required. Returns 201 (AC3). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerNoteDTO> createPartnerNote(String companyName, CreateNoteRequest createNoteRequest) {
        String authorUsername = securityContextHelper.getCurrentUsername();
        PartnerNoteDTO created = noteService.createNote(companyName, createNoteRequest, authorUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Partial update — only provided non-null fields changed (AC4).
     * companyName is passed to the service for ownership validation (H1).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerNoteDTO> updatePartnerNote(
            String companyName, UUID noteId, UpdateNoteRequest updateNoteRequest) {
        return ResponseEntity.ok(noteService.updateNote(companyName, noteId, updateNoteRequest));
    }

    /**
     * Delete a note. Returns 204 No Content (AC5).
     * companyName is passed to the service for ownership validation (H1).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deletePartnerNote(String companyName, UUID noteId) {
        noteService.deleteNote(companyName, noteId);
        return ResponseEntity.noContent().build();
    }
}
