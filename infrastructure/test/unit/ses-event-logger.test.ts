/**
 * SES Event Logger Lambda — unit tests
 *
 * The logger turns SES configuration-set event notifications (delivered via SNS)
 * into one structured CloudWatch log line PER RECIPIENT, so an operator can answer
 * "did <address> deliver / bounce / get marked spam?" with a Logs Insights query.
 */

import type { SNSEvent } from 'aws-lambda';
import { handler } from '../../lambda/ses-event-logger/index';

function snsEvent(notification: unknown): SNSEvent {
  return {
    Records: [
      {
        EventSource: 'aws:sns',
        Sns: { Message: JSON.stringify(notification) },
      } as never,
    ],
  } as SNSEvent;
}

describe('ses-event-logger handler', () => {
  let logSpy: jest.SpyInstance;

  beforeEach(() => {
    logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
  });
  afterEach(() => {
    logSpy.mockRestore();
  });

  function loggedRecipients(): Array<Record<string, unknown>> {
    return logSpy.mock.calls
      .filter((c) => c[0] === 'SES delivery event')
      .map((c) => c[1] as Record<string, unknown>);
  }

  test('should_logDeliveryPerRecipient_when_deliveryNotification', async () => {
    await handler(
      snsEvent({
        eventType: 'Delivery',
        mail: {
          messageId: 'msg-1',
          source: 'noreply@batbern.ch',
          destination: ['sue.ajdini@enersuisse.ch', 'bob@example.com'],
        },
        delivery: {
          recipients: ['sue.ajdini@enersuisse.ch', 'bob@example.com'],
          smtpResponse: '250 2.0.0 OK',
        },
      })
    );

    const recs = loggedRecipients();
    expect(recs).toHaveLength(2);
    expect(recs.map((r) => r.recipient)).toEqual(
      expect.arrayContaining(['sue.ajdini@enersuisse.ch', 'bob@example.com'])
    );
    expect(recs[0].eventType).toBe('delivery');
    expect(recs[0].messageId).toBe('msg-1');
  });

  test('should_logBouncePerRecipient_withBounceType_when_bounceNotification', async () => {
    await handler(
      snsEvent({
        eventType: 'Bounce',
        mail: { messageId: 'msg-2', destination: ['gone@example.com'] },
        bounce: {
          bounceType: 'Permanent',
          bounceSubType: 'General',
          bouncedRecipients: [
            { emailAddress: 'gone@example.com', diagnosticCode: 'smtp; 550 5.1.1 user unknown' },
          ],
        },
      })
    );

    const recs = loggedRecipients();
    expect(recs).toHaveLength(1);
    expect(recs[0].recipient).toBe('gone@example.com');
    expect(recs[0].eventType).toBe('bounce');
    expect(String(recs[0].status)).toContain('Permanent');
    expect(String(recs[0].diagnostic)).toContain('550');
  });

  test('should_logComplaintPerRecipient_when_complaintNotification', async () => {
    await handler(
      snsEvent({
        eventType: 'Complaint',
        mail: { messageId: 'msg-3', destination: ['angry@example.com'] },
        complaint: { complainedRecipients: [{ emailAddress: 'angry@example.com' }] },
      })
    );

    const recs = loggedRecipients();
    expect(recs).toHaveLength(1);
    expect(recs[0].recipient).toBe('angry@example.com');
    expect(recs[0].eventType).toBe('complaint');
  });

  test('should_logReject_when_rejectNotification', async () => {
    await handler(
      snsEvent({
        eventType: 'Reject',
        mail: { messageId: 'msg-4', destination: ['virus@example.com'] },
        reject: { reason: 'Bad content' },
      })
    );

    const recs = loggedRecipients();
    expect(recs).toHaveLength(1);
    expect(recs[0].recipient).toBe('virus@example.com');
    expect(recs[0].eventType).toBe('reject');
    expect(String(recs[0].status)).toContain('Bad content');
  });

  test('should_notThrow_when_messageUnparseable', async () => {
    const errSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    const bad = { Records: [{ EventSource: 'aws:sns', Sns: { Message: 'not-json{' } }] } as never;
    await expect(handler(bad as SNSEvent)).resolves.toBeUndefined();
    expect(errSpy).toHaveBeenCalled();
    errSpy.mockRestore();
  });
});
