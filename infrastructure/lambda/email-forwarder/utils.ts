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

/** Extract all email addresses from a header value (To or Cc may contain multiple). */
export function extractAllAddresses(header: string | undefined): string[] {
  if (!header || header.trim().length === 0) return [];
  const addresses: string[] = [];
  // Match all angle-bracket addresses and bare addresses
  const angleBrackets = header.matchAll(/<([^>]+)>/g);
  for (const m of angleBrackets) {
    addresses.push(m[1].toLowerCase());
  }
  if (addresses.length > 0) return addresses;
  // Fallback: split by comma and extract bare addresses
  for (const part of header.split(',')) {
    const match = part.trim().match(/([^\s]+@[^\s]+)/);
    if (match) {
      addresses.push(match[1].toLowerCase());
    }
  }
  return addresses;
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

/**
 * Detect iMIP calendar acceptance/reply emails (RFC 5546).
 *
 * Mail clients (Apple Mail, Outlook, Google Calendar) auto-send a `METHOD:REPLY`
 * iCalendar to the ORGANIZER when the user accepts/declines an invitation. If
 * the ORGANIZER address is a forwarding alias (e.g. events@batbern.ch), every
 * acceptance fans out to the entire organizer list. Drop these at the
 * forwarder.
 *
 * Returns true if any Content-Type header (top-level or inside any MIME part)
 * carries both `text/calendar` and `method=REPLY`.
 */
export function isCalendarReply(raw: string): boolean {
  // Unfold RFC 5322 continuation lines (lines starting with whitespace fold
  // into the previous line) so multi-line Content-Type headers parse correctly.
  const unfolded = raw.replace(/\r?\n[ \t]+/g, ' ');
  for (const line of unfolded.split(/\r?\n/)) {
    if (!/^content-type\s*:/i.test(line)) continue;
    if (/text\/calendar/i.test(line) && /method\s*=\s*"?reply"?/i.test(line)) {
      return true;
    }
  }
  return false;
}
