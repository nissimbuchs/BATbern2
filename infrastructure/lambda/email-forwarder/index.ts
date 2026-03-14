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
  extractToAddress,
  extractSenderEmail,
  extractSenderName,
  truncateEmail,
  excludeSender,
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

    // Parse headers
    const headers = parseHeaders(rawEmail);
    const toAddress = extractToAddress(headers.to);
    const senderEmail = extractSenderEmail(headers.from);
    const senderName = extractSenderName(headers.from);

    if (!toAddress || !senderEmail) {
      console.warn('Missing To or From header', { key });
      continue;
    }

    const truncatedSender = truncateEmail(senderEmail);
    console.log('Processing forwarding', { to: toAddress, sender: truncatedSender });

    // Check sender authorization
    const authorized = await isAuthorizedSender(toAddress, senderEmail);
    if (!authorized) {
      console.warn('Unauthorized sender', { to: toAddress, sender: truncatedSender });
      await publishMetric('EmailsRejected');
      return;
    }

    // Resolve recipients
    const recipients = await resolveRecipients(toAddress);
    if (recipients.length === 0) {
      console.warn('No recipients resolved', { to: toAddress });
      await publishMetric('EmailsUnresolved');
      return;
    }

    // Exclude sender from recipients to prevent bounce loops
    const filteredRecipients = excludeSender(recipients, senderEmail);
    if (filteredRecipients.length < recipients.length) {
      console.log('Excluded sender from recipients', { sender: truncatedSender });
    }
    if (filteredRecipients.length === 0) {
      console.warn('No recipients after excluding sender', { to: toAddress });
      await publishMetric('EmailsUnresolved');
      return;
    }

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
    for (const recipient of filteredRecipients) {
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
      if (sentCount + failCount < filteredRecipients.length) {
        await delay(RATE_DELAY_MS);
      }
    }

    console.log('Forwarded email', {
      to: toAddress,
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
