/**
 * useTopicSessionData (Story 10.4; Epic 17, entry 8)
 *
 * The hook runs with the real blobTopicService, partner APIs and apiClient against the MSW
 * harness: EMS session data, partner-coordination topics and logos, and the EMS classification
 * are each checked against their spec. The partner topics are composed in the browser now, with
 * the shape EMS gave them before: grouped by company ("Unknown" without one), logo or null,
 * cluster per title, and each partner-side failure degrading to what EMS answered then.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import type { TopicDTO } from '@/services/api/partnerTopicsApi';
import type { PartnerResponse } from '@/services/api/partnerApi';
import { useTopicSessionData } from './useTopicSessionData';

const EVENT = 'BATbern57';

const topic = (overrides: Partial<TopicDTO>): TopicDTO => ({
  id: '3f1c2a9e-8b4d-4c6e-9a7f-1d2e3f4a5b6c',
  title: 'AI in Operations',
  description: null,
  suggestedByCompany: 'aity',
  voteCount: 2,
  currentPartnerHasVoted: false,
  status: 'PROPOSED',
  plannedEvent: null,
  createdAt: '2026-02-01T10:00:00Z',
  source: 'PARTNER',
  ...overrides,
});

const partner = (companyName: string, logoUrl: string | null): PartnerResponse => ({
  companyName,
  partnershipLevel: 'GOLD',
  partnershipStartDate: '2020-01-01',
  partnershipEndDate: null,
  isActive: true,
  company: {
    companyName,
    name: companyName,
    displayName: companyName.toUpperCase(),
    logoUrl,
    industry: null,
    website: null,
  },
  contacts: null,
});

const SBB_LOGO = 'https://cdn.batbern.ch/logos/2026/companies/sbb/logo.svg';

const sessionData = {
  pastEvents: [{ eventNumber: 57, topicName: 'Security', cluster: 'SECURITY' }],
  organizerBacklog: [{ title: 'Return of the Monolith', topicCode: 'rotm', stalenessScore: 100 }],
  trendingTopics: ['AI Agents'],
};

const problem = {
  error: 'INTERNAL',
  message: 'boom',
  status: 500,
  timestamp: '2026-11-11T00:00:00Z',
};

describe('useTopicSessionData', () => {
  let queryClient: QueryClient;

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const load = async () => {
    const view = renderHook(() => useTopicSessionData(EVENT), { wrapper });
    await waitFor(() => expect(view.result.current.isSuccess).toBe(true));
    return view.result.current.data!;
  };

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
    api.on('getTopicSessionData', () => sessionData);
    api.on('partners-api/listTopics', () => [
      topic({
        id: '00000000-0000-4000-8000-000000000001',
        title: 'Security: Zerotrust',
        suggestedByCompany: 'sbb',
        voteCount: 3,
      }),
      topic({
        id: '00000000-0000-4000-8000-000000000002',
        title: 'AI in Operations',
        suggestedByCompany: 'aity',
      }),
      topic({
        id: '00000000-0000-4000-8000-000000000003',
        title: 'Impact AI für SW Engineers',
        suggestedByCompany: 'sbb',
        createdAt: null,
      }),
      topic({
        id: '00000000-0000-4000-8000-000000000004',
        title: 'Workplace Psychology',
        suggestedByCompany: null,
        source: 'COMMUNITY',
      }),
      topic({
        id: '00000000-0000-4000-8000-000000000005',
        title: 'AI in Operations',
        suggestedByCompany: 'sbb',
        voteCount: 0,
      }),
    ]);
    api.on('listPartners', () => ({
      data: [partner('sbb', SBB_LOGO), partner('aity', null)],
      metadata: {
        page: 0,
        limit: 200,
        totalItems: 2,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    }));
    api.on('classifyTopics', ({ body }) => ({
      classifications: (body?.topics ?? []).map((t) => ({
        topic: t,
        cluster: /zerotrust/i.test(t) ? 'SECURITY' : /\bAI\b/.test(t) ? 'AI_ML' : 'BUSINESS_OTHER',
        similarityScore: 0.85,
        relatedPastEventNumbers: [],
      })),
    }));
  });

  afterEach(() => {
    queryClient.clear();
  });

  it('groups partner topics by company with logo and cluster, next to the EMS data', async () => {
    const data = await load();

    expect(data.pastEvents).toEqual(sessionData.pastEvents);
    expect(data.organizerBacklog).toEqual(sessionData.organizerBacklog);
    expect(data.trendingTopics).toEqual(['AI Agents']);
    expect(data.partnerTopics).toEqual([
      {
        companyName: 'sbb',
        logoUrl: SBB_LOGO,
        topics: [
          {
            title: 'Security: Zerotrust',
            cluster: 'SECURITY',
            voteCount: 3,
            createdAt: '2026-02-01T10:00:00Z',
          },
          {
            title: 'Impact AI für SW Engineers',
            cluster: 'AI_ML',
            voteCount: 2,
            createdAt: '1970-01-01T00:00:00Z',
          },
          {
            title: 'AI in Operations',
            cluster: 'AI_ML',
            voteCount: 0,
            createdAt: '2026-02-01T10:00:00Z',
          },
        ],
      },
      {
        companyName: 'aity',
        logoUrl: null,
        topics: [
          {
            title: 'AI in Operations',
            cluster: 'AI_ML',
            voteCount: 2,
            createdAt: '2026-02-01T10:00:00Z',
          },
        ],
      },
      {
        companyName: 'Unknown',
        logoUrl: null,
        topics: [
          {
            title: 'Workplace Psychology',
            cluster: 'BUSINESS_OTHER',
            voteCount: 2,
            createdAt: '2026-02-01T10:00:00Z',
          },
        ],
      },
    ]);
  });

  it('reads logos with include=company&size=200 and classifies each title once', async () => {
    await load();

    const [logos] = api.calls('listPartners');
    expect(logos.query.get('include')).toBe('company');
    expect(logos.query.get('size')).toBe('200');
    expect(api.calls('classifyTopics')).toHaveLength(1);
    expect(api.calls('classifyTopics')[0].body).toEqual({
      topics: [
        'Security: Zerotrust',
        'AI in Operations',
        'Impact AI für SW Engineers',
        'Workplace Psychology',
      ],
    });
  });

  it('makes no classify call when there are no partner topics', async () => {
    api.on('partners-api/listTopics', () => []);

    const data = await load();

    expect(data.partnerTopics).toEqual([]);
    expect(api.calls('classifyTopics')).toHaveLength(0);
  });

  it('shows no partner topics, but the rest of the canvas, when the topic list fails', async () => {
    api.on('partners-api/listTopics', () => HttpResponse.json(problem, { status: 500 }));

    const data = await load();

    expect(data.partnerTopics).toEqual([]);
    expect(data.pastEvents).toEqual(sessionData.pastEvents);
  });

  it('shows no partner topics when partner-coordination is unreachable', async () => {
    api.on('partners-api/listTopics', () => HttpResponse.error());

    const data = await load();

    expect(data.partnerTopics).toEqual([]);
    expect(data.trendingTopics).toEqual(['AI Agents']);
  });

  it('keeps the topics without logos when the logo list fails', async () => {
    api.on('listPartners', () => HttpResponse.json(problem, { status: 500 }));

    const data = await load();

    expect(data.partnerTopics.map((g) => [g.companyName, g.logoUrl])).toEqual([
      ['sbb', null],
      ['aity', null],
      ['Unknown', null],
    ]);
    expect(data.partnerTopics[0].topics[0].cluster).toBe('SECURITY');
  });

  it('shows no partner topics when the classification fails', async () => {
    api.on('classifyTopics', () => HttpResponse.json(problem, { status: 500 }));

    const data = await load();

    expect(data.partnerTopics).toEqual([]);
    expect(data.organizerBacklog).toEqual(sessionData.organizerBacklog);
  });

  it('fails when the EMS session data fails, as before', async () => {
    api.on('getTopicSessionData', () => HttpResponse.json(problem, { status: 500 }));

    const view = renderHook(() => useTopicSessionData(EVENT), { wrapper });

    await waitFor(() => expect(view.result.current.isError).toBe(true));
  });
});
