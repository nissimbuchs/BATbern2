/**
 * Dev-only email service for the local email inbox.
 * Talks directly to the Event Management Service — no auth, no API gateway needed.
 *
 * Only used by DevEmailInboxPage (accessible at /dev/emails in local dev).
 */

const EMS_BASE = `http://localhost:${import.meta.env.VITE_EMS_PORT ?? 8002}`;

export interface CapturedEmail {
  id: string;
  to: string;
  subject: string;
  htmlBody: string;
  fromEmail: string;
  fromName: string;
  capturedAt: string; // ISO 8601
  attachments: Array<{
    filename: string;
    mimeType: string;
    sizeBytes: number;
  }>;
}

export const devEmailService = {
  fetchAll: async (): Promise<CapturedEmail[]> => {
    const response = await fetch(`${EMS_BASE}/dev/emails`);
    if (!response.ok) {
      throw new Error(`Failed to fetch emails: ${response.status}`);
    }
    return response.json();
  },

  clearAll: async (): Promise<void> => {
    const response = await fetch(`${EMS_BASE}/dev/emails`, { method: 'DELETE' });
    if (!response.ok && response.status !== 204) {
      throw new Error(`Failed to clear inbox: ${response.status}`);
    }
  },

  /**
   * Simulate an inbound email reply for local dev testing (Story 10.17 — AC11).
   * Constructs a ParsedEmail from the captured email and routes it via InboundEmailRouter.
   * Any resulting confirmation email will appear in the inbox after a refresh.
   */
  replyToEmail: async (id: string, replyBody: string): Promise<string> => {
    const response = await fetch(`${EMS_BASE}/dev/emails/${id}/reply`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ replyBody }),
    });
    if (!response.ok) {
      throw new Error(`Reply failed: ${response.status}`);
    }
    return response.text();
  },
};
