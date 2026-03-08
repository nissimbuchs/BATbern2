/**
 * newsletterService Tests
 *
 * Coverage for:
 * - subscribe
 * - verifyUnsubscribeToken
 * - unsubscribeByToken
 * - getMySubscription
 * - patchMySubscription
 * - getSubscriberCount
 * - getNewsletterHistory
 * - previewNewsletter
 * - sendNewsletter
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  subscribe,
  verifyUnsubscribeToken,
  unsubscribeByToken,
  getMySubscription,
  patchMySubscription,
  getSubscriberCount,
  getNewsletterHistory,
  previewNewsletter,
  sendNewsletter,
} from './newsletterService';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

import apiClient from './api/apiClient';

const mockGet = vi.mocked(apiClient.get);
const mockPost = vi.mocked(apiClient.post);
const mockPatch = vi.mocked(apiClient.patch);

describe('newsletterService', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('subscribe', () => {
    it('should POST to /newsletter/subscribe with request', async () => {
      mockPost.mockResolvedValue({ data: undefined });

      await subscribe({ email: 'alice@batbern.ch', firstName: 'Alice', language: 'de' });

      expect(mockPost).toHaveBeenCalledWith('/newsletter/subscribe', {
        email: 'alice@batbern.ch',
        firstName: 'Alice',
        language: 'de',
      });
    });

    it('should propagate 409 Conflict errors', async () => {
      mockPost.mockRejectedValue(new Error('Conflict'));

      await expect(subscribe({ email: 'dup@batbern.ch' })).rejects.toThrow('Conflict');
    });
  });

  describe('verifyUnsubscribeToken', () => {
    it('should GET verify endpoint with token', async () => {
      mockGet.mockResolvedValue({ data: { email: 'alice@batbern.ch' } });

      const result = await verifyUnsubscribeToken('tok-abc');

      expect(mockGet).toHaveBeenCalledWith('/newsletter/unsubscribe/verify?token=tok-abc');
      expect(result).toEqual({ email: 'alice@batbern.ch' });
    });

    it('should URL-encode special characters in token', async () => {
      mockGet.mockResolvedValue({ data: { email: 'test@x.ch' } });

      await verifyUnsubscribeToken('tok/with+special=chars');

      expect(mockGet).toHaveBeenCalledWith(
        '/newsletter/unsubscribe/verify?token=tok%2Fwith%2Bspecial%3Dchars'
      );
    });

    it('should propagate 404 for invalid tokens', async () => {
      mockGet.mockRejectedValue(new Error('Not Found'));

      await expect(verifyUnsubscribeToken('bad-token')).rejects.toThrow('Not Found');
    });
  });

  describe('unsubscribeByToken', () => {
    it('should POST to unsubscribe endpoint with token', async () => {
      mockPost.mockResolvedValue({ data: undefined });

      await unsubscribeByToken('unsubscribe-tok');

      expect(mockPost).toHaveBeenCalledWith('/newsletter/unsubscribe', {
        token: 'unsubscribe-tok',
      });
    });

    it('should propagate errors', async () => {
      mockPost.mockRejectedValue(new Error('Not Found'));

      await expect(unsubscribeByToken('bad')).rejects.toThrow('Not Found');
    });
  });

  describe('getMySubscription', () => {
    it('should GET current user subscription status', async () => {
      const status = { subscribed: true, email: 'alice@batbern.ch' };
      mockGet.mockResolvedValue({ data: status });

      const result = await getMySubscription();

      expect(mockGet).toHaveBeenCalledWith('/newsletter/my-subscription');
      expect(result).toEqual(status);
    });

    it('should return unsubscribed status', async () => {
      mockGet.mockResolvedValue({ data: { subscribed: false } });

      const result = await getMySubscription();

      expect(result.subscribed).toBe(false);
    });
  });

  describe('patchMySubscription', () => {
    it('should PATCH with subscribed and language', async () => {
      const updated = { subscribed: true, email: 'alice@batbern.ch' };
      mockPatch.mockResolvedValue({ data: updated });

      const result = await patchMySubscription(true, 'de');

      expect(mockPatch).toHaveBeenCalledWith('/newsletter/my-subscription', {
        subscribed: true,
        language: 'de',
      });
      expect(result).toEqual(updated);
    });

    it('should PATCH without language when not provided', async () => {
      mockPatch.mockResolvedValue({ data: { subscribed: false } });

      await patchMySubscription(false);

      expect(mockPatch).toHaveBeenCalledWith('/newsletter/my-subscription', {
        subscribed: false,
      });
    });
  });

  describe('getSubscriberCount', () => {
    it('should GET total active subscriber count', async () => {
      mockGet.mockResolvedValue({ data: { totalActive: 1250 } });

      const result = await getSubscriberCount();

      expect(mockGet).toHaveBeenCalledWith('/newsletter/subscribers/count');
      expect(result).toEqual({ totalActive: 1250 });
    });
  });

  describe('getNewsletterHistory', () => {
    it('should GET newsletter send history for event', async () => {
      const history = [{ id: 'h-1', isReminder: false, sentAt: '2025-01-01', recipientCount: 500 }];
      mockGet.mockResolvedValue({ data: history });

      const result = await getNewsletterHistory('BAT142');

      expect(mockGet).toHaveBeenCalledWith('/events/BAT142/newsletter/history');
      expect(result).toEqual(history);
    });
  });

  describe('previewNewsletter', () => {
    it('should POST preview request for event', async () => {
      const preview = {
        subject: 'BATbern Newsletter',
        htmlPreview: '<html/>',
        recipientCount: 300,
      };
      mockPost.mockResolvedValue({ data: preview });

      const result = await previewNewsletter('BAT142', { isReminder: false, locale: 'de' });

      expect(mockPost).toHaveBeenCalledWith('/events/BAT142/newsletter/preview', {
        isReminder: false,
        locale: 'de',
      });
      expect(result).toEqual(preview);
    });
  });

  describe('sendNewsletter', () => {
    it('should POST send request for event', async () => {
      const sent = { recipientCount: 300, sentAt: '2025-12-01T10:00:00Z' };
      mockPost.mockResolvedValue({ data: sent });

      const result = await sendNewsletter('BAT142', { isReminder: true, locale: 'en' });

      expect(mockPost).toHaveBeenCalledWith('/events/BAT142/newsletter/send', {
        isReminder: true,
        locale: 'en',
      });
      expect(result).toEqual(sent);
    });

    it('should propagate errors', async () => {
      mockPost.mockRejectedValue(new Error('Service unavailable'));

      await expect(sendNewsletter('BAT142', { isReminder: false, locale: 'de' })).rejects.toThrow(
        'Service unavailable'
      );
    });
  });
});
