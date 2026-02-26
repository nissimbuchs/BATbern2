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
};
