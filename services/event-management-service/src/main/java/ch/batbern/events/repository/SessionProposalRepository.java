package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Story 7.2 / ADR-012 — repository for self-nomination pitches ({@link SessionProposal}).
 */
@Repository
public interface SessionProposalRepository extends JpaRepository<SessionProposal, UUID> {

    /** The pitch attached to a given pool row (kanban read + the promote-to-READY carry-over). */
    Optional<SessionProposal> findBySpeakerPoolId(UUID speakerPoolId);

    /** Batch variant for building the organizer kanban list without an N+1. */
    List<SessionProposal> findBySpeakerPoolIdIn(Collection<UUID> speakerPoolIds);

    /** One self-nomination per attendee per event (AC8′ friendly pre-check). */
    boolean existsByEventIdAndProposedByUsername(UUID eventId, String proposedByUsername);
}
