/**
 * useParticipantBatchImport Hook Integration Tests
 *
 * Tests sequential processing, rate limiting, error handling, and progress tracking
 * Addresses QA Issue TEST-001: Missing integration tests for hook
 */

import React, { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useParticipantBatchImport } from '../useParticipantBatchImport';
import * as eventApi from '../../../services/api/eventApi';
import { AxiosError } from 'axios';
import type {
  BatchRegistrationRequest,
  BatchRegistrationResponse,
} from '../../../types/participantImport.types';

// Mock the event API service
vi.mock('../../../services/api/eventApi');

describe('useParticipantBatchImport Hook', () => {
  let queryClient: QueryClient;

  // Create wrapper with QueryClient for hooks
  const createWrapper = () => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
        },
      },
    });

    return ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    queryClient?.clear();
    vi.restoreAllMocks();
  });

  describe('Sequential Processing', () => {
    it('should_processRequestsSequentially_when_multipleCandidatesProvided', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test1@example.com',
          firstName: 'Test',
          lastName: 'One',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
        {
          participantEmail: 'test2@example.com',
          firstName: 'Test',
          lastName: 'Two',
          registrations: [{ eventCode: 'BATbern2', status: 'ATTENDED' }],
        },
      ];

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.one',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const progressUpdates: Array<{ current: number; total: number }> = [];
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests, (current, total) => {
          progressUpdates.push({ current, total });
        });
      });

      // Wait for first request to complete
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      // Wait for rate limiting delay
      await act(async () => {
        await vi.advanceTimersByTimeAsync(100);
      });

      // Wait for second request to complete
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      const importResult = await importPromise;

      // Assert
      expect(eventApi.batchRegisterParticipant).toHaveBeenCalledTimes(2);
      expect(eventApi.batchRegisterParticipant).toHaveBeenNthCalledWith(1, mockRequests[0]);
      expect(eventApi.batchRegisterParticipant).toHaveBeenNthCalledWith(2, mockRequests[1]);
      expect(progressUpdates).toHaveLength(2);
      expect(progressUpdates[0]).toEqual({ current: 1, total: 2 });
      expect(progressUpdates[1]).toEqual({ current: 2, total: 2 });
      expect(importResult.total).toBe(2);
      expect(importResult.success).toBe(2);
      expect(importResult.failed).toBe(0);
    });

    it('should_updateCandidateStatus_when_processingEachRequest', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
      ];

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      // Initial state should be pending
      expect(result.current.candidates[0].importStatus).toBe('pending');

      // Wait for request to start
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      // Assert final state
      await importPromise;
      expect(result.current.candidates[0].importStatus).toBe('success');
    });
  });

  describe('Rate Limiting', () => {
    it('should_enforceRateLimit_when_processingMultipleRequests', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = Array.from({ length: 3 }, (_, i) => ({
        participantEmail: `test${i}@example.com`,
        firstName: 'Test',
        lastName: `User${i}`,
        registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
      }));

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const startTime = Date.now();
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      // Process all requests with rate limiting
      for (let i = 0; i < 3; i++) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0); // Request processing
        });
        if (i < 2) {
          await act(async () => {
            await vi.advanceTimersByTimeAsync(100); // Rate limit delay
          });
        }
      }

      await importPromise;
      const endTime = Date.now();

      // Assert - minimum time should be ~200ms (100ms delay between 3 requests)
      const minExpectedTime = 200;
      expect(endTime - startTime).toBeGreaterThanOrEqual(minExpectedTime);
    });

    it('should_delay100ms_when_processingBetweenRequests', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test1@example.com',
          firstName: 'Test',
          lastName: 'One',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
        {
          participantEmail: 'test2@example.com',
          firstName: 'Test',
          lastName: 'Two',
          registrations: [{ eventCode: 'BATbern2', status: 'ATTENDED' }],
        },
      ];

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      const batchRegisterMock = vi.mocked(eventApi.batchRegisterParticipant);
      const callTimes: number[] = [];
      batchRegisterMock.mockImplementation(async () => {
        callTimes.push(Date.now());
        return mockResponse;
      });

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(100);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      await importPromise;

      // Assert - second call should be at least 100ms after first
      expect(callTimes).toHaveLength(2);
      expect(callTimes[1] - callTimes[0]).toBeGreaterThanOrEqual(100);
    });
  });

  describe('Error Handling', () => {
    it('should_handleAxiosError_when_requestFails', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
      ];

      const axiosError = new AxiosError(
        'Request failed with status code 400',
        'ERR_BAD_REQUEST',
        undefined,
        undefined,
        {
          data: { message: 'Invalid email format' },
          status: 400,
          statusText: 'Bad Request',
          headers: {},
          config: {} as any,
        }
      );

      vi.mocked(eventApi.batchRegisterParticipant).mockRejectedValue(axiosError);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      const importResult = await importPromise;

      // Assert
      expect(importResult.success).toBe(0);
      expect(importResult.failed).toBe(1);
      expect(result.current.candidates[0].importStatus).toBe('error');
      expect(result.current.candidates[0].errorMessage).toBe('Invalid email format');
    });

    it('should_handleNetworkError_when_noResponseReceived', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
      ];

      const networkError = new AxiosError(
        'Network Error',
        'ERR_NETWORK',
        undefined,
        { data: {} } as any,
        undefined
      );

      vi.mocked(eventApi.batchRegisterParticipant).mockRejectedValue(networkError);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      const importResult = await importPromise;

      // Assert
      expect(importResult.failed).toBe(1);
      expect(result.current.candidates[0].errorMessage).toBe('No response from server');
    });

    it('should_continueProcessing_when_oneRequestFails', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test1@example.com',
          firstName: 'Test',
          lastName: 'One',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
        {
          participantEmail: 'test2@example.com',
          firstName: 'Test',
          lastName: 'Two',
          registrations: [{ eventCode: 'BATbern2', status: 'ATTENDED' }],
        },
      ];

      const successResponse: BatchRegistrationResponse = {
        username: 'test.two',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant)
        .mockRejectedValueOnce(new Error('Server error'))
        .mockResolvedValueOnce(successResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(100);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      const importResult = await importPromise;

      // Assert
      expect(importResult.total).toBe(2);
      expect(importResult.success).toBe(1);
      expect(importResult.failed).toBe(1);
      expect(result.current.candidates[0].importStatus).toBe('error');
      expect(result.current.candidates[1].importStatus).toBe('success');
    });
  });

  describe('Partial Success Handling', () => {
    it('should_handlePartialSuccess_when_someRegistrationsFail', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
          registrations: [
            { eventCode: 'BATbern1', status: 'ATTENDED' },
            { eventCode: 'BATbern2', status: 'ATTENDED' },
          ],
        },
      ];

      const partialSuccessResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 2,
        successfulRegistrations: 1,
        failedRegistrations: [
          {
            eventCode: 'BATbern2',
            error: 'Event not found',
          },
        ],
        errors: [],
      };

      vi.mocked(axios.post).mockResolvedValue({ data: partialSuccessResponse });

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      const importResult = await importPromise;

      // Assert
      expect(importResult.success).toBe(1);
      expect(result.current.candidates[0].importStatus).toBe('success');
      expect(result.current.candidates[0].errorMessage).toBe('1/2 registrations succeeded');
    });
  });

  describe('Progress Tracking', () => {
    it('should_invokeProgressCallback_when_eachRequestCompletes', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = Array.from({ length: 3 }, (_, i) => ({
        participantEmail: `test${i}@example.com`,
        firstName: 'Test',
        lastName: `User${i}`,
        registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
      }));

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      const progressCallback = vi.fn();

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests, progressCallback);
      });

      for (let i = 0; i < 3; i++) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });
        if (i < 2) {
          await act(async () => {
            await vi.advanceTimersByTimeAsync(100);
          });
        }
      }

      await importPromise;

      // Assert
      expect(progressCallback).toHaveBeenCalledTimes(3);
      expect(progressCallback).toHaveBeenNthCalledWith(1, 1, 3);
      expect(progressCallback).toHaveBeenNthCalledWith(2, 2, 3);
      expect(progressCallback).toHaveBeenNthCalledWith(3, 3, 3);
    });

    it('should_updateIsImportingState_when_importStarts', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
      ];

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Assert initial state
      expect(result.current.isImporting).toBe(false);

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      // Assert importing state
      expect(result.current.isImporting).toBe(true);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      await importPromise;

      // Assert final state
      expect(result.current.isImporting).toBe(false);
    });
  });

  describe('Cache Invalidation', () => {
    it('should_invalidateCaches_when_importCompletes', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
      ];

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      await importPromise;

      // Assert
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['registrations'] });
    });
  });

  describe('Synthetic Email Detection', () => {
    it('should_detectSyntheticEmail_when_emailEndsWithBatbernCh', async () => {
      // Arrange
      const mockRequests: BatchRegistrationRequest[] = [
        {
          participantEmail: 'firstname.lastname@batbern.ch',
          firstName: 'FirstName',
          lastName: 'LastName',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
        {
          participantEmail: 'real@example.com',
          firstName: 'Real',
          lastName: 'User',
          registrations: [{ eventCode: 'BATbern1', status: 'ATTENDED' }],
        },
      ];

      const mockResponse: BatchRegistrationResponse = {
        username: 'test.user',
        totalRegistrations: 1,
        successfulRegistrations: 1,
        failedRegistrations: [],
        errors: [],
      };

      vi.mocked(eventApi.batchRegisterParticipant).mockResolvedValue(mockResponse);

      const { result } = renderHook(() => useParticipantBatchImport(), {
        wrapper: createWrapper(),
      });

      // Act
      const importPromise = act(async () => {
        return result.current.importCandidates(mockRequests);
      });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(100);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      await importPromise;

      // Assert
      expect(result.current.candidates[0].isSyntheticEmail).toBe(true);
      expect(result.current.candidates[1].isSyntheticEmail).toBe(false);
    });
  });
});
