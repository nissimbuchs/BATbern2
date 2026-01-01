/**
 * Event Registration Service Tests
 *
 * TDD Tests for API service fetching event registrations
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './apiClient';
import { getEventRegistrations } from './eventRegistrationService';
import type { EventParticipant, RegistrationStatus } from '@/types/eventParticipant.types';

describe('eventRegistrationService', () => {
  let mockAxios: MockAdapter;

  beforeEach(() => {
    mockAxios = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mockAxios.reset();
    mockAxios.restore();
    vi.clearAllMocks();
  });

  describe('getEventRegistrations', () => {
    const eventCode = 'BAT-2024-01';

    // Mock backend response format (different from frontend types)
    const mockBackendRegistrations = [
      {
        registrationCode: 'REG-001',
        eventCode: 'BAT-2024-01',
        attendeeUsername: 'john.doe',
        attendeeFirstName: 'John',
        attendeeLastName: 'Doe',
        attendeeEmail: 'john.doe@example.com',
        attendeeCompany: 'centris-ag',
        status: 'CONFIRMED',
        registrationDate: '2024-01-15T10:30:00Z',
      },
      {
        registrationCode: 'REG-002',
        eventCode: 'BAT-2024-01',
        attendeeUsername: 'jane.smith',
        attendeeFirstName: 'Jane',
        attendeeLastName: 'Smith',
        attendeeEmail: 'jane.smith@example.com',
        status: 'REGISTERED',
        registrationDate: '2024-01-16T14:20:00Z',
      },
    ];

    // Expected frontend format after transformation
    const expectedRegistrations: EventParticipant[] = [
      {
        registrationCode: 'REG-001',
        eventCode: 'BAT-2024-01',
        attendeeUsername: 'john.doe',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john.doe@example.com',
        company: {
          id: 'centris-ag',
          name: 'centris-ag',
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

    it('should fetch registrations and transform backend format to frontend format', async () => {
      // Backend returns paginated response: { data: [...], pagination: {...} }
      const backendResponse = {
        data: mockBackendRegistrations,
        pagination: {
          page: 1,
          limit: 25,
          totalItems: 2,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mockAxios.onGet(`/events/${eventCode}/registrations`).reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode);

      // Should transform to frontend format with firstName, lastName, etc.
      expect(result.data).toHaveLength(2);
      expect(result.pagination.totalItems).toBe(2);
      expect(result.pagination.totalPages).toBe(1);
      expect(result.data[0]).toEqual(expectedRegistrations[0]);
      expect(result.data[0].firstName).toBe('John'); // Transformed from attendeeFirstName
      expect(result.data[0].lastName).toBe('Doe'); // Transformed from attendeeLastName
      expect(result.data[0].email).toBe('john.doe@example.com'); // Transformed from attendeeEmail
    });

    it('should include pagination parameters in request', async () => {
      const backendResponse = {
        data: [mockBackendRegistrations[0]],
        pagination: {
          page: 2,
          limit: 1,
          totalItems: 10,
          totalPages: 10,
          hasNext: true,
          hasPrev: true,
        },
      };

      mockAxios
        .onGet(`/events/${eventCode}/registrations`, {
          params: {
            page: 2,
            limit: 1,
          },
        })
        .reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode, {
        pagination: { page: 2, limit: 1 },
      });

      expect(result.data).toHaveLength(1);
      expect(result.pagination.totalItems).toBe(10);
      expect(result.pagination.page).toBe(2);
    });

    it('should include status filter in request', async () => {
      const backendResponse = {
        data: [mockBackendRegistrations[0]],
        pagination: {
          page: 1,
          limit: 25,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mockAxios
        .onGet(`/events/${eventCode}/registrations`, {
          params: {
            status: ['CONFIRMED'],
          },
        })
        .reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode, {
        filters: { status: ['CONFIRMED'] },
      });

      expect(result.data).toHaveLength(1);
      expect(result.data[0].status).toBe('CONFIRMED');
    });

    it('should include multiple status filters', async () => {
      const backendResponse = {
        data: mockBackendRegistrations,
        pagination: {
          page: 1,
          limit: 25,
          totalItems: 2,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mockAxios
        .onGet(`/events/${eventCode}/registrations`, {
          params: {
            status: ['CONFIRMED', 'REGISTERED'],
          },
        })
        .reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode, {
        filters: { status: ['CONFIRMED', 'REGISTERED'] },
      });

      expect(result.data).toHaveLength(2);
    });

    it('should include company filter in request', async () => {
      const backendResponse = {
        data: [mockBackendRegistrations[0]],
        pagination: {
          page: 1,
          limit: 25,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mockAxios
        .onGet(`/events/${eventCode}/registrations`, {
          params: {
            companyId: 'centris-ag',
          },
        })
        .reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode, {
        filters: { companyId: 'centris-ag' },
      });

      expect(result.data[0].company?.id).toBe('centris-ag');
    });

    it('should include search parameter in request', async () => {
      const backendResponse = {
        data: [mockBackendRegistrations[0]],
        pagination: {
          page: 1,
          limit: 25,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mockAxios
        .onGet(`/events/${eventCode}/registrations`, {
          params: {
            search: 'john',
          },
        })
        .reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode, {
        search: 'john',
      });

      expect(result.data[0].firstName).toBe('John');
    });

    it('should include all parameters combined', async () => {
      const backendResponse = {
        data: [mockBackendRegistrations[0]],
        pagination: {
          page: 1,
          limit: 25,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      };

      mockAxios
        .onGet(`/events/${eventCode}/registrations`, {
          params: {
            page: 1,
            limit: 25,
            status: ['CONFIRMED'],
            companyId: 'centris-ag',
            search: 'john',
          },
        })
        .reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode, {
        pagination: { page: 1, limit: 25 },
        filters: { status: ['CONFIRMED'], companyId: 'centris-ag' },
        search: 'john',
      });

      expect(result.data).toHaveLength(1);
    });

    it('should handle empty registrations list', async () => {
      const backendResponse = {
        data: [],
        pagination: {
          page: 1,
          limit: 25,
          totalItems: 0,
          totalPages: 0,
          hasNext: false,
          hasPrev: false,
        },
      };

      mockAxios.onGet(`/events/${eventCode}/registrations`).reply(200, backendResponse);

      const result = await getEventRegistrations(eventCode);

      expect(result.data).toHaveLength(0);
      expect(result.pagination.totalItems).toBe(0);
    });

    it('should throw error on API failure', async () => {
      mockAxios.onGet(`/events/${eventCode}/registrations`).reply(500, {
        message: 'Internal server error',
      });

      await expect(getEventRegistrations(eventCode)).rejects.toThrow();
    });

    it('should throw error on 404', async () => {
      mockAxios.onGet(`/events/${eventCode}/registrations`).reply(404, {
        message: 'Event not found',
      });

      await expect(getEventRegistrations(eventCode)).rejects.toThrow();
    });

    it('should handle network error', async () => {
      mockAxios.onGet(`/events/${eventCode}/registrations`).networkError();

      await expect(getEventRegistrations(eventCode)).rejects.toThrow();
    });
  });
});
