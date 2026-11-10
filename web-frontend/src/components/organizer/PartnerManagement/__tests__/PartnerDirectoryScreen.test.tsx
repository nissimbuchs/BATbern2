/**
 * PartnerDirectoryScreen (Story 2.8.1, AC1 + AC5)
 *
 * The real tree runs against the MSW harness (17.0c2): `usePartners` / `usePartnerStatistics`,
 * the partner API client, both Zustand stores, the five children (stats, search, filters, list,
 * create/edit modal) and en i18n. `GET /partners` and `GET /partners/statistics` are checked
 * against partners-api and answered with typed bodies; the test reads what went over the wire.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, delay } from 'msw';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { renderWithAuth, signedInAs } from '@/test/auth';
import type { PartnerResponse } from '@/services/api/partnerApi';
import { usePartnerStore } from '@/stores/partnerStore';
import { usePartnerModalStore } from '@/stores/partnerModalStore';
import { PartnerDirectoryScreen } from '../PartnerDirectoryScreen';
import { aPartner } from '@/test/fixtures';

type PartnerList = ResponseBody<'listPartners', 200>;
type Statistics = ResponseBody<'getPartnerStatistics', 200>;

const gold: PartnerResponse = aPartner({
  companyName: 'mobiliar',
  partnershipLevel: 'GOLD',
  partnershipStartDate: '2020-01-01',
  isActive: true,
  company: {
    companyName: 'mobiliar',
    name: 'mobiliar',
    displayName: 'Die Mobiliar',
    logoUrl: null,
    industry: 'Insurance',
    website: null,
  },
});

const page = (data: PartnerResponse[]): PartnerList => ({
  data,
  metadata: {
    page: 0,
    limit: 20,
    totalItems: data.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
  },
});

const statistics: Statistics = {
  totalPartners: 17,
  activePartners: 14,
  tierCounts: { STRATEGIC: 1, PLATINUM: 2, GOLD: 5, SILVER: 4, BRONZE: 5 },
};

const renderScreen = () =>
  renderWithAuth(<PartnerDirectoryScreen />, { auth: signedInAs('organizer') });

/** The query of the latest `GET /partners`. */
const lastListQuery = () => api.calls('listPartners').at(-1)!.query;

describe('PartnerDirectoryScreen - Main Screen Component', () => {
  beforeEach(() => {
    localStorage.clear();
    usePartnerStore.setState({
      filters: { tier: 'all', status: 'all' },
      viewMode: 'grid',
      searchQuery: '',
      sortBy: 'name',
      sortOrder: 'asc',
      page: 0,
    });
    usePartnerModalStore.getState().closeModal();
    api.on('listPartners', () => page([gold]));
    api.on('getPartnerStatistics', () => statistics);
  });

  describe('AC1 & AC5 Tests: Component Rendering and Data Loading', () => {
    it('should_renderPartnerDirectoryScreen_when_componentMounts', async () => {
      renderScreen();

      expect(screen.getByTestId('partner-directory-screen')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Partner Directory' })).toBeInTheDocument();
      expect(await screen.findByText(gold.companyName)).toBeInTheDocument();
    });

    it('should_loadPartnersOnMount_when_hookCalled', async () => {
      renderScreen();

      expect(await screen.findByText(gold.companyName)).toBeInTheDocument();
      // Screen and PartnerList call usePartners with the same arguments: one shared query.
      expect(api.calls('listPartners')).toHaveLength(1);
      const query = lastListQuery();
      expect(query.get('page')).toBe('0');
      expect(query.get('size')).toBe('20');
      expect(query.get('include')).toBe('company,contacts');
      expect(query.has('filter')).toBe(false);
      expect(query.has('search')).toBe(false);
      // The screen's own result announcement reads the same response.
      expect(screen.getByRole('status', { name: 'Search partners' })).toHaveTextContent(
        '1 partner found'
      );
    });

    it('should_updatePartnersOnFilterChange_when_filtersChanged', async () => {
      const user = userEvent.setup();
      renderScreen();
      expect(await screen.findByText(gold.companyName)).toBeInTheDocument();
      expect(lastListQuery().has('filter')).toBe(false);

      // Drive the real tier filter.
      await user.click(screen.getByRole('combobox', { name: 'Partnership Tier' }));
      await user.click(await screen.findByRole('option', { name: /Gold/ }));

      await waitFor(() => expect(lastListQuery().get('filter')).toBe('partnershipLevel:GOLD'));
      expect(lastListQuery().get('page')).toBe('0');
      expect(usePartnerStore.getState().filters.tier).toBe('GOLD');
      expect(screen.getByRole('status', { name: 'Filters updated' })).toHaveTextContent(
        'Filtered by tier: GOLD'
      );
    });

    it.todo('should_navigateToDetail_when_partnerClicked - tested in PartnerCard.test.tsx');
  });

  describe('Layout and Component Integration Tests', () => {
    it('should_renderOverviewStats_when_mounted', async () => {
      renderScreen();

      const total = await screen.findByTestId('stats-total-partners');
      await waitFor(() => expect(total).toHaveTextContent('17'));
      expect(screen.getByTestId('stats-active-partners')).toHaveTextContent('14');
      expect(screen.getByTestId('stats-tier-distribution')).toHaveTextContent('Gold: 5');
      expect(api.calls('getPartnerStatistics')).toHaveLength(1);
    });

    it('should_renderSearchComponent_when_mounted', async () => {
      const user = userEvent.setup();
      renderScreen();

      const search = screen.getByRole('textbox', { name: 'Search partners' });
      expect(search).toBeInTheDocument();
      expect(await screen.findByText(gold.companyName)).toBeInTheDocument();

      // The real search box reaches the backend (after its 300 ms debounce).
      await user.type(search, 'mobi');
      await waitFor(() => expect(lastListQuery().get('search')).toBe('mobi'));
    });

    it('should_renderFiltersComponent_when_mounted', () => {
      renderScreen();

      expect(screen.getByTestId('tier-filter-select')).toBeInTheDocument();
      expect(screen.getByTestId('status-filter-select')).toBeInTheDocument();
      expect(screen.getByTestId('reset-filters-button')).toBeInTheDocument();
    });

    it('should_renderPartnerList_when_mounted', async () => {
      renderScreen();

      const grid = await screen.findByTestId('partner-grid');
      const cards = within(grid).getAllByTestId('partner-card');
      expect(cards).toHaveLength(1);
      expect(cards[0]).toHaveTextContent(gold.companyName);
      expect(cards[0]).toHaveTextContent('Insurance');
    });

    it('should_renderAddPartnerButton_when_mounted', () => {
      renderScreen();

      expect(screen.getByTestId('add-partner-button')).toHaveTextContent('Add Partner');
    });

    it('should_renderViewModeToggle_when_mounted', async () => {
      const user = userEvent.setup();
      renderScreen();

      expect(screen.getByTestId('view-mode-toggle')).toBeInTheDocument();
      expect(await screen.findByTestId('partner-grid')).toBeInTheDocument();

      // The real toggle switches the real list to the single-column view.
      await user.click(screen.getByRole('button', { name: 'List view' }));
      expect(await screen.findByTestId('partner-list-list')).toBeInTheDocument();
      expect(screen.queryByTestId('partner-grid')).not.toBeInTheDocument();
      expect(usePartnerStore.getState().viewMode).toBe('list');
    });

    it('should_renderSortSelect_when_mounted', () => {
      renderScreen();

      expect(screen.getByTestId('partner-sort-select')).toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: 'Sort by' })).toHaveTextContent('Company Name');
    });

    it('should_listHighestTierFirst_when_tierSortChosen', async () => {
      const user = userEvent.setup();
      renderScreen();
      await screen.findByText(gold.companyName);
      expect(lastListQuery().get('sort')).toBe('name');

      await user.click(screen.getByRole('combobox', { name: 'Sort by' }));
      // Only the sorts the backend implements are offered.
      expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([
        'Company Name',
        'Partnership Tier',
      ]);
      await user.click(screen.getByRole('option', { name: 'Partnership Tier' }));

      await waitFor(() => expect(lastListQuery().get('sort')).toBe('-tier'));
    });
  });

  describe('Loading and Error States', () => {
    it('should_showLoadingState_when_partnersLoading', async () => {
      api.on('listPartners', async () => {
        await delay('infinite');
        return page([]);
      });
      renderScreen();

      expect(await screen.findByTestId('partner-list-loading')).toBeInTheDocument();
      expect(screen.getByRole('status', { name: 'Loading partners' })).toHaveTextContent(
        'Loading partners...'
      );
      expect(screen.queryByTestId('partner-card')).not.toBeInTheDocument();
    });

    it('should_showErrorState_when_partnersFetchFails', async () => {
      api.on('listPartners', () =>
        HttpResponse.json(
          { error: 'INTERNAL_ERROR', message: 'Failed to fetch partners', status: 500 },
          { status: 500 }
        )
      );
      renderScreen();

      expect(await screen.findByTestId('partner-list-error')).toBeInTheDocument();
      expect(screen.queryByTestId('partner-card')).not.toBeInTheDocument();
      // Positive control: the rest of the screen still renders.
      expect(screen.getByTestId('add-partner-button')).toBeInTheDocument();
    });
  });

  describe('Toolbar Actions', () => {
    it('should_openCreateModal_when_addButtonClicked', async () => {
      const user = userEvent.setup();
      renderScreen();

      const addButton = screen.getByTestId('add-partner-button');
      expect(addButton).not.toBeDisabled();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      await user.click(addButton);

      expect(usePartnerModalStore.getState()).toMatchObject({ isOpen: true, mode: 'create' });
      const dialog = await screen.findByRole('dialog');
      expect(within(dialog).getByText('Create Partnership')).toBeInTheDocument();
    });
  });

  it('keeps the filter announcement in step with the store', async () => {
    renderScreen();
    expect(await screen.findByText(gold.companyName)).toBeInTheDocument();

    act(() => usePartnerStore.getState().setFilters({ status: 'active' }));

    await waitFor(() => expect(lastListQuery().get('filter')).toBe('isActive:true'));
    expect(screen.getByRole('status', { name: 'Filters updated' })).toHaveTextContent(
      'Status: active'
    );
  });
});
