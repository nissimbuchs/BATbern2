/**
 * CompanyManagementScreen: the Companies tab as the app routes it (`/organizer/companies/*`), with
 * its real hooks and services. The network is the MSW harness, auth the real `AuthContext`, i18n
 * the real `en` (moved off own-code mocks with #1251 story 6, touch rule).
 */
import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import CompanyManagementScreen from '@/components/shared/Company/CompanyManagementScreen';
import { renderWithAuth, signedInAs } from '@/test/auth';
import { api } from '@/test/msw/api';
import type { UserRole } from '@/types/auth';

const company = {
  name: 'unic',
  displayName: 'Unic',
  isVerified: true,
  createdAt: '2024-01-15T10:00:00Z',
  updatedAt: '2024-10-14T15:30:00Z',
  createdBy: 'system',
};

const renderScreen = (path = '/organizer/companies', role: UserRole = 'organizer') =>
  renderWithAuth(
    <Routes>
      <Route path="/organizer/companies/*" element={<CompanyManagementScreen />} />
    </Routes>,
    { auth: signedInAs(role), initialEntries: [path] }
  );

describe('CompanyManagementScreen', () => {
  describe('list', () => {
    it('should_showTitleSearchFiltersViewToggleAndCreate_when_screenLoaded', async () => {
      renderScreen();

      expect(screen.getByRole('heading', { name: /company management/i })).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/search companies/i)).toBeInTheDocument();
      expect(screen.getByTestId('company-filters')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /grid view/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /list view/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /create company/i })).toBeInTheDocument();
      await waitFor(() => expect(api.calls('listCompanies')).toHaveLength(1));
    });

    it('should_sendTheSearchToTheBackend_when_typed', async () => {
      renderScreen();

      await userEvent.type(screen.getByPlaceholderText(/search companies/i), 'Müller');

      await waitFor(() =>
        expect(api.calls('listCompanies').some((c) => c.query.get('search') === 'Müller')).toBe(
          true
        )
      );
    });
  });

  describe('possible duplicates (#1251)', () => {
    it('should_openTheDuplicatesPage_when_buttonClicked', async () => {
      api.on('listCompanyDuplicateCandidates', () => []);
      renderScreen();

      await userEvent.click(screen.getByTestId('company-duplicates-button'));

      expect(
        await screen.findByRole('heading', { name: 'Possible duplicate companies' })
      ).toBeInTheDocument();
      expect(await screen.findByTestId('company-duplicates-empty')).toBeInTheDocument();
      // the list header (title, filters) belongs to the list only
      expect(screen.queryByTestId('company-duplicates-button')).not.toBeInTheDocument();
    });

    it('should_offerMergeOnTheDetailPage_when_organizer', async () => {
      api.on('getCompany', () => company);
      api.on('searchCompanies', () => [
        { name: 'unicag', displayName: 'Unic AG', isVerified: false },
      ]);
      renderScreen('/organizer/companies/unic');

      await userEvent.click(await screen.findByTestId('company-merge-button'));
      const dialog = await screen.findByTestId('merge-company-dialog');
      await userEvent.type(within(dialog).getByRole('combobox'), 'Unic');
      await userEvent.click(await screen.findByRole('option', { name: /Unic AG/ }));

      await within(dialog).findByTestId('merge-company-target');
      const preview = api.calls('getCompanyMergePreview')[0];
      expect(preview.params.name).toBe('unic');
      expect(preview.query.get('source')).toBe('unicag');
    });

    it('should_openTheCompanyThatStaysAndKeepTheResult_when_theOpenCompanyWasMergedIn', async () => {
      api.on('getCompany', ({ params }) =>
        params.name === 'unic' ? company : { ...company, name: 'unicag', displayName: 'Unic AG' }
      );
      api.on('searchCompanies', () => [
        { name: 'unicag', displayName: 'Unic AG', isVerified: false },
      ]);
      api.on('getCompanyMergePreview', ({ params, query }) => ({
        target: { name: params.name, isVerified: true, userCount: 1 },
        source: { name: query.get('source') ?? '', isVerified: true, userCount: 1 },
        moves: {},
        conflicts: {},
        blockers: [],
        fills: [],
        canMerge: true,
        reasons: [],
        canSwap: true,
      }));
      api.on('mergeCompany', ({ params, body }) => ({
        targetName: params.name,
        sourceName: body?.sourceName ?? '',
        moved: {},
        removed: {},
        filled: [],
      }));
      renderScreen('/organizer/companies/unic');

      await userEvent.click(await screen.findByTestId('company-merge-button'));
      const dialog = await screen.findByTestId('merge-company-dialog');
      await userEvent.type(within(dialog).getByRole('combobox'), 'Unic');
      await userEvent.click(await screen.findByRole('option', { name: /Unic AG/ }));
      await userEvent.click(await within(dialog).findByTestId('merge-company-swap'));
      await waitFor(() => expect(api.calls('getCompanyMergePreview')).toHaveLength(2));
      const confirm = within(dialog).getByTestId('merge-company-confirm');
      await waitFor(() => expect(confirm).toBeEnabled());
      await userEvent.click(confirm);

      expect(await screen.findByTestId('merge-company-success')).toHaveTextContent(
        'Merged: unic is now part of unicag.'
      );
      expect(
        await screen.findByRole('heading', { name: 'Unic AG', hidden: true })
      ).toBeInTheDocument(); // behind the open dialog
      expect(screen.getByTestId('merge-company-success')).toBeInTheDocument();
      expect(api.calls('mergeCompany')[0].params.name).toBe('unicag');
    });

    it('should_notOfferMerge_when_notOrganizer', async () => {
      api.on('getCompany', () => company);
      renderScreen('/organizer/companies/unic', 'speaker');

      expect(await screen.findByTestId('company-detail-view')).toBeInTheDocument();
      // positive control: the edit action of the same row is there
      expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
      expect(screen.queryByTestId('company-merge-button')).not.toBeInTheDocument();
    });
  });
});
