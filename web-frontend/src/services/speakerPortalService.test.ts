/**
 * Story 11.F.1 RD5: Cognito-side replacement for the legacy magic-link test suite.
 *
 * The old test file asserted magic-link `?token=` parameter handling + `Skip-Auth`
 * header behaviour. After Phase E (Story 11.E.3) the service was rewritten:
 *   - every per-event method takes `eventCode` first (path parameter)
 *   - the magic-link `token` field is gone from every request body
 *   - the `Skip-Auth` header is gone (the axios interceptor attaches the Cognito Bearer)
 *
 * These tests verify the post-E.3 contract: correct URL shape, no token query
 * parameter, clean error surfacing for 401/403/404/409 from the backend.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';
import { speakerPortalService } from './speakerPortalService';

const mockedGet = vi.mocked(apiClient.get);
const mockedPost = vi.mocked(apiClient.post);

describe('speakerPortalService — Cognito contract (Story 11.E.3 + 11.F.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('respond()', () => {
    it('should_postWithEventCodeInPath_when_submittingAcceptResponse', async () => {
      mockedPost.mockResolvedValueOnce({
        data: {
          success: true,
          speakerName: 'Test Speaker',
          eventName: 'BATbern99',
          nextSteps: [],
        },
      } as never);

      await speakerPortalService.respond('BATbern99', { response: 'ACCEPT' });

      expect(mockedPost).toHaveBeenCalledTimes(1);
      const [url, body] = mockedPost.mock.calls[0]!;
      expect(url).toBe('/speaker-portal/events/BATbern99/respond');
      expect(body).toEqual({ response: 'ACCEPT' });
      // Story 11.F.1: no magic-link token field in the request body.
      expect(body).not.toHaveProperty('token');
    });

    it('should_urlEncodeEventCode_when_eventCodeHasReservedChars', async () => {
      mockedPost.mockResolvedValueOnce({ data: { success: true } } as never);

      await speakerPortalService.respond('BAT/99', { response: 'DECLINE', reason: 'busy' });

      const [url] = mockedPost.mock.calls[0]!;
      expect(url).toBe('/speaker-portal/events/BAT%2F99/respond');
    });

    it('should_surfaceBackend409_when_alreadyResponded', async () => {
      const axiosError = {
        isAxiosError: true,
        response: {
          status: 409,
          data: {
            errorCode: 'ALREADY_RESPONDED',
            message: 'Already responded',
            previousResponse: 'ACCEPTED',
            respondedAt: '2026-05-20T10:00:00Z',
          },
          headers: {},
        },
      };
      mockedPost.mockRejectedValueOnce(axiosError);

      await expect(
        speakerPortalService.respond('BATbern99', { response: 'ACCEPT' })
      ).rejects.toMatchObject({
        status: 409,
        errorCode: 'ALREADY_RESPONDED',
        previousResponse: 'ACCEPTED',
      });
    });

    it('should_surfaceBackend403_when_speakerHasNoAccessToEvent', async () => {
      mockedPost.mockRejectedValueOnce({
        isAxiosError: true,
        response: {
          status: 403,
          data: { errorCode: 'FORBIDDEN', message: 'Forbidden' },
          headers: {},
        },
      });

      await expect(
        speakerPortalService.respond('OtherEvent', { response: 'ACCEPT' })
      ).rejects.toMatchObject({ status: 403, errorCode: 'FORBIDDEN' });
    });

    it('should_returnNetworkError_when_axiosErrorHasNoResponse', async () => {
      mockedPost.mockRejectedValueOnce({ isAxiosError: true });

      await expect(
        speakerPortalService.respond('BATbern99', { response: 'ACCEPT' })
      ).rejects.toThrow(/Network Error/);
    });
  });

  describe('getDashboard()', () => {
    it('should_getFromDashboardEndpoint_when_calledWithoutArgs', async () => {
      mockedGet.mockResolvedValueOnce({
        data: { speakerName: 'X', profileCompleteness: 100, upcomingEvents: [], pastEvents: [] },
      } as never);

      await speakerPortalService.getDashboard();

      expect(mockedGet).toHaveBeenCalledWith('/speaker-portal/dashboard');
    });

    it('should_surfaceBackend401_when_authenticationMissing', async () => {
      mockedGet.mockRejectedValueOnce({
        isAxiosError: true,
        response: { status: 401, data: { message: 'Unauthorized' }, headers: {} },
      });

      await expect(speakerPortalService.getDashboard()).rejects.toMatchObject({ status: 401 });
    });
  });

  describe('getContentInfo() / submitContent()', () => {
    it('should_getContentInfoFromEventScopedPath_when_called', async () => {
      mockedGet.mockResolvedValueOnce({ data: {} } as never);

      await speakerPortalService.getContentInfo('BATbern99');

      expect(mockedGet).toHaveBeenCalledWith('/speaker-portal/events/BATbern99/content');
    });

    it('should_postToContentSubmitEndpoint_when_submittingContent', async () => {
      mockedPost.mockResolvedValueOnce({
        data: { submissionId: 's-1', version: 1, status: 'submitted', sessionTitle: 'T' },
      } as never);

      await speakerPortalService.submitContent('BATbern99', {
        title: 'My Talk',
        contentAbstract: 'Abstract',
      });

      const [url, body] = mockedPost.mock.calls[0]!;
      expect(url).toBe('/speaker-portal/events/BATbern99/content/submit');
      expect(body).toMatchObject({ title: 'My Talk', contentAbstract: 'Abstract' });
      expect(body).not.toHaveProperty('token');
    });

    it('should_surfaceBackend404_when_eventNotFound', async () => {
      mockedGet.mockRejectedValueOnce({
        isAxiosError: true,
        response: { status: 404, data: { message: 'Event not found' }, headers: {} },
      });

      await expect(speakerPortalService.getContentInfo('Unknown')).rejects.toMatchObject({
        status: 404,
      });
    });
  });

  describe('correlation ID propagation', () => {
    it('should_appendCorrelationIdToErrorMessage_when_headerPresent', async () => {
      mockedPost.mockRejectedValueOnce({
        isAxiosError: true,
        response: {
          status: 500,
          data: { message: 'Server error' },
          headers: { 'x-correlation-id': 'abc-123' },
        },
      });

      await expect(
        speakerPortalService.respond('BATbern99', { response: 'ACCEPT' })
      ).rejects.toThrow(/Server error.*ID: abc-123/);
    });
  });
});
