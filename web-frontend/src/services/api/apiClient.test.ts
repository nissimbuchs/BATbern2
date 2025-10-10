/**
 * API Client Tests
 * Story 1.2.1: QA Fix - API Client Interceptor Coverage
 * Story 1.17 QA Fix: SEC-001 (secure token storage), SEC-002 (correlation IDs)
 *
 * Tests for axios interceptors:
 * - Accept-Language header injection
 * - Authorization token retrieval from AWS Amplify
 * - X-Correlation-ID header generation
 * - Error response handling (401/403/500)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AxiosError } from 'axios';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import i18n from '@/i18n/config';

// Mock i18n
vi.mock('@/i18n/config', () => ({
  default: {
    language: 'de',
  },
}));

// Mock AWS Amplify auth
vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: vi.fn(),
}));

import { fetchAuthSession } from 'aws-amplify/auth';

interface I18nMock {
  language: string;
}

describe('API Client', () => {
  let mockAxios: MockAdapter;

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient);
    // Clear mock calls
    vi.clearAllMocks();
  });

  afterEach(() => {
    mockAxios.restore();
  });

  describe('Request Interceptor', () => {
    it('should_addAcceptLanguageHeader_when_requestMade', async () => {
      // Mock i18n language
      (i18n as I18nMock).language = 'de';

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Accept-Language']).toBe('de');
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });

    it('should_changeAcceptLanguageHeader_when_languageChanged', async () => {
      // Test with English
      (i18n as I18nMock).language = 'en';

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Accept-Language']).toBe('en');
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });

    it('should_addAuthorizationHeader_when_amplifySessionHasToken', async () => {
      // Mock AWS Amplify fetchAuthSession to return a token
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: {
          accessToken: {
            toString: () => 'amplify-token-123',
          },
        },
      } as any);

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Authorization']).toBe('Bearer amplify-token-123');
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });

    it('should_addCorrelationIdHeader_when_requestMade', async () => {
      // Mock AWS Amplify - no token
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: undefined,
      } as any);

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['X-Correlation-ID']).toBeDefined();
        expect(typeof config.headers?.['X-Correlation-ID']).toBe('string');
        // Should be a valid UUID format
        expect(config.headers?.['X-Correlation-ID']).toMatch(
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
        );
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });

    it('should_notAddAuthorizationHeader_when_noToken', async () => {
      // Mock AWS Amplify - no tokens available
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: undefined,
      } as any);

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Authorization']).toBeUndefined();
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });
  });

  describe('Response Interceptor - Error Handling', () => {
    it('should_handleUnauthorizedError_when_401Received', async () => {
      // Mock AWS Amplify
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: undefined,
      } as any);

      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      mockAxios.onGet('/protected').reply(401, { message: 'Unauthorized' });

      try {
        await apiClient.get('/protected');
      } catch (error) {
        // Should log with correlation ID
        expect(consoleErrorSpy).toHaveBeenCalled();
        const callArgs = consoleErrorSpy.mock.calls[0][0];
        expect(callArgs).toMatch(/\[.*\] Unauthorized - session expired/);
        expect((error as AxiosError).response?.status).toBe(401);
      }

      consoleErrorSpy.mockRestore();
    });

    it('should_handleForbiddenError_when_403Received', async () => {
      // Mock AWS Amplify
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: undefined,
      } as any);

      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      mockAxios.onGet('/admin').reply(403, { message: 'Forbidden' });

      try {
        await apiClient.get('/admin');
      } catch (error) {
        // Should log with correlation ID
        expect(consoleErrorSpy).toHaveBeenCalled();
        const callArgs = consoleErrorSpy.mock.calls[0][0];
        expect(callArgs).toMatch(/\[.*\] Forbidden: Insufficient permissions/);
        expect((error as AxiosError).response?.status).toBe(403);
      }

      consoleErrorSpy.mockRestore();
    });

    it('should_handleServerError_when_500Received', async () => {
      // Mock AWS Amplify
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: undefined,
      } as any);

      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      mockAxios.onGet('/data').reply(500, { message: 'Internal Server Error' });

      try {
        await apiClient.get('/data');
      } catch (error) {
        // Should log with correlation ID
        expect(consoleErrorSpy).toHaveBeenCalled();
        const callArgs = consoleErrorSpy.mock.calls[0][0];
        expect(callArgs).toMatch(/\[.*\] Server error occurred/);
        expect((error as AxiosError).response?.status).toBe(500);
      }

      consoleErrorSpy.mockRestore();
    });

    it('should_passThrough2xxResponses_when_successful', async () => {
      // Mock AWS Amplify
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: undefined,
      } as any);

      mockAxios.onGet('/data').reply(200, { data: 'test' });

      const response = await apiClient.get('/data');

      expect(response.status).toBe(200);
      expect(response.data).toEqual({ data: 'test' });
    });

    it('should_rejectWithError_when_networkErrorOccurs', async () => {
      // Mock AWS Amplify
      vi.mocked(fetchAuthSession).mockResolvedValue({
        tokens: undefined,
      } as any);

      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      mockAxios.onGet('/data').networkError();

      try {
        await apiClient.get('/data');
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error).toBeDefined();
        expect((error as AxiosError).message).toContain('Network Error');
        // Should log network error with correlation ID
        expect(consoleErrorSpy).toHaveBeenCalled();
      }

      consoleErrorSpy.mockRestore();
    });
  });

  describe('Base Configuration', () => {
    it('should_haveCorrectBaseURL_when_configured', () => {
      expect(apiClient.defaults.baseURL).toBeDefined();
    });

    it('should_haveCorrectTimeout_when_configured', () => {
      expect(apiClient.defaults.timeout).toBe(30000);
    });

    it('should_haveCorrectContentType_when_configured', () => {
      expect(apiClient.defaults.headers['Content-Type']).toBe('application/json');
    });
  });
});
