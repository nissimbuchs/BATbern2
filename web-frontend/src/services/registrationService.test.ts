/**
 * Registration Service Tests (Story 10.10)
 *
 * Tests for getMyRegistration:
 * - Returns registration data on success
 * - Returns null on 404 (not registered)
 * - Rethrows on other errors
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getMyRegistration } from './registrationService';
import apiClient from './api/apiClient';

vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}));

const mockApiClient = vi.mocked(apiClient);

describe('registrationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getMyRegistration', () => {
    it('should return registration data on success', async () => {
      const mockReg = { registrationCode: 'ABC123', status: 'CONFIRMED', eventCode: 'BATbern142' };
      mockApiClient.get.mockResolvedValue({ data: mockReg });

      const result = await getMyRegistration('BATbern142');

      expect(mockApiClient.get).toHaveBeenCalledWith('/events/BATbern142/my-registration');
      expect(result).toEqual(mockReg);
    });

    it('should return null when user is not registered (404)', async () => {
      const notFoundError = { response: { status: 404 } };
      mockApiClient.get.mockRejectedValue(notFoundError);

      const result = await getMyRegistration('BATbern142');

      expect(result).toBeNull();
    });

    it('should rethrow non-404 errors', async () => {
      const serverError = { response: { status: 500 } };
      mockApiClient.get.mockRejectedValue(serverError);

      await expect(getMyRegistration('BATbern142')).rejects.toEqual(serverError);
    });

    it('should rethrow errors without response', async () => {
      const networkError = new Error('Network failure');
      mockApiClient.get.mockRejectedValue(networkError);

      await expect(getMyRegistration('BATbern142')).rejects.toThrow('Network failure');
    });
  });
});
