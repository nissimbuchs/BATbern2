/**
 * Watch Pairing API Tests
 * Story W2.1
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import watchPairingApi from '../watchPairingApi';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

describe('watchPairingApi', () => {
  const mockPost = vi.mocked(apiClient.post);
  const mockGet = vi.mocked(apiClient.get);
  const mockDelete = vi.mocked(apiClient.delete);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('generatePairingCode', () => {
    it('should POST to the correct endpoint and return data', async () => {
      const mockResponse = {
        pairingCode: 'ABC123',
        expiresAt: '2026-01-01T00:00:00Z',
        hoursUntilExpiry: 24,
      };
      mockPost.mockResolvedValueOnce({ data: mockResponse });

      const result = await watchPairingApi.generatePairingCode('testuser');

      expect(mockPost).toHaveBeenCalledWith('/users/testuser/watch-pairing');
      expect(result).toEqual(mockResponse);
    });
  });

  describe('getPairingStatus', () => {
    it('should GET the correct endpoint and return data', async () => {
      const mockResponse = {
        pairedWatches: [
          { id: 'watch-42', deviceName: 'My Watch', pairedAt: '2026-01-01T00:00:00Z' },
        ],
        pendingCode: null,
      };
      mockGet.mockResolvedValueOnce({ data: mockResponse });

      const result = await watchPairingApi.getPairingStatus('testuser');

      expect(mockGet).toHaveBeenCalledWith('/users/testuser/watch-pairing');
      expect(result).toEqual(mockResponse);
    });
  });

  describe('unpairWatch', () => {
    it('should DELETE the correct endpoint and return undefined', async () => {
      mockDelete.mockResolvedValueOnce({ data: undefined });

      const result = await watchPairingApi.unpairWatch('testuser', 'watch-42');

      expect(mockDelete).toHaveBeenCalledWith('/users/testuser/watch-pairing/watch-42');
      expect(result).toBeUndefined();
    });
  });
});
