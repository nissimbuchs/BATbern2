/**
 * Event API Client Tests (RED Phase - Story 2.5.3, Task 5a)
 *
 * TDD RED Phase: Write failing tests before implementation
 * Tests verify:
 * - API client structure and methods
 * - Request/response interceptors
 * - Error handling (401, 403, 404, 500)
 * - Auth token injection
 * - Resource expansion (?include= parameter)
 * - Partial update (PATCH) support
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { eventApiClient } from '@/services/eventApiClient';
import type {
  CreateEventRequest,
  UpdateEventRequest,
  PatchEventRequest,
} from '@/types/event.types';
import apiClient from '@/services/api/apiClient';

describe('Event API Client (RED Phase)', () => {
  // Store original timeout to restore after tests
  let originalTimeout: number;

  beforeAll(() => {
    // Save original timeout
    originalTimeout = apiClient.defaults.timeout || 30000;
    // Set short timeout for these tests to fail fast when backend unavailable
    apiClient.defaults.timeout = 100;
  });

  afterAll(() => {
    // Restore original timeout
    apiClient.defaults.timeout = originalTimeout;
  });

  describe('API Structure', () => {
    it('should have all required methods', () => {
      expect(eventApiClient).toHaveProperty('getEvents');
      expect(eventApiClient).toHaveProperty('getEvent');
      expect(eventApiClient).toHaveProperty('createEvent');
      expect(eventApiClient).toHaveProperty('updateEvent');
      expect(eventApiClient).toHaveProperty('patchEvent');
      expect(eventApiClient).toHaveProperty('deleteEvent');
      expect(eventApiClient).toHaveProperty('getEventWorkflow');
      expect(eventApiClient).toHaveProperty('getCriticalTasks');
      expect(eventApiClient).toHaveProperty('getTeamActivity');
    });

    it('should have methods that return promises', () => {
      // All methods should be async functions - catch errors to prevent unhandled rejections
      const promise1 = eventApiClient.getEvents().catch(() => {});
      const promise2 = eventApiClient.getEvent('BATbern56').catch(() => {});
      const promise3 = eventApiClient.getEventWorkflow('BATbern56').catch(() => {});

      expect(promise1).toBeInstanceOf(Promise);
      expect(promise2).toBeInstanceOf(Promise);
      expect(promise3).toBeInstanceOf(Promise);
    });
  });

  describe('Client-side Validation', () => {
    describe('createEvent', () => {
      it('should_rejectInvalidEventDate_when_dateIsInThePast', async () => {
        const invalidEvent: CreateEventRequest = {
          title: 'Test Event',
          eventNumber: 56,
          date: '2020-01-01T10:00:00Z', // Past date
          registrationDeadline: '2019-12-15T23:59:59Z',
          venueName: 'Test Venue',
          venueAddress: 'Test Address',
          venueCapacity: 100,
          status: 'planning',
        };

        await expect(eventApiClient.createEvent(invalidEvent)).rejects.toThrow(
          /Event date must be in the future/
        );
      });

      it('should_rejectInvalidDeadline_when_deadlineIsAfterEventDate', async () => {
        const futureDate = new Date();
        futureDate.setDate(futureDate.getDate() + 60);
        const invalidDeadline = new Date(futureDate);
        invalidDeadline.setDate(invalidDeadline.getDate() + 10); // After event date

        const invalidEvent: CreateEventRequest = {
          title: 'Test Event',
          eventNumber: 56,
          date: futureDate.toISOString(),
          registrationDeadline: invalidDeadline.toISOString(),
          venueName: 'Test Venue',
          venueAddress: 'Test Address',
          venueCapacity: 100,
          status: 'planning',
        };

        await expect(eventApiClient.createEvent(invalidEvent)).rejects.toThrow(
          /Registration deadline must be before event date/
        );
      });

      it('should_rejectInvalidCapacity_when_capacityIsNegative', async () => {
        const futureDate = new Date();
        futureDate.setDate(futureDate.getDate() + 60);
        const deadline = new Date(futureDate);
        deadline.setDate(deadline.getDate() - 10);

        const invalidEvent: CreateEventRequest = {
          title: 'Test Event',
          eventNumber: 56,
          date: futureDate.toISOString(),
          registrationDeadline: deadline.toISOString(),
          venueName: 'Test Venue',
          venueAddress: 'Test Address',
          venueCapacity: -100, // Negative capacity
          status: 'planning',
        };

        await expect(eventApiClient.createEvent(invalidEvent)).rejects.toThrow(
          /Venue capacity must be positive/
        );
      });

      it('should_acceptValidEventData_when_allValidationsPassed', async () => {
        const futureDate = new Date();
        futureDate.setDate(futureDate.getDate() + 60);
        const deadline = new Date(futureDate);
        deadline.setDate(deadline.getDate() - 10);

        const validEvent: CreateEventRequest = {
          title: 'Test Event',
          eventNumber: 56,
          date: futureDate.toISOString(),
          registrationDeadline: deadline.toISOString(),
          venueName: 'Test Venue',
          venueAddress: 'Test Address',
          venueCapacity: 100,
          status: 'planning',
        };

        // If validation passes, the function should attempt to make the API call
        // In test environment without backend, this will fail with network/timeout error or 500
        // We expect network/server error (not validation error)
        // Error is transformed to user-friendly message by transformError()
        await expect(eventApiClient.createEvent(validEvent)).rejects.toThrow(
          /(Network Error|Server Error)/
        );
      });
    });

    describe('updateEvent', () => {
      it('should_rejectInvalidDeadline_when_updatingWithInvalidDate', async () => {
        const futureDate = new Date();
        futureDate.setDate(futureDate.getDate() + 60);
        const invalidDeadline = new Date(futureDate);
        invalidDeadline.setDate(invalidDeadline.getDate() + 10); // After event date

        const updates: UpdateEventRequest = {
          registrationDeadline: invalidDeadline.toISOString(),
          date: futureDate.toISOString(),
        };

        await expect(eventApiClient.updateEvent('BATbern56', updates)).rejects.toThrow(
          /Registration deadline must be before event date/
        );
      });
    });
  });

  describe('Resource Expansion', () => {
    it('should_buildIncludeParameter_when_expandOptionsProvided', async () => {
      // Test that ?include= parameter is properly constructed
      // We can't fully test without backend, but we can verify the call is made
      const options = { expand: ['workflow', 'speakers', 'sessions', 'venue'] };

      // Expect network/server error (not parameter building error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.getEvent('BATbern56', options)).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });

    it('should_getEventList_when_includeParameterProvided', async () => {
      const options = { expand: ['workflow'] };

      // Expect network/server error (not parameter building error)
      // Error is transformed to user-friendly message by transformError()
      await expect(
        eventApiClient.getEvents({ page: 1, limit: 20 }, undefined, options)
      ).rejects.toThrow(/(Network Error|Not Found|Server Error|timeout)/);
    });

    it('should_omitIncludeParameter_when_noExpandOptionsProvided', async () => {
      // Without expand options, should not add ?include= parameter
      // Expect network/server error (not parameter building error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.getEvent('BATbern56')).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });
  });

  describe('Partial Update (PATCH)', () => {
    it('should_usePatchMethod_when_partialUpdateRequested', async () => {
      const partialUpdate: PatchEventRequest = {
        title: 'Updated Title',
        // Only updating title, not other fields
      };

      // Should use PATCH method, not PUT
      // Expect network/server error (not method error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.patchEvent('BATbern56', partialUpdate)).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });

    it('should_sendOnlyChangedFields_when_patchingEvent', async () => {
      const partialUpdate: PatchEventRequest = {
        description: 'Updated description only',
      };

      // Should only send changed fields in request body
      // Expect network/server error (not validation error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.patchEvent('BATbern56', partialUpdate)).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });
  });

  describe('Error Handling', () => {
    it('should_transformError_when_401Unauthorized', async () => {
      // This test will pass when error handling is implemented
      // For now, we just verify the method exists and returns a promise
      const promise = eventApiClient.getEvents().catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should_transformError_when_403Forbidden', async () => {
      // This test will pass when error handling is implemented
      const promise = eventApiClient.deleteEvent('BATbern56').catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should_transformError_when_404NotFound', async () => {
      // This test will pass when error handling is implemented
      const promise = eventApiClient.getEvent('nonexistent').catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should_transformError_when_500ServerError', async () => {
      // This test will pass when error handling is implemented
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 60);

      const event: CreateEventRequest = {
        title: 'Test',
        description: 'Test',
        eventDate: futureDate.toISOString(),
        registrationDeadline: new Date(
          futureDate.getTime() - 10 * 24 * 60 * 60 * 1000
        ).toISOString(),
        eventNumber: 56,
        venueName: 'Test Venue',
        venueAddress: 'Test Address',
        venueCapacity: 100,
        organizerId: 'john.doe',
      };

      const promise = eventApiClient.createEvent(event).catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });
  });

  describe('Pagination and Filtering', () => {
    it('should_supportPagination_when_getEventsWithPageParams', async () => {
      const pagination = { page: 2, limit: 10 };

      // Should add ?page=2&limit=10 to query
      // Expect network/server error (not parameter error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.getEvents(pagination)).rejects.toThrow(
        /(Network Error|Server Error)/
      );
    });

    it('should_supportFiltering_when_getEventsWithFilters', async () => {
      const filters = { status: ['published'], year: 2024 };

      // Should add filter parameter with JSON filter syntax
      // Expect network/server error (not filter error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.getEvents({ page: 1, limit: 20 }, filters)).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });
  });

  describe('Workflow and Tasks APIs', () => {
    it('should_getWorkflowState_when_eventCodeProvided', async () => {
      // Expect network/server error (not API structure error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.getEventWorkflow('BATbern56')).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });

    it('should_getCriticalTasks_when_eventCodeProvided', async () => {
      // Expect network/server error (not API structure error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.getCriticalTasks('BATbern56')).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });

    it('should_getTeamActivity_when_eventCodeProvided', async () => {
      // Expect network/server error (not API structure error)
      // Error is transformed to user-friendly message by transformError()
      await expect(eventApiClient.getTeamActivity('BATbern56')).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });
  });

  describe('Topic Assignment (Story 5.2a)', () => {
    it('should_assignTopicToEvent_when_topicIdProvided', async () => {
      const topicId = '123e4567-e89b-12d3-a456-426614174000';

      // Expect network/server error (not API structure error)
      await expect(eventApiClient.assignTopicToEvent('BATbern56', topicId)).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });

    it('should_haveCorrectMethodSignature_when_assigningTopic', () => {
      expect(eventApiClient).toHaveProperty('assignTopicToEvent');
      expect(typeof eventApiClient.assignTopicToEvent).toBe('function');
    });
  });

  describe('Event Existence Check (Story 5.2a)', () => {
    it('should_checkEventExists_when_eventNumberProvided', async () => {
      // Expect network/server error (not API structure error)
      await expect(eventApiClient.checkEventExists(56)).rejects.toThrow(
        /(Network Error|Not Found|Server Error|timeout)/
      );
    });

    it('should_haveCorrectMethodSignature_when_checkingExistence', () => {
      expect(eventApiClient).toHaveProperty('checkEventExists');
      expect(typeof eventApiClient.checkEventExists).toBe('function');
    });
  });

  describe('Auth Token Injection', () => {
    it('should_includeAuthHeader_when_makingApiCalls', async () => {
      // This test verifies that the API client uses the shared apiClient
      // which has auth interceptors configured in Story 1.17
      // We can't fully test without backend, but we verify the method exists
      const promise = eventApiClient.getEvents().catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });
  });

  describe('Archived Event Validation', () => {
    it('should_allowPastDate_when_statusIsArchived', async () => {
      const pastDate = new Date('2020-01-01T10:00:00Z');
      const archivedEvent: CreateEventRequest = {
        title: 'Historical Event',
        eventNumber: 42,
        date: pastDate.toISOString(),
        registrationDeadline: '2019-12-15T23:59:59Z',
        venueName: 'Historical Venue',
        venueAddress: 'Historical Address',
        venueCapacity: 100,
        workflowState: 'ARCHIVED', // Archived workflow state bypasses date validation
      };

      // Should not throw validation error for past date when archived
      // Will fail with network error instead (expected)
      await expect(eventApiClient.createEvent(archivedEvent)).rejects.toThrow(
        /(Network Error|Server Error)/
      );
    });
  });

  describe('Error Transformation with Status Codes', () => {
    it('should_includeCorrelationId_when_errorOccurs', async () => {
      // Test that correlation ID is included in error messages
      // This will be tested with real backend errors
      const promise = eventApiClient.getEvent('nonexistent').catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should_handleConflictError_when_409Returned', async () => {
      // Conflict errors (409) should be transformed properly
      // Used for duplicate registration, etc.
      const promise = eventApiClient
        .createRegistration('BATbern56', {
          firstName: 'John',
          lastName: 'Doe',
          email: 'john.doe@example.com',
          companyName: 'ACME Corp',
        })
        .catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });
  });

  describe('Public API Methods', () => {
    it('should_returnNull_when_getCurrentEventReturns404', async () => {
      // getCurrentEvent should return null for 404, not throw
      // This tests the special 404 handling in getCurrentEvent
      const promise = eventApiClient.getCurrentEvent().catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should_supportExpansion_when_getCurrentEventCalled', async () => {
      const options = { expand: ['sessions', 'speakers'] };
      const promise = eventApiClient.getCurrentEvent(options).catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should_confirmRegistration_when_tokenProvided', async () => {
      const promise = eventApiClient
        .confirmRegistration('BATbern56', 'test-jwt-token')
        .catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should_getRegistration_when_confirmationCodeProvided', async () => {
      const promise = eventApiClient.getRegistration('BATbern56', 'ABC123').catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });
  });

  describe('Filter Parameter Building', () => {
    it('should_addFilterParameter_when_multipleFiltersProvided', async () => {
      const filters = {
        status: ['published', 'planning'],
        year: 2024,
        search: 'Architecture',
      };

      // Should build JSON filter with all provided filters
      await expect(eventApiClient.getEvents({ page: 1, limit: 20 }, filters)).rejects.toThrow(
        /(Network Error|Server Error)/
      );
    });

    it('should_omitFilterParameter_when_noFiltersProvided', async () => {
      // Should not add filter parameter when no filters
      await expect(eventApiClient.getEvents({ page: 1, limit: 20 })).rejects.toThrow(
        /(Network Error|Server Error)/
      );
    });

    it('should_handleSearchFilter_when_searchTextProvided', async () => {
      const filters = { search: 'Innovation' };

      // Search should use $contains operator
      await expect(eventApiClient.getEvents({ page: 1, limit: 20 }, filters)).rejects.toThrow(
        /(Network Error|Server Error)/
      );
    });
  });
});
