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
  /**
   * When set, replaces the visible `To:` header with these addresses. Used by
   * the single-mail speaker broadcast so every speaker (and the Cc'd moderator)
   * sees the real recipient list instead of the `batbern{N}-speaker@` alias.
   */
  toRecipients?: string[];
  /**
   * When set (non-empty), sets/replaces the visible `Cc:` header. Used to put
   * the event moderator in Cc on the speaker broadcast.
   */
  ccRecipients?: string[];
  /**
   * When set, injects an `X-SES-CONFIGURATION-SET` header so SES attaches the
   * configuration set (delivery/bounce/complaint/reject event tracking). SES
   * consumes and strips this header before delivery.
   */
  configurationSet?: string;
}

/**
 * Rewrite raw MIME email for forwarding.
 * Replaces From, adds Reply-To, preserves everything else.
 */
export function rewriteEmail(rawEmail: string, options: RewriteOptions): string {
  const { senderName, senderEmail, sesSender, toRecipients, ccRecipients, configurationSet } =
    options;
  const overrideTo = toRecipients !== undefined && toRecipients.length > 0;
  const overrideCc = ccRecipients !== undefined;

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
  let hasCc = false;
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    const lowerLine = line.toLowerCase();

    // Override the visible To header with the resolved recipient list
    if (overrideTo && lowerLine.startsWith('to:')) {
      i++;
      while (i < lines.length && /^[ \t]/.test(lines[i])) {
        i++;
      }
      newLines.push(`To: ${toRecipients!.join(', ')}`);
      continue;
    }

    // Override (or drop) the visible Cc header
    if (overrideCc && lowerLine.startsWith('cc:')) {
      hasCc = true;
      i++;
      while (i < lines.length && /^[ \t]/.test(lines[i])) {
        i++;
      }
      if (ccRecipients!.length > 0) {
        newLines.push(`Cc: ${ccRecipients!.join(', ')}`);
      }
      continue;
    }

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

  // Add Cc header if requested but not already present (insert after To)
  if (overrideCc && ccRecipients!.length > 0 && !hasCc) {
    const toIdx = newLines.findIndex((l) => l.toLowerCase().startsWith('to:'));
    if (toIdx >= 0) {
      newLines.splice(toIdx + 1, 0, `Cc: ${ccRecipients!.join(', ')}`);
    } else {
      newLines.push(`Cc: ${ccRecipients!.join(', ')}`);
    }
  }

  // Attach the SES configuration set (delivery/bounce/complaint/reject tracking).
  // SES reads and strips this header before delivery.
  if (configurationSet) {
    newLines.push(`X-SES-CONFIGURATION-SET: ${configurationSet}`);
  }

  return newLines.join('\r\n') + bodySection;
}
