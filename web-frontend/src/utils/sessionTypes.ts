import type { components } from '@/types/generated/event-sessions-api.types';

/**
 * Structural (non-speaker) session types: the timetable slots that carry no talk.
 *
 * The single frontend definition, mirroring the backend's `Session.isStructuralType`
 * (services/event-management-service/.../domain/Session.java). `networking` is the apéro: V127
 * (#1123) folded the duplicate `aperitif` value back into it. `satisfies` keeps the list inside the
 * contract's `SessionType` enum.
 */
export const STRUCTURAL_SESSION_TYPES = [
  'moderation',
  'break',
  'lunch',
  'networking',
] as const satisfies readonly components['schemas']['SessionType'][];

export type StructuralSessionType = (typeof STRUCTURAL_SESSION_TYPES)[number];

/** True for moderation, break, lunch and networking (case-insensitive); false for null/undefined. */
export function isStructuralSession(sessionType: string | null | undefined): boolean {
  return (
    !!sessionType &&
    (STRUCTURAL_SESSION_TYPES as readonly string[]).includes(sessionType.toLowerCase())
  );
}
