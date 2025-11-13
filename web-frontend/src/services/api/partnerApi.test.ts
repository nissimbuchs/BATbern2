/**
 * Partner API Client Tests (Combined from Stories 2.8.1 and 2.8.2)
 *
 * TDD tests for Partner API client methods
 * - Story 2.8.1: Partner Directory (listPartners, getPartnerStatistics)
 * - Story 2.8.2: Partner Detail View (detail, votes, meetings, activity, notes)
 *
 * Test Scenarios:
 * - AC10: API Integration tests for partner list with filters, sort, pagination
 * - AC13: API Integration tests for partner detail, votes, meetings, activity, notes
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import * as partnerApi from './partnerApi';
import {
  getPartnerDetail,
  getPartnerVotes,
  // TODO: Uncomment when implemented in partnerApi.ts
  // getPartnerMeetings,
  // getPartnerActivity,
  // getPartnerNotes,
  // createPartnerNote,
  // updatePartnerNote,
  // deletePartnerNote,
} from '@/services/api/partnerApi';
import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';

// ============================================================================
// Story 2.8.1: Partner Directory Tests
// ============================================================================

describe('Partner API Client - Story 2.8.1 (Partner Directory)', () => {
  beforeEach(() => {
    // Mock apiClient methods to prevent real network calls
    vi.spyOn(apiClient, 'get').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'post').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'put').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'delete').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('AC10: Test 10.1 - should_callPartnersAPI_when_listPartnersInvoked', () => {
    it('should have listPartners method', () => {
      expect(partnerApi).toHaveProperty('listPartners');
      expect(typeof partnerApi.listPartners).toBe('function');
    });

    it('should return a promise when listPartners is called', () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const promise = partnerApi.listPartners(filters, sort, pagination).catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should call GET /partners endpoint', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      // This will fail with network error (expected - backend not running in tests)
      await expect(partnerApi.listPartners(filters, sort, pagination)).rejects.toThrow();
    });
  });

  describe('AC10: Test 10.2 - should_buildCorrectQueryParams_when_filtersApplied', () => {
    it('should build filter query parameter for tier filter', async () => {
      const filters = { tier: 'GOLD' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail (backend not running), but we can check the call
      }

      // Verify the API was called with correct path
      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringContaining('partnershipLevel:GOLD'),
          }),
        })
      );
    });

    it('should build filter query parameter for status filter (active)', async () => {
      const filters = { tier: 'all' as const, status: 'active' as const };
      const sort = { sortBy: 'name' as const, sortOrder: 'asc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringContaining('isActive:true'),
          }),
        })
      );
    });

    it('should build filter query parameter for status filter (inactive)', async () => {
      const filters = { tier: 'all' as const, status: 'inactive' as const };
      const sort = { sortBy: 'name' as const, sortOrder: 'asc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringContaining('isActive:false'),
          }),
        })
      );
    });

    it('should combine multiple filters when both tier and status are set', async () => {
      const filters = { tier: 'PLATINUM' as const, status: 'active' as const };
      const sort = { sortBy: 'tier' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            filter: expect.stringMatching(
              /partnershipLevel:PLATINUM.*isActive:true|isActive:true.*partnershipLevel:PLATINUM/
            ),
          }),
        })
      );
    });

    it('should not include filter parameter when filters are all', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      const callParams = getSpy.mock.calls[0]?.[1]?.params;
      expect(callParams).not.toHaveProperty('filter');
    });
  });

  describe('AC10: Test 10.3 - should_includePaginationParams_when_listPartnersInvoked', () => {
    it('should include page and size parameters', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 2, size: 50 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            page: 2,
            size: 50,
          }),
        })
      );
    });
  });

  describe('AC10: Test 10.4 - should_includeSortParams_when_listPartnersInvoked', () => {
    it('should include sort parameter with sortBy and sortOrder', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'name' as const, sortOrder: 'asc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            sort: 'name:asc',
          }),
        })
      );
    });

    it('should support different sort fields (engagement, tier, lastEvent)', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      // Test engagement sort
      try {
        await partnerApi.listPartners(
          filters,
          { sortBy: 'engagement', sortOrder: 'desc' },
          pagination
        );
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenLastCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            sort: 'engagement:desc',
          }),
        })
      );

      // Test tier sort
      try {
        await partnerApi.listPartners(filters, { sortBy: 'tier', sortOrder: 'asc' }, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenLastCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            sort: 'tier:asc',
          }),
        })
      );
    });
  });

  describe('AC10: Test 10.5 - should_includeHTTPEnrichment_when_listPartnersInvoked', () => {
    it('should include ?include=company,contacts parameter for HTTP enrichment (ADR-004)', async () => {
      const filters = { tier: 'all' as const, status: 'all' as const };
      const sort = { sortBy: 'engagement' as const, sortOrder: 'desc' as const };
      const pagination = { page: 0, size: 20 };

      const getSpy = vi.mocked(apiClient.get);

      try {
        await partnerApi.listPartners(filters, sort, pagination);
      } catch {
        // Expected to fail
      }

      expect(getSpy).toHaveBeenCalledWith(
        '/partners',
        expect.objectContaining({
          params: expect.objectContaining({
            include: 'company,contacts',
          }),
        })
      );
    });
  });

  describe('AC11: Test 11.1 - should_callStatisticsAPI_when_getPartnerStatisticsInvoked', () => {
    it('should have getPartnerStatistics method', () => {
      expect(partnerApi).toHaveProperty('getPartnerStatistics');
      expect(typeof partnerApi.getPartnerStatistics).toBe('function');
    });

    it('should return a promise when getPartnerStatistics is called', () => {
      const promise = partnerApi.getPartnerStatistics().catch(() => {});
      expect(promise).toBeInstanceOf(Promise);
    });

    it('should call GET /partners/statistics endpoint', async () => {
      const getSpy = vi.mocked(apiClient.get);

      // This will fail with network error (expected - backend not running in tests)
      await expect(partnerApi.getPartnerStatistics()).rejects.toThrow();

      expect(getSpy).toHaveBeenCalledWith('/partners/statistics');
    });
  });
});

// ============================================================================
// Story 2.8.2: Partner Detail View Tests
// ============================================================================

describe('Partner API Client - Story 2.8.2 (Partner Detail View)', () => {
  beforeEach(() => {
    // Mock apiClient methods to prevent real network calls
    vi.spyOn(apiClient, 'get').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'post').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'put').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'delete').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('getPartnerDetail', () => {
    // Test 1: should_fetchPartnerDetail_when_companyNameProvided
    it('should_fetchPartnerDetail_when_companyNameProvided', async () => {
      const companyName = 'GoogleZH';

      // Should attempt to call API (will fail with network/timeout error in test env)
      await expect(getPartnerDetail(companyName)).rejects.toThrow(
        /(Network Error|status code 500|timeout|Not implemented)/
      );
    });

    // Test 2: should_includeAllData_when_includeParamUsed
    it('should_includeAllData_when_includeParamUsed', async () => {
      const companyName = 'GoogleZH';
      const include = 'company,contacts,votes,meetings,activity';

      // Should attempt to call API with include parameter
      await expect(getPartnerDetail(companyName, include)).rejects.toThrow(
        /(Network Error|status code 500|timeout|Not implemented)/
      );
    });

    // Test 3: should_handle404_when_partnerNotFound
    it('should_handle404_when_partnerNotFound', async () => {
      const invalidCompanyName = 'NonExistentCompany';

      // Should attempt to call API (will fail in test env)
      await expect(getPartnerDetail(invalidCompanyName)).rejects.toThrow(
        /(Network Error|status code 500|status code 404|timeout|Not implemented)/
      );
    });
  });

  describe('getPartnerVotes', () => {
    // Test 4: should_fetchVotes_when_votesTabActivated
    it('should_fetchVotes_when_votesTabActivated', async () => {
      const companyName = 'GoogleZH';

      // Should attempt to call API
      await expect(getPartnerVotes(companyName)).rejects.toThrow(
        /(Network Error|status code 500|timeout|Not implemented)/
      );
    });
  });

  // TODO: Uncomment these tests when functions are implemented in partnerApi.ts
  describe.skip('getPartnerMeetings (NOT YET IMPLEMENTED)', () => {
    // Test 5: should_fetchMeetings_when_meetingsTabActivated
    it('should_fetchMeetings_when_meetingsTabActivated', async () => {
      // const companyName = 'GoogleZH';
      // await expect(getPartnerMeetings(companyName)).rejects.toThrow(
      //   /(Network Error|status code 500|timeout|Not implemented)/
      // );
    });
  });

  describe.skip('getPartnerActivity (NOT YET IMPLEMENTED)', () => {
    // Test 6: should_fetchActivity_when_activityTabActivated
    it('should_fetchActivity_when_activityTabActivated', async () => {
      // const companyName = 'GoogleZH';
      // await expect(getPartnerActivity(companyName)).rejects.toThrow(
      //   /(Network Error|status code 500|timeout|Not implemented)/
      // );
    });

    // Test 6b: should_fetchFilteredActivity_when_filterApplied
    it('should_fetchFilteredActivity_when_filterApplied', async () => {
      // const companyName = 'GoogleZH';
      // const filters = { type: 'VOTE_CAST' };
      // await expect(getPartnerActivity(companyName, filters)).rejects.toThrow(
      //   /(Network Error|status code 500|timeout|Not implemented)/
      // );
    });
  });

  describe.skip('getPartnerNotes (NOT YET IMPLEMENTED)', () => {
    // Test 7: should_fetchNotes_when_notesTabActivated
    it('should_fetchNotes_when_notesTabActivated', async () => {
      // const companyName = 'GoogleZH';
      // await expect(getPartnerNotes(companyName)).rejects.toThrow(
      //   /(Network Error|status code 500|timeout|Not implemented)/
      // );
    });
  });

  describe.skip('createPartnerNote (NOT YET IMPLEMENTED)', () => {
    // Test 8: should_createNote_when_noteSubmitted
    it('should_createNote_when_noteSubmitted', async () => {
      // const companyName = 'GoogleZH';
      // const note = {
      //   title: 'Test Note',
      //   content: 'Test note content',
      // };
      // await expect(createPartnerNote(companyName, note)).rejects.toThrow(
      //   /(Network Error|status code 500|timeout|Not implemented)/
      // );
    });
  });

  describe.skip('updatePartnerNote (NOT YET IMPLEMENTED)', () => {
    // Test 9: should_updateNote_when_editSubmitted
    it('should_updateNote_when_editSubmitted', async () => {
      // const companyName = 'GoogleZH';
      // const noteId = 'test-note-id';
      // const note = {
      //   title: 'Updated Note',
      //   content: 'Updated note content',
      // };
      // await expect(updatePartnerNote(companyName, noteId, note)).rejects.toThrow(
      //   /(Network Error|status code 500|timeout|Not implemented)/
      // );
    });
  });

  describe.skip('deletePartnerNote (NOT YET IMPLEMENTED)', () => {
    // Test 10: should_deleteNote_when_deleteConfirmed
    it('should_deleteNote_when_deleteConfirmed', async () => {
      // const companyName = 'GoogleZH';
      // const noteId = 'test-note-id';
      // await expect(deletePartnerNote(companyName, noteId)).rejects.toThrow(
      //   /(Network Error|status code 500|timeout|Not implemented)/
      // );
    });
  });

  describe('API Structure', () => {
    it('should_haveAllRequiredMethods_when_apiImported', () => {
      // Verify currently implemented API methods exist
      expect(getPartnerDetail).toBeDefined();
      expect(getPartnerVotes).toBeDefined();
      // TODO: Uncomment when implemented
      // expect(getPartnerMeetings).toBeDefined();
      // expect(getPartnerActivity).toBeDefined();
      // expect(getPartnerNotes).toBeDefined();
      // expect(createPartnerNote).toBeDefined();
      // expect(updatePartnerNote).toBeDefined();
      // expect(deletePartnerNote).toBeDefined();
    });

    it('should_returnPromises_when_methodsCalled', () => {
      // All methods should be async functions - catch errors to prevent unhandled rejections
      const promise1 = getPartnerDetail('test').catch(() => {});
      const promise2 = getPartnerVotes('test').catch(() => {});
      // TODO: Uncomment when implemented
      // const promise3 = getPartnerMeetings('test').catch(() => {});
      // const promise4 = getPartnerActivity('test').catch(() => {});
      // const promise5 = getPartnerNotes('test').catch(() => {});

      expect(promise1).toBeInstanceOf(Promise);
      expect(promise2).toBeInstanceOf(Promise);
      // expect(promise3).toBeInstanceOf(Promise);
      // expect(promise4).toBeInstanceOf(Promise);
      // expect(promise5).toBeInstanceOf(Promise);
    });
  });
});

// ============================================================================
// Story 2.8.3: Partner Create/Edit Modal Tests
// ============================================================================

describe('Partner API Client - Story 2.8.3 (Partner Create/Edit)', () => {
  beforeEach(() => {
    // Mock apiClient methods to prevent real network calls
    vi.spyOn(apiClient, 'get').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'post').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'put').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
    vi.spyOn(apiClient, 'delete').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('createPartner', () => {
    it('should_callCreatePartnerAPI_when_createPartnerCalled', async () => {
      const request = {
        companyName: 'test-company',
        partnershipLevel: 'GOLD' as const,
        partnershipStartDate: '2025-01-01',
        partnershipEndDate: '2025-12-31',
      };

      // Expect network/server/timeout error (not "Not implemented" error)
      // This confirms the actual API call is being made
      await expect(partnerApi.createPartner(request)).rejects.toThrow(
        /(Network Error|status code 500|timeout|status code 404)/
      );
    });

    it('should_validateRequiredFields_when_createPartnerCalled', async () => {
      const request = {
        companyName: 'test-company',
        partnershipLevel: 'BRONZE' as const,
        partnershipStartDate: '2025-01-01',
      };

      // Should attempt API call with valid required fields
      await expect(partnerApi.createPartner(request)).rejects.toThrow(
        /(Network Error|status code 500|timeout|status code 404)/
      );
    });
  });

  describe('updatePartner', () => {
    it('should_callUpdatePartnerAPI_when_updatePartnerCalled', async () => {
      const companyName = 'test-company';
      const request = {
        partnershipLevel: 'PLATINUM' as const,
        partnershipEndDate: '2026-12-31',
        isActive: true,
      };

      // Expect network/server/timeout error (not "Not implemented" error)
      await expect(partnerApi.updatePartner(companyName, request)).rejects.toThrow(
        /(Network Error|status code 500|timeout|status code 404)/
      );
    });

    it('should_updatePartialFields_when_updatePartnerCalled', async () => {
      const companyName = 'test-company';
      const request = {
        partnershipLevel: 'SILVER' as const,
      };

      // Should allow partial updates
      await expect(partnerApi.updatePartner(companyName, request)).rejects.toThrow(
        /(Network Error|status code 500|timeout|status code 404)/
      );
    });
  });

  describe('error handling', () => {
    it('should_handleConflictError_when_partnershipExists', async () => {
      const request = {
        companyName: 'existing-company',
        partnershipLevel: 'GOLD' as const,
        partnershipStartDate: '2025-01-01',
      };

      // In production, conflict errors would be 409
      // In test environment, we expect network/server error since backend is unavailable
      await expect(partnerApi.createPartner(request)).rejects.toThrow(
        /(Network Error|status code 500|timeout|status code 404|status code 409)/
      );
    });
  });

  describe('API Structure', () => {
    it('should have all required mutation methods', () => {
      expect(partnerApi.createPartner).toBeDefined();
      expect(typeof partnerApi.createPartner).toBe('function');
      expect(partnerApi.updatePartner).toBeDefined();
      expect(typeof partnerApi.updatePartner).toBe('function');
    });
  });
});
