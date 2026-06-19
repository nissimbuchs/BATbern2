/**
 * SES Event Logger Lambda
 *
 * Subscribed (via SNS) to the dedicated forwarder SES configuration set's event
 * destination. Turns each SES event notification into one structured CloudWatch
 * log line PER RECIPIENT, so an operator can answer "did <address> deliver /
 * bounce / get marked as spam?" with a Logs Insights query, e.g.:
 *
 *   fields @timestamp, eventType, recipient, status, diagnostic
 *   | filter recipient = "sue.ajdini@enersuisse.ch"
 *   | sort @timestamp desc
 *
 * Decoupled from the newsletter bounce pipeline (its own topic + log group): a
 * speaker bounce logged here never reaches BounceProcessingService / suppression.
 */

import type { SNSEvent } from 'aws-lambda';

interface BouncedRecipient {
  emailAddress?: string;
  diagnosticCode?: string;
}

interface SesNotification {
  eventType?: string;
  notificationType?: string; // older SES notifications use this key
  mail?: { messageId?: string; source?: string; destination?: string[] };
  delivery?: { recipients?: string[]; smtpResponse?: string };
  bounce?: { bounceType?: string; bounceSubType?: string; bouncedRecipients?: BouncedRecipient[] };
  complaint?: { complainedRecipients?: Array<{ emailAddress?: string }>; complaintFeedbackType?: string };
  reject?: { reason?: string };
}

interface PerRecipientRow {
  recipient: string;
  status: string;
  diagnostic?: string;
}

export const handler = async (event: SNSEvent): Promise<void> => {
  for (const record of event.Records) {
    let notification: SesNotification;
    try {
      notification = JSON.parse(record.Sns.Message) as SesNotification;
    } catch {
      console.error('Unparseable SES event message', {
        raw: record.Sns.Message?.slice(0, 200),
      });
      continue;
    }

    const eventType = (notification.eventType ?? notification.notificationType ?? 'unknown').toLowerCase();
    const mail = notification.mail ?? {};
    const base = { eventType, messageId: mail.messageId, source: mail.source };

    const rows = perRecipientRows(eventType, notification, mail.destination ?? []);
    if (rows.length === 0) {
      console.log('SES delivery event', base);
      continue;
    }
    for (const row of rows) {
      console.log('SES delivery event', {
        ...base,
        recipient: row.recipient,
        status: row.status,
        diagnostic: row.diagnostic,
      });
    }
  }
};

function perRecipientRows(
  eventType: string,
  n: SesNotification,
  destination: string[],
): PerRecipientRow[] {
  switch (eventType) {
    case 'delivery':
      return (n.delivery?.recipients ?? destination).map((r) => ({
        recipient: r,
        status: 'delivered',
        diagnostic: n.delivery?.smtpResponse,
      }));
    case 'bounce': {
      const type = `${n.bounce?.bounceType ?? '?'}/${n.bounce?.bounceSubType ?? '?'}`;
      return (n.bounce?.bouncedRecipients ?? []).map((r) => ({
        recipient: r.emailAddress ?? 'unknown',
        status: `bounce:${type}`,
        diagnostic: r.diagnosticCode,
      }));
    }
    case 'complaint':
      return (n.complaint?.complainedRecipients ?? []).map((r) => ({
        recipient: r.emailAddress ?? 'unknown',
        status: `complaint:${n.complaint?.complaintFeedbackType ?? 'unspecified'}`,
      }));
    case 'reject':
      return destination.map((r) => ({
        recipient: r,
        status: `reject:${n.reject?.reason ?? 'unspecified'}`,
      }));
    default:
      return destination.map((r) => ({ recipient: r, status: eventType }));
  }
}
