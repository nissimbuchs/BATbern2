/**
 * PartnerList search: the search term from the store goes to the backend, and every
 * partner the backend returns is shown. The list used to filter the loaded page in the browser,
 * so a partner on another page, or one matched by an umlaut spelling of its display name, was
 * missing. Real hook, store and service; the MSW harness answers the HTTP call.
 */
import { describe, it, expect, afterEach, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { api } from '@/test/msw/api';
import { PartnerList } from '../PartnerList';
import { usePartnerStore } from '@/stores/partnerStore';
import { aPartner } from '@/test/fixtures';

beforeEach(() => {
  usePartnerStore.setState({ searchQuery: 'mueller', page: 1 });
  api.on('listPartners', () => ({
    // Matched by the backend through the display name "Müller Bau AG": neither the slug nor
    // the query "mueller" contains the other, so a browser-side filter would drop it.
    data: [
      aPartner({
        companyName: 'mullerbau',
        partnershipLevel: 'GOLD',
        isActive: true,
        partnershipStartDate: '2023-01-15',
        company: {
          companyName: 'mullerbau',
          name: 'mullerbau',
          displayName: 'Müller Bau AG',
          logoUrl: null,
          industry: null,
          website: null,
        },
      }),
    ],
    metadata: {
      page: 1,
      limit: 20,
      totalItems: 21,
      totalPages: 2,
      hasNext: false,
      hasPrev: true,
    },
  }));
});
afterEach(() => {
  usePartnerStore.getState().resetFilters();
});

const renderList = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter>
        <PartnerList />
      </MemoryRouter>
    </QueryClientProvider>
  );

describe('PartnerList search', () => {
  it('sends the search term with the page and shows every partner the backend matched', async () => {
    renderList();

    expect(await screen.findByText('mullerbau')).toBeInTheDocument();
    expect(screen.getAllByTestId('partner-card')).toHaveLength(1);
    await waitFor(() => expect(api.calls('listPartners')).toHaveLength(1));
    const { query } = api.calls('listPartners')[0];
    expect(query.get('search')).toBe('mueller');
    expect(query.get('page')).toBe('1');
  });
});
