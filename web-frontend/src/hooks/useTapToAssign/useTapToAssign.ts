/**
 * useTapToAssign (Story 15.3)
 *
 * Touch-friendly slot assignment: tap a session to "arm" it, then tap a target slot to
 * place it. Resolves the right backend mode from the armed source and the target state:
 *
 * - armed from the pool + empty target  → ASSIGN
 * - armed from the pool + occupied target → INSERT (push the occupant + reflow)
 * - armed from a slot  + empty target   → ASSIGN (move)
 * - armed from a slot  + occupied target → SWAP
 *
 * Extracted from the inline tap logic in DragDropSlotAssignment (14.G.3) and generalised to
 * also support tap-to-swap. Desktop keeps native HTML5 drag; this hook drives touch viewports.
 */

import { useCallback, useState } from 'react';
import type { SlotAssignmentMode } from '@/services/slotAssignmentService/slotAssignmentService';

/** The currently "picked up" session. `fromSlotKey` is null when armed from the pool. */
export interface ArmedSession {
  sessionSlug: string;
  fromSlotKey: string | null;
}

export interface UseTapToAssignArgs {
  /** Commit an assignment. Receives the resolved mode. */
  commit: (
    sessionSlug: string,
    targetSlotKey: string,
    mode: SlotAssignmentMode
  ) => Promise<void> | void;
}

export interface UseTapToAssignReturn {
  armed: ArmedSession | null;
  armedSessionSlug: string | null;
  /** True while any session is armed (used to highlight candidate target slots). */
  hasArmed: boolean;
  isArmed: (sessionSlug: string) => boolean;
  /** Tap a pool session: arms it, or disarms if it was already the armed pool session. */
  armFromPool: (sessionSlug: string) => void;
  /** Tap an assigned slot: arms its occupant for a move/swap, or disarms if re-tapped. */
  armFromSlot: (sessionSlug: string, slotKey: string) => void;
  /** Tap a target slot. No-op if nothing is armed. */
  tapSlot: (targetSlotKey: string, occupiedSessionSlug: string | null) => Promise<void>;
  clear: () => void;
}

export function useTapToAssign({ commit }: UseTapToAssignArgs): UseTapToAssignReturn {
  const [armed, setArmed] = useState<ArmedSession | null>(null);

  const armFromPool = useCallback((sessionSlug: string) => {
    setArmed((prev) =>
      prev && prev.fromSlotKey === null && prev.sessionSlug === sessionSlug
        ? null
        : { sessionSlug, fromSlotKey: null }
    );
  }, []);

  const armFromSlot = useCallback((sessionSlug: string, slotKey: string) => {
    setArmed((prev) =>
      prev && prev.fromSlotKey === slotKey ? null : { sessionSlug, fromSlotKey: slotKey }
    );
  }, []);

  const clear = useCallback(() => setArmed(null), []);

  const tapSlot = useCallback(
    async (targetSlotKey: string, occupiedSessionSlug: string | null) => {
      if (!armed) {
        return;
      }
      // Tapping the slot you armed from cancels the pick-up.
      if (armed.fromSlotKey === targetSlotKey) {
        setArmed(null);
        return;
      }
      const targetOccupied = !!occupiedSessionSlug && occupiedSessionSlug !== armed.sessionSlug;
      let mode: SlotAssignmentMode;
      if (!targetOccupied) {
        mode = 'ASSIGN';
      } else if (armed.fromSlotKey) {
        mode = 'SWAP';
      } else {
        mode = 'INSERT';
      }
      try {
        await commit(armed.sessionSlug, targetSlotKey, mode);
      } finally {
        setArmed(null);
      }
    },
    [armed, commit]
  );

  return {
    armed,
    armedSessionSlug: armed?.sessionSlug ?? null,
    hasArmed: armed !== null,
    isArmed: (sessionSlug: string) => armed?.sessionSlug === sessionSlug,
    armFromPool,
    armFromSlot,
    tapSlot,
    clear,
  };
}
