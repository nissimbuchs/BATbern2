/**
 * Pure email parsing utilities (Story 10.26)
 *
 * Extracted from index.ts so unit tests can import these functions
 * without pulling in @aws-sdk/* dependencies.
 */

/** Parse email headers from raw MIME content. */
export function parseHeaders(raw: string): Record<string, string> {
  const headerSection = raw.split(/\r?\n\r?\n/)[0] ?? '';
  const headers: Record<string, string> = {};
  // Unfold continuation lines (lines starting with whitespace)
  const unfolded = headerSection.replace(/\r?\n[ \t]+/g, ' ');
  for (const line of unfolded.split(/\r?\n/)) {
    const colonIdx = line.indexOf(':');
    if (colonIdx > 0) {
      const name = line.substring(0, colonIdx).trim().toLowerCase();
      const value = line.substring(colonIdx + 1).trim();
      headers[name] = value;
    }
  }
  return headers;
}

/** Extract the first email address from a To header value. */
export function extractToAddress(to: string | undefined): string | undefined {
  if (!to) return undefined;
  const match = to.match(/<([^>]+)>/) ?? to.match(/([^\s,]+@[^\s,]+)/);
  return match?.[1]?.toLowerCase();
}

/** Extract email address from a From header value. */
export function extractSenderEmail(from: string | undefined): string | undefined {
  if (!from) return undefined;
  const match = from.match(/<([^>]+)>/) ?? from.match(/([^\s]+@[^\s]+)/);
  return match?.[1]?.toLowerCase();
}

/** Extract display name from a From header value. */
export function extractSenderName(from: string | undefined): string {
  if (!from) return 'Unknown';
  // "John Doe <john@example.com>" → "John Doe"
  const match = from.match(/^"?([^"<]+)"?\s*</);
  return match?.[1]?.trim() ?? from.split('@')[0] ?? 'Unknown';
}

/** Truncate email for logging (first 5 chars + ***). */
export function truncateEmail(email: string): string {
  if (email.length <= 5) return email + '***';
  return email.substring(0, 5) + '***';
}

/** Exclude sender from recipients to prevent bounce loops. */
export function excludeSender(recipients: string[], senderEmail: string): string[] {
  const senderLower = senderEmail.toLowerCase();
  return recipients.filter((r) => r.toLowerCase() !== senderLower);
}
