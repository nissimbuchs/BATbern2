package ch.batbern.events.service.slotassignment;

/**
 * Mode for the stable slot-assignment endpoint (Story 15.3).
 *
 * <ul>
 *   <li>{@code ASSIGN} — place onto an empty target slot.</li>
 *   <li>{@code INSERT} — place at the target ordinal and shift every later assigned session
 *       one slot later (reflowing their times); 409 if the agenda is full.</li>
 *   <li>{@code SWAP} — exchange the dragged (already-assigned) session with the occupant of
 *       the target slot.</li>
 * </ul>
 */
public enum SlotAssignmentMode {
    ASSIGN,
    INSERT,
    SWAP
}
