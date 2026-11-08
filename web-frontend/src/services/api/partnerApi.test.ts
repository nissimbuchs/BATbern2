/**
 * Partner API Client Tests (Stories 2.8.1, 2.8.2, 2.8.3)
 *
 * Every call runs through the real apiClient against the MSW harness (17.0c2): the request is
 * checked against partners-api, and the test reads what went over the wire (`api.calls`) instead
 * of the arguments of a spied apiClient. Bodies are typed from the generated partner types.
 *
 * - Story 2.8.1: Partner Directory (listPartners, getPartnerStatistics)
 * - Story 2.8.2: Partner Detail View (getPartnerDetail)
 * - Story 2.8.3: Partner Create/Edit (createPartner, updatePartner, getPartnerContacts)
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse } from 'msw';
import { fetchAuthSession } from 'aws-amplify/auth';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import * as partnerApi from './partnerApi';
import type { PartnerFilters, PartnerResponse } from './partnerApi';

type PartnerList = ResponseBody<'listPartners', 200>;

const gold: PartnerResponse = {
  companyName: 'mobiliar',
  partnershipLevel: 'GOLD',
  partnershipStartDate: '2020-01-01',
  isActive: true,
};

const page = (data: PartnerResponse[], pageNo = 0, limit = 20): PartnerList => ({
  data,
  metadata: {
    page: pageNo,
    limit,
    totalItems: data.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: pageNo > 0,
  },
});

const problem = (status: number, message: string) =>
  HttpResponse.json({ error: 'ERROR', message, status }, { status });

const all: PartnerFilters = { tier: 'all', status: 'all' };
const byEngagement = { sortBy: 'engagement', sortOrder: 'desc' } as const;
const firstPage = { page: 0, size: 20 };

/** The `filter` query of the only listPartners call of the test. */
const sentFilter = () => api.calls('listPartners')[0].query.get('filter');

// ============================================================================
// Story 2.8.1: Partner Directory Tests
// ============================================================================

describe('Partner API Client - Story 2.8.1 (Partner Directory)', () => {
  describe('AC10: Test 10.1 - should_callPartnersAPI_when_listPartnersInvoked', () => {
    it('should call GET /partners and return the page the backend sends', async () => {
      api.on('listPartners', () => page([gold]));

      const result = await partnerApi.listPartners(all, byEngagement, firstPage);

      const calls = api.calls('listPartners');
      expect(calls).toHaveLength(1);
      expect(calls[0].method).toBe('GET');
      expect(result.data).toEqual([gold]);
      expect(result.metadata.totalItems).toBe(1);
    });

    it('should reject with ERR_NETWORK when the partner service is unreachable', async () => {
      api.on('listPartners', () => HttpResponse.error());

      await expect(partnerApi.listPartners(all, byEngagement, firstPage)).rejects.toMatchObject({
        code: 'ERR_NETWORK',
      });
    });
  });

  describe('AC10: Test 10.2 - should_buildCorrectQueryParams_when_filtersApplied', () => {
    it('should build filter query parameter for tier filter', async () => {
      await partnerApi.listPartners({ tier: 'GOLD', status: 'all' }, byEngagement, firstPage);

      expect(sentFilter()).toBe('partnershipLevel:GOLD');
    });

    it('should build filter query parameter for status filter (active)', async () => {
      await partnerApi.listPartners({ tier: 'all', status: 'active' }, byEngagement, firstPage);

      expect(sentFilter()).toBe('isActive:true');
    });

    it('should build filter query parameter for status filter (inactive)', async () => {
      await partnerApi.listPartners({ tier: 'all', status: 'inactive' }, byEngagement, firstPage);

      expect(sentFilter()).toBe('isActive:false');
    });

    it('should combine multiple filters when both tier and status are set', async () => {
      await partnerApi.listPartners(
        { tier: 'PLATINUM', status: 'active' },
        { sortBy: 'tier', sortOrder: 'desc' },
        firstPage
      );

      expect(sentFilter()).toBe('partnershipLevel:PLATINUM,isActive:true');
    });

    it('should not include filter parameter when filters are all', async () => {
      await partnerApi.listPartners(all, byEngagement, firstPage);

      const { query } = api.calls('listPartners')[0];
      expect(query.has('filter')).toBe(false);
      expect(query.get('include')).toBe('company,contacts'); // positive control: params sent
    });
  });

  describe('AC10: Test 10.3 - should_includePaginationParams_when_listPartnersInvoked', () => {
    it('should include page and size parameters', async () => {
      await partnerApi.listPartners(all, byEngagement, { page: 2, size: 50 });

      const { query } = api.calls('listPartners')[0];
      expect(query.get('page')).toBe('2');
      expect(query.get('size')).toBe('50');
    });
  });

  describe('AC10: Test 10.4 - sort is not sent', () => {
    // GET /partners declares no sort parameter and PCS always sorts by company slug: the
    // `sort=name:asc` this test used to pin was ignored by the backend (found by the MSW harness,
    // 17.0c1). Whether the directory gets a backend sort is an open owner decision.
    it('sends no sort parameter, whatever the sort configuration', async () => {
      await partnerApi.listPartners(all, { sortBy: 'name', sortOrder: 'asc' }, firstPage);
      await partnerApi.listPartners(all, byEngagement, firstPage);

      const calls = api.calls('listPartners');
      expect(calls).toHaveLength(2);
      for (const { query } of calls) {
        expect(query.has('sort')).toBe(false);
        expect(query.get('include')).toBe('company,contacts');
      }
    });
  });

  describe('AC10: Test 10.5 - should_includeHTTPEnrichment_when_listPartnersInvoked', () => {
    it('should include ?include=company,contacts parameter for HTTP enrichment (ADR-004)', async () => {
      await partnerApi.listPartners(all, byEngagement, firstPage);

      expect(api.calls('listPartners')[0].query.get('include')).toBe('company,contacts');
    });
  });

  describe('AC11: Test 11.1 - should_callStatisticsAPI_when_getPartnerStatisticsInvoked', () => {
    it('should call GET /partners/statistics and return the statistics', async () => {
      api.on('getPartnerStatistics', () => ({
        totalPartners: 12,
        activePartners: 10,
        tierCounts: { GOLD: 4, SILVER: 6 },
      }));

      const stats = await partnerApi.getPartnerStatistics();

      expect(api.calls('getPartnerStatistics')).toHaveLength(1);
      expect(stats).toMatchObject({ totalPartners: 12, activePartners: 10 });
    });

    it('should reject when the statistics fail', async () => {
      api.on('getPartnerStatistics', () => problem(500, 'Statistics unavailable'));

      await expect(partnerApi.getPartnerStatistics()).rejects.toMatchObject({
        response: { status: 500 },
      });
    });
  });
});

// ============================================================================
// Story 2.8.2: Partner Detail View Tests
// ============================================================================

describe('Partner API Client - Story 2.8.2 (Partner Detail View)', () => {
  describe('getPartnerDetail', () => {
    it('should_fetchPartnerDetail_when_companyNameProvided', async () => {
      api.on('getPartnerByCompanyName', ({ params }) => ({
        ...gold,
        companyName: params.companyName,
      }));

      const partner = await partnerApi.getPartnerDetail('mobiliar');

      const [call] = api.calls('getPartnerByCompanyName');
      expect(call.params.companyName).toBe('mobiliar');
      expect(call.query.has('include')).toBe(false);
      expect(partner).toMatchObject({ companyName: 'mobiliar', partnershipLevel: 'GOLD' });
    });

    it('should_includeAllData_when_includeParamUsed', async () => {
      await partnerApi.getPartnerDetail('mobiliar', 'company,contacts');

      expect(api.calls('getPartnerByCompanyName')[0].query.get('include')).toBe('company,contacts');
    });

    it('should_handle404_when_partnerNotFound', async () => {
      api.on('getPartnerByCompanyName', () => problem(404, 'Partner not found'));

      await expect(partnerApi.getPartnerDetail('unbekannt')).rejects.toMatchObject({
        response: { status: 404, data: { message: 'Partner not found' } },
      });
    });
  });
});

// ============================================================================
// Story 2.8.3: Partner Create/Edit Modal Tests
// ============================================================================

describe('Partner API Client - Story 2.8.3 (Partner Create/Edit)', () => {
  describe('createPartner', () => {
    it('should_returnCreatedPartner_when_apiReturnsSuccess', async () => {
      const request = {
        companyName: 'brunnimann',
        partnershipLevel: 'GOLD' as const,
        partnershipStartDate: '2025-01-01',
        partnershipEndDate: '2025-12-31',
      };
      api.on('createPartner', ({ body }) => ({ ...body!, isActive: true }), { status: 201 });

      const result = await partnerApi.createPartner(request);

      const [call] = api.calls('createPartner');
      expect(call.method).toBe('POST');
      expect(call.body).toEqual(request);
      expect(result).toEqual({ ...request, isActive: true });
    });

    it('should_sendOnlyRequiredFields_when_noEndDate', async () => {
      const request = {
        companyName: 'brunnimann',
        partnershipLevel: 'BRONZE' as const,
        partnershipStartDate: '2025-01-01',
      };

      await partnerApi.createPartner(request);

      expect(api.calls('createPartner')[0].body).toEqual(request);
    });

    it('should_handleConflictError_when_partnershipExists', async () => {
      api.on('createPartner', () => problem(409, 'Partnership already exists'));

      await expect(
        partnerApi.createPartner({
          companyName: 'mobiliar',
          partnershipLevel: 'GOLD',
          partnershipStartDate: '2025-01-01',
        })
      ).rejects.toMatchObject({ response: { status: 409 } });
    });
  });

  describe('updatePartner', () => {
    it('should_returnUpdatedPartner_when_apiReturnsSuccess', async () => {
      const request = { partnershipLevel: 'PLATINUM' as const, partnershipEndDate: '2026-12-31' };
      api.on('updatePartner', ({ body, params }) => ({
        ...gold,
        companyName: params.companyName,
        ...body,
      }));

      const result = await partnerApi.updatePartner('mobiliar', request);

      const [call] = api.calls('updatePartner');
      expect(call.method).toBe('PATCH');
      expect(call.params.companyName).toBe('mobiliar');
      expect(call.body).toEqual(request);
      expect(result).toMatchObject({ companyName: 'mobiliar', partnershipLevel: 'PLATINUM' });
    });

    it('should_updatePartialFields_when_updatePartnerCalled', async () => {
      await partnerApi.updatePartner('mobiliar', { partnershipLevel: 'SILVER' });

      expect(api.calls('updatePartner')[0].body).toEqual({ partnershipLevel: 'SILVER' });
    });
  });

  describe('getPartnerContacts', () => {
    it('should_fetchContacts_when_companyNameProvided', async () => {
      const contacts = [
        {
          username: 'hong.an',
          firstName: 'Hông',
          lastName: 'Ân',
          email: 'hong.an@example.com',
        },
        {
          username: 'matthias.keller',
          firstName: 'Matthias',
          lastName: 'Keller',
          email: 'Matthias.Keller@example.com',
        },
      ];
      api.on('getPartnerContacts', () => contacts);

      const result = await partnerApi.getPartnerContacts('mobiliar');

      expect(api.calls('getPartnerContacts')[0].params.companyName).toBe('mobiliar');
      expect(result).toEqual(contacts);
    });

    it('should_returnEmptyArray_when_noContactsFound', async () => {
      api.on('getPartnerContacts', () => []);

      expect(await partnerApi.getPartnerContacts('ohnekontakt')).toEqual([]);
      expect(api.calls('getPartnerContacts')[0].params.companyName).toBe('ohnekontakt');
    });

    it('should_throwError_when_partnerNotFound', async () => {
      api.on('getPartnerContacts', () => problem(404, 'Partner not found'));

      await expect(partnerApi.getPartnerContacts('unbekannt')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });
});

// ============================================================================
// Public partner list and the partner's own company
// ============================================================================

describe('Partner API Client (moved calls)', () => {
  // A signed-in browser: a Cognito key in storage, and Amplify (the boundary) hands out a token.
  beforeEach(() => {
    localStorage.setItem('CognitoIdentityServiceProvider.test.LastAuthUser', 'anna.keller');
    vi.mocked(fetchAuthSession).mockResolvedValue({
      tokens: { idToken: { toString: () => 'id-token-of-anna' } },
    } as never);
  });
  afterEach(() => {
    localStorage.clear();
    vi.mocked(fetchAuthSession).mockReset();
  });

  it('should_listActivePartnersWithoutAuth_when_listPublicPartners', async () => {
    api.on('listPartners', () => page([gold], 0, 100));

    await expect(partnerApi.listPublicPartners()).resolves.toEqual(page([gold], 0, 100));

    const [{ query, request }] = api.calls('listPartners');
    expect(Object.fromEntries(query)).toEqual({
      page: '0',
      size: '100',
      include: 'company',
      filter: 'isActive:true',
    });
    // The Skip-Auth marker never leaves the browser, and neither does the token.
    expect(request.headers.has('Skip-Auth')).toBe(false);
    expect(request.headers.has('Authorization')).toBe(false);
  });

  it('should_returnCompany_when_getMyPartnerCompany (signed in, with the token)', async () => {
    api.on('getMyPartnerCompany', () => ({ companyName: 'mobiliar' }));

    await expect(partnerApi.getMyPartnerCompany()).resolves.toEqual({ companyName: 'mobiliar' });
    // Positive control for the test above: a normal call carries the token.
    expect(api.calls('getMyPartnerCompany')[0].request.headers.get('Authorization')).toBe(
      'Bearer id-token-of-anna'
    );
  });

  it('should_passErrorThrough_when_getMyPartnerCompanyFails', async () => {
    api.on('getMyPartnerCompany', () => problem(404, 'No partner company'));

    await expect(partnerApi.getMyPartnerCompany()).rejects.toMatchObject({
      response: { status: 404 },
    });
  });
});
