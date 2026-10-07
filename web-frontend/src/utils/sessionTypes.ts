/**
 * Structural (non-speaker) session types: the timetable slots that carry no talk.
 *
 * The single frontend definition, mirroring the backend's `Session.isStructuralType`
 * (services/event-management-service/.../domain/Session.java). `networking` is deliberately NOT
 * structural: the database allows it, but no event uses it (owner decision 2026-10-07). The API
 * declares `sessionType` as a free string, so this list cannot be generated from the spec.
 */
export const STRUCTURAL_SESSION_TYPES = ['moderation', 'break', 'lunch', 'aperitif'] as const;

export type StructuralSessionType = (typeof STRUCTURAL_SESSION_TYPES)[number];

/** True for moderation, break, lunch and aperitif (case-insensitive); false for null/undefined. */
export function isStructuralSession(sessionType: string | null | undefined): boolean {
  return (
    !!sessionType &&
    (STRUCTURAL_SESSION_TYPES as readonly string[]).includes(sessionType.toLowerCase())
  );
}
