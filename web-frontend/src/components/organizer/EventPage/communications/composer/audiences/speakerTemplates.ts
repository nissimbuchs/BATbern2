import type { SpeakerReminderType } from '../types';

/** The six reminder templates; the SPEAKER category also holds invitations and Q&A mails. */
export const REMINDER_KEY = /^speaker-reminder-(response|content)-tier[123]$/;
export const REMINDER_TYPES: SpeakerReminderType[] = ['RESPONSE', 'CONTENT'];

/** The reminder type a chosen template targets, or null in automatic mode. */
export function reminderTypeOf(templateKey: string | null): SpeakerReminderType | null {
  const match = templateKey ? REMINDER_KEY.exec(templateKey) : null;
  if (!match) return null;
  return match[1] === 'content' ? 'CONTENT' : 'RESPONSE';
}
