/**
 * Dev-only email service for the local email inbox.
 * Aggregates captured emails from all services that have a DevEmailController.
 *
 * Currently: Event Management Service (EMS, :8002) + Partner Coordination Service (PCS, :8004).
 * Only used by DevEmailInboxPage (accessible at /dev/emails in local dev).
 */

const EMS_BASE = `http://localhost:${import.meta.env.VITE_EMS_PORT ?? 8002}`;
const PCS_BASE = `http://localhost:${import.meta.env.VITE_PCS_PORT ?? 8004}`;

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

/** Frontend-only: CapturedEmail annotated with the service base URL it came from. */
export interface CapturedEmailWithSource extends CapturedEmail {
  _sourceBaseUrl: string;
}

async function fetchFromService(baseUrl: string): Promise<CapturedEmailWithSource[]> {
  try {
    const response = await fetch(`${baseUrl}/dev/emails`);
    if (!response.ok) return [];
    const emails: CapturedEmail[] = await response.json();
    return emails.map((e) => ({ ...e, _sourceBaseUrl: baseUrl }));
  } catch {
    // Service may not be running — silently return empty
    return [];
  }
}

export const devEmailService = {
  fetchAll: async (): Promise<CapturedEmailWithSource[]> => {
    const [emsEmails, pcsEmails] = await Promise.all([
      fetchFromService(EMS_BASE),
      fetchFromService(PCS_BASE),
    ]);
    // Merge and sort newest first
    return [...emsEmails, ...pcsEmails].sort(
      (a, b) => new Date(b.capturedAt).getTime() - new Date(a.capturedAt).getTime()
    );
  },

  /** Returns the direct download URL for an attachment (opens in browser / triggers save). */
  attachmentDownloadUrl: (email: CapturedEmailWithSource, filename: string): string =>
    `${email._sourceBaseUrl}/dev/emails/${email.id}/attachments/${filename}`,

  clearAll: async (): Promise<void> => {
    await Promise.all([
      fetch(`${EMS_BASE}/dev/emails`, { method: 'DELETE' }),
      fetch(`${PCS_BASE}/dev/emails`, { method: 'DELETE' }).catch(() => undefined),
    ]);
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
