/**
 * Email Forwarder Lambda (Story 10.26)
 *
 * Triggered by S3 OBJECT_CREATED events under the forwarding/ prefix.
 * Fetches raw MIME email from S3, resolves recipients by address alias,
 * checks sender authorization, rewrites headers, and re-sends via SES.
 */

import type { S3Event } from 'aws-lambda';
import { S3Client, GetObjectCommand } from '@aws-sdk/client-s3';
import { SESClient, SendRawEmailCommand } from '@aws-sdk/client-ses';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';
import { resolveRecipients } from './address-resolver';
import { isAuthorizedSender } from './sender-auth';
import { rewriteEmail } from './email-rewriter';
import {
  parseHeaders,
  extractAllAddresses,
  extractSenderEmail,
  extractSenderName,
  truncateEmail,
  // excludeSender,  // disabled — see commented block below; re-add to enable sender exclusion
  isCalendarReply,
} from './utils';

const s3 = new S3Client({});
const ses = new SESClient({});
const cw = new CloudWatchClient({});

const SES_SENDER = process.env.SES_SENDER_ADDRESS ?? 'noreply@batbern.ch';
const RATE_DELAY_MS = 70;
const METRIC_NAMESPACE = 'BATbern/EmailForwarder';

export const handler = async (event: S3Event): Promise<void> => {
  for (const record of event.Records) {
    const bucket = record.s3.bucket.name;
    const key = decodeURIComponent(record.s3.object.key.replace(/\+/g, ' '));

    // Fetch raw email from S3
    const obj = await s3.send(new GetObjectCommand({ Bucket: bucket, Key: key }));
    const rawEmail = await obj.Body!.transformToString('utf-8');

    // iMIP acceptance/decline replies: mail clients auto-send METHOD:REPLY to
    // the ORGANIZER on every Accept/Decline click. Forwarding these to the
    // entire organizer list creates inbox spam, so drop them at the edge.
    if (isCalendarReply(rawEmail)) {
      console.log('Dropping iMIP calendar reply', { key });
      await publishMetric('CalendarRepliesDropped');
      continue;
    }

    // Parse headers — extract all addresses from To and Cc
    const headers = parseHeaders(rawEmail);
    const forwardingDomain = process.env.FORWARDING_DOMAIN ?? 'batbern.ch';
    const toAddresses = extractAllAddresses(headers.to);
    const ccAddresses = extractAllAddresses(headers.cc);
    const allAddresses = [...toAddresses, ...ccAddresses].filter(
      (addr) => addr.endsWith(`@${forwardingDomain}`),
    );
    const senderEmail = extractSenderEmail(headers.from);
    const senderName = extractSenderName(headers.from);

    if (allAddresses.length === 0 || !senderEmail) {
      console.warn('Missing forwarding addresses or From header', { key });
      continue;
    }

    const truncatedSender = truncateEmail(senderEmail);
    console.log('Processing forwarding', { addresses: allAddresses, sender: truncatedSender });

    // Resolve recipients for each authorized address, then deduplicate
    const recipientSet = new Set<string>();
    let anyAuthorized = false;
    for (const addr of allAddresses) {
      const authorized = await isAuthorizedSender(addr, senderEmail);
      if (!authorized) {
        console.warn('Unauthorized sender for address', { to: addr, sender: truncatedSender });
        continue;
      }
      anyAuthorized = true;
      const resolved = await resolveRecipients(addr);
      for (const r of resolved) {
        recipientSet.add(r);
      }
    }

    if (!anyAuthorized) {
      await publishMetric('EmailsRejected');
      return;
    }

    const recipients = [...recipientSet];
    if (recipients.length === 0) {
      console.warn('No recipients resolved', { addresses: allAddresses });
      await publishMetric('EmailsUnresolved');
      return;
    }

    // Sender-exclusion intentionally disabled: forwarded copies are sent from
    // `noreply@batbern.ch` (rewritten From + envelope Source), so delivering a
    // copy back to the original sender does not produce a bounce or a re-
    // forwarding loop — resolved recipients are real mailbox addresses, not
    // batbern.ch aliases. The commit message that introduced this guard
    // (0a35eb0f) framed it as bounce prevention, but it was effectively a UX
    // preference. Leaving the block here so it can be restored quickly if a
    // future receipt setup changes that assumption — re-add `excludeSender`
    // to the import above and rename `recipients` → `filteredRecipients` in
    // the send loop below.
    //
    // const filteredRecipients = excludeSender(recipients, senderEmail);
    // if (filteredRecipients.length < recipients.length) {
    //   console.log('Excluded sender from recipients', { sender: truncatedSender });
    // }
    // if (filteredRecipients.length === 0) {
    //   console.warn('No recipients after excluding sender', { to: toAddresses });
    //   await publishMetric('EmailsUnresolved');
    //   return;
    // }

    // Rewrite email headers
    const rewrittenEmail = rewriteEmail(rawEmail, {
      originalFrom: headers.from,
      senderName: senderName,
      senderEmail: senderEmail,
      sesSender: SES_SENDER,
    });

    // Send to each recipient with rate limiting
    let sentCount = 0;
    let failCount = 0;
    for (const recipient of recipients) {
      try {
        await ses.send(
          new SendRawEmailCommand({
            Source: SES_SENDER,
            Destinations: [recipient],
            RawMessage: { Data: Buffer.from(rewrittenEmail) },
          }),
        );
        sentCount++;
      } catch (err) {
        failCount++;
        console.error('Failed to send to recipient', { recipient: truncateEmail(recipient), error: err });
      }
      if (sentCount + failCount < recipients.length) {
        await delay(RATE_DELAY_MS);
      }
    }

    console.log('Forwarded email', {
      addresses: allAddresses,
      sender: truncatedSender,
      recipientCount: sentCount,
      failedCount: failCount,
      outcome: 'forwarded',
    });
    await publishMetric('EmailsForwarded');
  }
};

async function publishMetric(metricName: string): Promise<void> {
  try {
    await cw.send(
      new PutMetricDataCommand({
        Namespace: METRIC_NAMESPACE,
        MetricData: [
          {
            MetricName: metricName,
            Value: 1,
            Unit: 'Count',
          },
        ],
      }),
    );
  } catch (err) {
    console.error('Failed to publish metric', { metricName, error: err });
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
