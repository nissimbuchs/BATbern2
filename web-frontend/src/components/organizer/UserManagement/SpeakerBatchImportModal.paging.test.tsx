/**
 * SpeakerBatchImportModal reads every speaker for its duplicate check page by page at the spec
 * maximum (17.0c1). It used to send `limit=1000`, which GET /users refuses (maximum 100); the MSW
 * harness fails the test on such a request. Real component, hooks and services.
 */
import { describe, it, expect } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { api } from '@/test/msw/api';
import { SpeakerBatchImportModal } from './SpeakerBatchImportModal';

describe('SpeakerBatchImportModal duplicate check', () => {
  it('reads the speakers at limit=100 when opened, never above the spec maximum', async () => {
    api.on('listUsers', ({ query }) => {
      const page = Number(query.get('page'));
      return {
        data: [],
        pagination: {
          page,
          limit: 100,
          totalItems: 150,
          totalPages: 2,
          hasNext: page < 2,
          hasPrev: page > 1,
        },
      };
    });

    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <SpeakerBatchImportModal open onClose={() => {}} />
      </QueryClientProvider>
    );

    await waitFor(() => expect(api.calls('listUsers')).toHaveLength(2));
    expect(api.calls('listUsers').map((c) => [c.query.get('page'), c.query.get('limit')])).toEqual([
      ['1', '100'],
      ['2', '100'],
    ]);
    expect(JSON.parse(api.calls('listUsers')[0].query.get('filter') ?? '{}')).toEqual({
      role: 'SPEAKER',
    });
    expect(api.takeViolations()).toEqual([]);
  });
});
