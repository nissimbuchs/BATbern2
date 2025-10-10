/**
 * Auth API Tests
 * Story 1.17: Tests for type-safe auth API wrappers
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { logout } from './authApi';
import { LogoutResponse } from './authApi';

describe('Auth API', () => {
  let mockAxios: MockAdapter;

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mockAxios.restore();
  });

  describe('logout', () => {
    it('should_logoutUser_when_validRefreshTokenProvided', async () => {
      const mockResponse: LogoutResponse = {
        success: true,
      };

      mockAxios
        .onPost('/api/v1/auth/logout', { refreshToken: 'refresh-token-123' })
        .reply((config) => {
          const data = JSON.parse(config.data);
          expect(data.refreshToken).toBe('refresh-token-123');
          return [200, mockResponse];
        });

      const result = await logout('refresh-token-123');

      expect(result).toEqual(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should_throwError_when_invalidRefreshTokenProvided', async () => {
      mockAxios.onPost('/api/v1/auth/logout').reply(400, {
        message: 'Invalid refresh token',
      });

      await expect(logout('invalid-token')).rejects.toThrow();
    });

    it('should_throwError_when_unauthorized', async () => {
      mockAxios.onPost('/api/v1/auth/logout').reply(401, {
        message: 'Unauthorized',
      });

      await expect(logout('refresh-token-123')).rejects.toThrow();
    });

    it('should_throwError_when_serverError', async () => {
      mockAxios.onPost('/api/v1/auth/logout').reply(500, {
        message: 'Internal server error',
      });

      await expect(logout('refresh-token-123')).rejects.toThrow();
    });
  });
});
