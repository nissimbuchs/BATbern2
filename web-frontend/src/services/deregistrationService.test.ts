/**
 * Deregistration Service Tests (Story 10.12)
 *
 * Tests for verifyDeregistrationToken, deregisterByToken, deregisterByEmail.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  verifyDeregistrationToken,
  deregisterByToken,
  deregisterByEmail,
} from './deregistrationService';
import type { DeregistrationVerifyResponse } from './deregistrationService';
import apiClient from './api/apiClient';

vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

describe('deregistrationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('verifyDeregistrationToken', () => {
    it('should return registration details for a valid token', async () => {
      const mockResponse: DeregistrationVerifyResponse = {
        registrationCode: 'REG-001',
        eventCode: 'BATbern142',
        eventTitle: 'BATbern #142',
        eventDate: '2026-04-15T18:00:00Z',
        attendeeFirstName: 'Alice',
      };
      mockApiClient.get.mockResolvedValue({ data: mockResponse });

      const result = await verifyDeregistrationToken('valid-token');

      expect(mockApiClient.get).toHaveBeenCalledWith(
        '/registrations/deregister/verify?token=valid-token'
      );
      expect(result.attendeeFirstName).toBe('Alice');
    });

    it('should propagate errors (e.g. 404 for invalid token)', async () => {
      mockApiClient.get.mockRejectedValue({ response: { status: 404 } });

      await expect(verifyDeregistrationToken('bad-token')).rejects.toEqual({
        response: { status: 404 },
      });
    });

    it('should URL-encode the token', async () => {
      mockApiClient.get.mockResolvedValue({ data: {} });

      await verifyDeregistrationToken('token with spaces');

      expect(mockApiClient.get).toHaveBeenCalledWith(
        '/registrations/deregister/verify?token=token%20with%20spaces'
      );
    });
  });

  describe('deregisterByToken', () => {
    it('should POST to deregister endpoint with token', async () => {
      mockApiClient.post.mockResolvedValue({ data: undefined });

      await deregisterByToken('my-token');

      expect(mockApiClient.post).toHaveBeenCalledWith('/registrations/deregister', {
        token: 'my-token',
      });
    });

    it('should propagate errors (409 already cancelled)', async () => {
      mockApiClient.post.mockRejectedValue({ response: { status: 409 } });

      await expect(deregisterByToken('my-token')).rejects.toEqual({
        response: { status: 409 },
      });
    });
  });

  describe('deregisterByEmail', () => {
    it('should POST email deregistration request', async () => {
      mockApiClient.post.mockResolvedValue({ data: undefined });

      await deregisterByEmail('user@example.com', 'BATbern142');

      expect(mockApiClient.post).toHaveBeenCalledWith('/registrations/deregister/by-email', {
        email: 'user@example.com',
        eventCode: 'BATbern142',
      });
    });
  });
});
