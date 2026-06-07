/**
 * Email Rewriter (Story 10.26 — AC3)
 *
 * Rewrites MIME email headers for forwarding:
 *   - From: "{name} via BATbern <noreply@batbern.ch>"
 *   - Reply-To: original sender address
 *   - Subject: preserved as-is
 *   - Body and attachments: preserved
 */

export interface RewriteOptions {
  originalFrom: string;
  senderName: string;
  senderEmail: string;
  sesSender: string;
}

/**
 * Rewrite raw MIME email for forwarding.
 * Replaces From, adds Reply-To, preserves everything else.
 */
export function rewriteEmail(rawEmail: string, options: RewriteOptions): string {
  const { senderName, senderEmail, sesSender } = options;

  // Split into header and body sections
  const splitIndex = rawEmail.search(/\r?\n\r?\n/);
  if (splitIndex === -1) {
    return rawEmail;
  }

  const headerSection = rawEmail.substring(0, splitIndex);
  const bodySection = rawEmail.substring(splitIndex);

  // Process headers line by line (handling continuation lines)
  const lines = headerSection.split(/\r?\n/);
  const newLines: string[] = [];
  let hasReplyTo = false;
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    const lowerLine = line.toLowerCase();

    // Skip original From header (will add new one)
    if (lowerLine.startsWith('from:')) {
      i++;
      // Skip continuation lines
      while (i < lines.length && /^[ \t]/.test(lines[i])) {
        i++;
      }
      // Add rewritten From
      const displayName = senderName.replace(/"/g, '\\"');
      newLines.push(`From: "${displayName} via BATbern" <${sesSender}>`);
      continue;
    }

    // Track if Reply-To already exists (replace it)
    if (lowerLine.startsWith('reply-to:')) {
      hasReplyTo = true;
      i++;
      // Skip continuation lines
      while (i < lines.length && /^[ \t]/.test(lines[i])) {
        i++;
      }
      // Add Reply-To with original sender
      newLines.push(`Reply-To: ${senderEmail}`);
      continue;
    }

    // Skip Return-Path (SES will set this)
    if (lowerLine.startsWith('return-path:')) {
      i++;
      while (i < lines.length && /^[ \t]/.test(lines[i])) {
        i++;
      }
      continue;
    }

    // Skip DKIM-Signature (forwarded email re-signed by SES)
    if (lowerLine.startsWith('dkim-signature:')) {
      i++;
      while (i < lines.length && /^[ \t]/.test(lines[i])) {
        i++;
      }
      continue;
    }

    newLines.push(line);
    i++;
  }

  // Add Reply-To if not already present
  if (!hasReplyTo) {
    // Insert after From header
    const fromIdx = newLines.findIndex((l) => l.toLowerCase().startsWith('from:'));
    if (fromIdx >= 0) {
      newLines.splice(fromIdx + 1, 0, `Reply-To: ${senderEmail}`);
    } else {
      newLines.push(`Reply-To: ${senderEmail}`);
    }
  }

  return newLines.join('\r\n') + bodySection;
}
