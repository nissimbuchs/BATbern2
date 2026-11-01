/**
 * Story 16.9: a recorded partner meeting mail names its meeting as `partner-meeting:{meetingId}`.
 */
import type { MailMessageSummary } from '@/services/mailMessageService';

const MEETING_REFERENCE = 'partner-meeting:';
const INVITE_TEMPLATE = 'partner-meeting-invite';

/** The meeting id of a partner meeting invite row, or null for every other row. */
export function partnerMeetingId(row: MailMessageSummary): string | null {
  if (
    row.audience !== 'PARTNERS' ||
    row.templateKey !== INVITE_TEMPLATE ||
    !row.reference?.startsWith(MEETING_REFERENCE)
  ) {
    return null;
  }
  return row.reference.slice(MEETING_REFERENCE.length) || null;
}
