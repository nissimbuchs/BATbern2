/**
 * notificationApiClient error mapping, on the MSW harness (Epic 17 test fidelity).
 *
 * The client runs with the real apiClient, so the errors are the ones real axios raises: an HTTP
 * error reaches the caller as an `Error` with the server's message (else axios' own) and the
 * correlation id when the response carries one. The success paths are in
 * `notificationApiClient.test.ts`, also on the harness.
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { isAxiosError } from 'axios';
import { api } from '@/test/msw/api';
import { notificationApiClient } from './notificationApiClient';

const ID = '3f2a9c1e-7b4d-4e8a-9c0f-1a2b3c4d5e6f';

/** Every method of the client, with the operation it calls. */
const methods = [
  ['listNotifications', () => notificationApiClient.getNotifications()],
  ['getUnreadCount', () => notificationApiClient.getUnreadCount()],
  ['updateNotification', () => notificationApiClient.markAsRead(ID)],
  ['updateNotifications', () => notificationApiClient.batchMarkAsRead([ID])],
  ['deleteNotification', () => notificationApiClient.deleteNotification(ID)],
] as const;

/** Resolves to what `promise` rejected with; fails the test when it resolves. */
const rejectionOf = async (promise: Promise<unknown>): Promise<unknown> =>
  promise.then(
    () => {
      throw new Error('expected a rejection');
    },
    (e: unknown) => e
  );

describe('notificationApiClient errors', () => {
  it.each(methods)('%s maps a 500 to its message and correlation id', async (op, call) => {
    api.on(op, () =>
      HttpResponse.json(
        { status: 500, error: 'Internal Server Error', message: 'Notification store down' },
        { status: 500, headers: { 'x-correlation-id': 'corr-7' } }
      )
    );

    const error = await rejectionOf(call());

    expect(api.calls(op)).toHaveLength(1);
    expect(isAxiosError(error)).toBe(false);
    expect(error).toEqual(new Error('Notification store down (Correlation ID: corr-7)'));
  });

  it('should_keepMessage_when_noCorrelationId', async () => {
    api.on('updateNotification', () =>
      HttpResponse.json(
        { status: 404, error: 'Not Found', message: 'Notification not found' },
        { status: 404 }
      )
    );

    await expect(notificationApiClient.markAsRead(ID)).rejects.toEqual(
      new Error('Notification not found')
    );
  });

  it('should_useAxiosMessage_when_networkFails', async () => {
    api.on('deleteNotification', () => HttpResponse.error());

    await expect(notificationApiClient.deleteNotification(ID)).rejects.toEqual(
      new Error('Network Error')
    );
  });
});
