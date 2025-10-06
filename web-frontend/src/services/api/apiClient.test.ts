/**
 * API Client Tests
 * Story 1.2.1: QA Fix - API Client Interceptor Coverage
 *
 * Tests for axios interceptors:
 * - Accept-Language header injection
 * - Authorization token retrieval from storage
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

interface I18nMock {
  language: string;
}

describe('API Client', () => {
  let mockAxios: MockAdapter;

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient);
    // Clear storage before each test
    localStorage.clear();
    sessionStorage.clear();
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

    it('should_addAuthorizationHeader_when_tokenInSessionStorage', async () => {
      sessionStorage.setItem('accessToken', 'session-token-123');

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Authorization']).toBe('Bearer session-token-123');
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });

    it('should_addAuthorizationHeader_when_tokenInLocalStorage', async () => {
      localStorage.setItem('accessToken', 'local-token-456');

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Authorization']).toBe('Bearer local-token-456');
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });

    it('should_preferSessionStorage_when_tokenInBothStorages', async () => {
      sessionStorage.setItem('accessToken', 'session-token');
      localStorage.setItem('accessToken', 'local-token');

      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Authorization']).toBe('Bearer session-token');
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });

    it('should_notAddAuthorizationHeader_when_noToken', async () => {
      mockAxios.onGet('/test').reply((config) => {
        expect(config.headers?.['Authorization']).toBeUndefined();
        return [200, { success: true }];
      });

      await apiClient.get('/test');
    });
  });

  describe('Response Interceptor - Error Handling', () => {
    it('should_handleUnauthorizedError_when_401Received', async () => {
      // Mock window.location.href
      const originalLocation = window.location;
      delete (window as { location?: Location }).location;
      window.location = { ...originalLocation, href: '' } as Location;

      mockAxios.onGet('/protected').reply(401, { message: 'Unauthorized' });

      try {
        await apiClient.get('/protected');
      } catch (error) {
        expect(window.location.href).toBe('/login');
        expect((error as AxiosError).response?.status).toBe(401);
      }

      // Restore
      window.location = originalLocation;
    });

    it('should_handleForbiddenError_when_403Received', async () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      mockAxios.onGet('/admin').reply(403, { message: 'Forbidden' });

      try {
        await apiClient.get('/admin');
      } catch (error) {
        expect(consoleErrorSpy).toHaveBeenCalledWith('Forbidden: Insufficient permissions');
        expect((error as AxiosError).response?.status).toBe(403);
      }

      consoleErrorSpy.mockRestore();
    });

    it('should_handleServerError_when_500Received', async () => {
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      mockAxios.onGet('/data').reply(500, { message: 'Internal Server Error' });

      try {
        await apiClient.get('/data');
      } catch (error) {
        expect(consoleErrorSpy).toHaveBeenCalledWith('Server error occurred');
        expect((error as AxiosError).response?.status).toBe(500);
      }

      consoleErrorSpy.mockRestore();
    });

    it('should_passThrough2xxResponses_when_successful', async () => {
      mockAxios.onGet('/data').reply(200, { data: 'test' });

      const response = await apiClient.get('/data');

      expect(response.status).toBe(200);
      expect(response.data).toEqual({ data: 'test' });
    });

    it('should_rejectWithError_when_networkErrorOccurs', async () => {
      mockAxios.onGet('/data').networkError();

      try {
        await apiClient.get('/data');
        expect.fail('Should have thrown an error');
      } catch (error) {
        expect(error).toBeDefined();
        expect((error as AxiosError).message).toContain('Network Error');
      }
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
