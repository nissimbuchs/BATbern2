package ch.batbern.partners.service;

import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnerNote;
import ch.batbern.partners.dto.CreateNoteRequest;
import ch.batbern.partners.dto.PartnerNoteDTO;
import ch.batbern.partners.dto.UpdateNoteRequest;
import ch.batbern.partners.exception.PartnerNoteNotFoundException;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerNoteRepository;
import ch.batbern.partners.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for partner notes — Story 8.4 (AC2-5).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerNoteService {

    private final PartnerNoteRepository noteRepository;
    private final PartnerRepository partnerRepository;

    /**
     * List all notes for a partner sorted by created_at descending (AC2).
     *
     * @param companyName partner company name (ADR-003)
     * @return list of note DTOs, empty if none exist
     * @throws PartnerNotFoundException if companyName is unknown
     */
    @Transactional(readOnly = true)
    public List<PartnerNoteDTO> getNotes(String companyName) {
        Partner partner = findPartnerOrThrow(companyName);
        return noteRepository.findByPartnerIdOrderByCreatedAtDesc(partner.getId())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a note for a partner (AC3).
     *
     * @param companyName    partner company name
     * @param req            create request with title and content
     * @param authorUsername JWT username of the creating organizer
     * @return created note DTO
     * @throws PartnerNotFoundException if companyName is unknown
     */
    @Transactional
    public PartnerNoteDTO createNote(String companyName, CreateNoteRequest req, String authorUsername) {
        Partner partner = findPartnerOrThrow(companyName);

        PartnerNote note = PartnerNote.builder()
                .partnerId(partner.getId())
                .title(req.getTitle())
                .content(req.getContent())
                .authorUsername(authorUsername)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PartnerNote saved = noteRepository.save(note);
        log.debug("Created partner note {} for partner {}", saved.getId(), companyName);
        return toDTO(saved);
    }

    /**
     * Partially update a note (AC4).
     * Only non-null fields in the request are applied.
     * Verifies the note belongs to the partner identified by companyName (H1 fix).
     *
     * @param companyName partner company name — ownership check
     * @param noteId      note UUID
     * @param req         update request (all fields optional)
     * @return updated note DTO
     * @throws PartnerNotFoundException     if companyName is unknown
     * @throws PartnerNoteNotFoundException if noteId is unknown or belongs to a different partner
     */
    @Transactional
    public PartnerNoteDTO updateNote(String companyName, UUID noteId, UpdateNoteRequest req) {
        Partner partner = findPartnerOrThrow(companyName);
        PartnerNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new PartnerNoteNotFoundException("Note not found: " + noteId));

        if (!note.getPartnerId().equals(partner.getId())) {
            // Return 404 — do not reveal that the note exists for another partner
            throw new PartnerNoteNotFoundException("Note not found: " + noteId);
        }

        if (req.getTitle() != null) {
            note.setTitle(req.getTitle());
        }
        if (req.getContent() != null) {
            note.setContent(req.getContent());
        }
        note.setUpdatedAt(Instant.now());

        PartnerNote saved = noteRepository.save(note);
        return toDTO(saved);
    }

    /**
     * Delete a note (AC5).
     * Verifies the note belongs to the partner identified by companyName (H1 fix).
     *
     * @param companyName partner company name — ownership check
     * @param noteId      note UUID
     * @throws PartnerNotFoundException     if companyName is unknown
     * @throws PartnerNoteNotFoundException if noteId is unknown or belongs to a different partner
     */
    @Transactional
    public void deleteNote(String companyName, UUID noteId) {
        Partner partner = findPartnerOrThrow(companyName);
        PartnerNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new PartnerNoteNotFoundException("Note not found: " + noteId));

        if (!note.getPartnerId().equals(partner.getId())) {
            throw new PartnerNoteNotFoundException("Note not found: " + noteId);
        }

        noteRepository.delete(note);
        log.debug("Deleted partner note {}", noteId);
    }

    private Partner findPartnerOrThrow(String companyName) {
        return partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found: " + companyName));
    }

    private PartnerNoteDTO toDTO(PartnerNote note) {
        return PartnerNoteDTO.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .authorUsername(note.getAuthorUsername())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
