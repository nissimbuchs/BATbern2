/**
 * useEventRegistrations Hook Tests
 *
 * TDD Tests for React Query hook fetching event registrations
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React, { ReactNode } from 'react';
import { useEventRegistrations } from './useEventRegistrations';
import * as eventRegistrationService from '@/services/api/eventRegistrationService';
import type { EventParticipant, RegistrationStatus } from '@/types/eventParticipant.types';

// Mock the service
vi.mock('@/services/api/eventRegistrationService');

describe('useEventRegistrations', () => {
  let queryClient: QueryClient;

  const mockRegistrations: EventParticipant[] = [
    {
      registrationCode: 'REG-001',
      eventCode: 'BAT-2024-01',
      attendeeUsername: 'john.doe',
      firstName: 'John',
      lastName: 'Doe',
      email: 'john.doe@example.com',
      company: {
        id: 'centris-ag',
        name: 'Centris AG',
      },
      status: 'CONFIRMED' as RegistrationStatus,
      registrationDate: '2024-01-15T10:30:00Z',
    },
    {
      registrationCode: 'REG-002',
      eventCode: 'BAT-2024-01',
      attendeeUsername: 'jane.smith',
      firstName: 'Jane',
      lastName: 'Smith',
      email: 'jane.smith@example.com',
      status: 'REGISTERED' as RegistrationStatus,
      registrationDate: '2024-01-16T14:20:00Z',
    },
  ];

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          gcTime: 0,
          staleTime: 0,
        },
      },
    });

    vi.clearAllMocks();
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('Basic Fetching', () => {
    it('should fetch registrations for an event', async () => {
      const mockResponse = {
        registrations: mockRegistrations,
        total: 2,
      };

      vi.spyOn(eventRegistrationService, 'getEventRegistrations').mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
          }),
        { wrapper }
      );

      expect(result.current.isLoading).toBe(true);

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockResponse);
      expect(result.current.data?.registrations).toHaveLength(2);
      expect(result.current.data?.total).toBe(2);
    });

    it('should pass filters to service', async () => {
      const mockResponse = {
        registrations: [mockRegistrations[0]],
        total: 1,
      };

      const getEventRegistrationsSpy = vi
        .spyOn(eventRegistrationService, 'getEventRegistrations')
        .mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
            filters: { status: ['CONFIRMED'] },
          }),
        { wrapper }
      );

      await waitFor(() => {
        expect(getEventRegistrationsSpy).toHaveBeenCalledWith('BAT-2024-01', {
          filters: { status: ['CONFIRMED'] },
        });
      });
    });

    it('should pass pagination to service', async () => {
      const mockResponse = {
        registrations: [mockRegistrations[0]],
        total: 10,
      };

      const getEventRegistrationsSpy = vi
        .spyOn(eventRegistrationService, 'getEventRegistrations')
        .mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
            pagination: { page: 2, limit: 1 },
          }),
        { wrapper }
      );

      await waitFor(() => {
        expect(getEventRegistrationsSpy).toHaveBeenCalledWith('BAT-2024-01', {
          pagination: { page: 2, limit: 1 },
        });
      });
    });

    it('should pass search to service', async () => {
      const mockResponse = {
        registrations: [mockRegistrations[0]],
        total: 1,
      };

      const getEventRegistrationsSpy = vi
        .spyOn(eventRegistrationService, 'getEventRegistrations')
        .mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
            search: 'john',
          }),
        { wrapper }
      );

      await waitFor(() => {
        expect(getEventRegistrationsSpy).toHaveBeenCalledWith('BAT-2024-01', {
          search: 'john',
        });
      });
    });

    it('should pass all options combined to service', async () => {
      const mockResponse = {
        registrations: [mockRegistrations[0]],
        total: 1,
      };

      const getEventRegistrationsSpy = vi
        .spyOn(eventRegistrationService, 'getEventRegistrations')
        .mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
            filters: { status: ['CONFIRMED'], companyId: 'centris-ag' },
            pagination: { page: 1, limit: 25 },
            search: 'john',
          }),
        { wrapper }
      );

      await waitFor(() => {
        expect(getEventRegistrationsSpy).toHaveBeenCalledWith('BAT-2024-01', {
          filters: { status: ['CONFIRMED'], companyId: 'centris-ag' },
          pagination: { page: 1, limit: 25 },
          search: 'john',
        });
      });
    });
  });

  describe('Enabled Flag', () => {
    it('should not fetch when enabled is false', async () => {
      const getEventRegistrationsSpy = vi.spyOn(eventRegistrationService, 'getEventRegistrations');

      const { result } = renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
            enabled: false,
          }),
        { wrapper }
      );

      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(result.current.isLoading).toBe(false);
      expect(result.current.isFetching).toBe(false);
      expect(getEventRegistrationsSpy).not.toHaveBeenCalled();
    });

    it('should fetch when enabled is true', async () => {
      const mockResponse = {
        registrations: mockRegistrations,
        total: 2,
      };

      const getEventRegistrationsSpy = vi
        .spyOn(eventRegistrationService, 'getEventRegistrations')
        .mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
            enabled: true,
          }),
        { wrapper }
      );

      await waitFor(() => {
        expect(getEventRegistrationsSpy).toHaveBeenCalled();
      });
    });
  });

  describe('Error Handling', () => {
    it('should handle API errors', async () => {
      const error = new Error('Failed to fetch registrations');

      vi.spyOn(eventRegistrationService, 'getEventRegistrations').mockRejectedValue(error);

      const { result } = renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
          }),
        { wrapper }
      );

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(error);
    });
  });

  describe('Refetch', () => {
    it('should provide refetch function', async () => {
      const mockResponse = {
        registrations: mockRegistrations,
        total: 2,
      };

      vi.spyOn(eventRegistrationService, 'getEventRegistrations').mockResolvedValue(mockResponse);

      const { result } = renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
          }),
        { wrapper }
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.refetch).toBeDefined();
      expect(typeof result.current.refetch).toBe('function');
    });
  });

  describe('Query Key Generation', () => {
    it('should use correct query key with only eventCode', async () => {
      const mockResponse = {
        registrations: mockRegistrations,
        total: 2,
      };

      vi.spyOn(eventRegistrationService, 'getEventRegistrations').mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
          }),
        { wrapper }
      );

      await waitFor(() => {
        const cache = queryClient.getQueryCache();
        const queries = cache.findAll({
          queryKey: ['event-registrations', 'BAT-2024-01'],
        });
        expect(queries.length).toBeGreaterThan(0);
      });
    });

    it('should include filters and pagination in query key', async () => {
      const mockResponse = {
        registrations: [mockRegistrations[0]],
        total: 1,
      };

      vi.spyOn(eventRegistrationService, 'getEventRegistrations').mockResolvedValue(mockResponse);

      renderHook(
        () =>
          useEventRegistrations({
            eventCode: 'BAT-2024-01',
            filters: { status: ['CONFIRMED'] },
            pagination: { page: 2, limit: 25 },
          }),
        { wrapper }
      );

      await waitFor(() => {
        const cache = queryClient.getQueryCache();
        const queries = cache.findAll({
          queryKey: [
            'event-registrations',
            'BAT-2024-01',
            { filters: { status: ['CONFIRMED'] }, pagination: { page: 2, limit: 25 } },
          ],
        });
        expect(queries.length).toBeGreaterThan(0);
      });
    });
  });
});
