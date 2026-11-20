/**
 * #1251 story 6: the company merge dialog. Preview (both sides, what moves, collisions, fields
 * taken over), swap, refusal reasons, confirm, error codes; with a preset pair (duplicates list)
 * and with the company picker (detail page). The network is the MSW harness, i18n the real `en`.
 */
import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { MergeCompanyDialog } from '@/components/shared/Company/MergeCompanyDialog';
import { renderWithProviders } from '@/test/test-utils';
import { api } from '@/test/msw/api';
import type { CompanyMergePreview } from '@/services/api/companyMergeApi';

const preview = (overrides: Partial<CompanyMergePreview> = {}): CompanyMergePreview => ({
  target: { name: 'unic', displayName: 'Unic', isVerified: true, userCount: 12 },
  source: {
    name: 'unicag',
    displayName: 'Unic AG',
    website: 'https://www.unic.com',
    isVerified: false,
    userCount: 3,
  },
  moves: {
    'user_profiles.company_id': 3,
    'company_logo_requests.company_name': 0,
    'registrations.attendee_company_id': 4,
  },
  conflicts: { topic_votes: 1 },
  blockers: [],
  fills: ['website', 'logo'],
  canMerge: true,
  reasons: [],
  canSwap: true,
  ...overrides,
});

const problem = (status: number, errorCode: string) =>
  HttpResponse.json(
    { status, error: 'x', errorCode, message: errorCode, timestamp: '2026-11-20T10:30:00Z' },
    { status }
  );

const renderDialog = (props: Partial<React.ComponentProps<typeof MergeCompanyDialog>> = {}) =>
  renderWithProviders(
    <MergeCompanyDialog open onClose={vi.fn()} companyName="unic" otherName="unicag" {...props} />
  );

describe('MergeCompanyDialog', () => {
  it('should_showBothSidesMovesConflictsAndFills_when_previewLoads', async () => {
    api.on('getCompanyMergePreview', () => preview());
    renderDialog();

    const target = await screen.findByTestId('merge-company-target');
    expect(within(target).getByText('Unic')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('merge-company-source')).getByText('Unic AG')
    ).toBeInTheDocument();
    const moves = screen.getByTestId('merge-company-moves');
    expect(within(moves).getByText('People: 3')).toBeInTheDocument();
    expect(within(moves).getByText('Registrations: 4')).toBeInTheDocument();
    expect(within(moves).queryByText(/Logo requests/)).not.toBeInTheDocument();
    expect(screen.getByTestId('merge-company-conflicts')).toHaveTextContent('Topic votes: 1');
    expect(screen.getByTestId('merge-company-fills')).toHaveTextContent('Website, Logo');

    const call = api.calls('getCompanyMergePreview')[0];
    expect(call.params.name).toBe('unic');
    expect(call.query.get('source')).toBe('unicag');
  });

  it('should_previewTheOtherDirection_when_swapClicked', async () => {
    api.on('getCompanyMergePreview', ({ params }) =>
      params.name === 'unic'
        ? preview()
        : preview({ target: preview().source, source: preview().target })
    );
    renderDialog();

    await userEvent.click(await screen.findByTestId('merge-company-swap'));

    await waitFor(() => expect(api.calls('getCompanyMergePreview')).toHaveLength(2));
    const second = api.calls('getCompanyMergePreview')[1];
    expect(second.params.name).toBe('unicag');
    expect(second.query.get('source')).toBe('unic');
    await waitFor(() =>
      expect(
        within(screen.getByTestId('merge-company-target')).getByText('Unic AG')
      ).toBeInTheDocument()
    );
  });

  it('should_showTheReasonAndBlockConfirm_when_bothArePartners', async () => {
    api.on('getCompanyMergePreview', () =>
      preview({
        canMerge: false,
        canSwap: false,
        reasons: ['BOTH_PARTNERS'],
        blockers: ['MERGE_BLOCKED_BOTH_PARTNERS'],
      })
    );
    renderDialog();

    expect(
      await screen.findByText(/Two partner companies cannot be merged; merge the partners first/)
    ).toBeInTheDocument();
    expect(screen.getByTestId('merge-company-confirm')).toBeDisabled();
    expect(screen.queryByTestId('merge-company-swap')).not.toBeInTheDocument();
  });

  it('should_postTheMergeAndReport_when_confirmed', async () => {
    const onMerged = vi.fn();
    api.on('getCompanyMergePreview', () => preview());
    renderDialog({ onMerged, stayOnPage: true });

    const confirm = await screen.findByTestId('merge-company-confirm');
    await waitFor(() => expect(confirm).toBeEnabled());
    await userEvent.click(confirm);

    expect(await screen.findByTestId('merge-company-success')).toHaveTextContent(
      'Merged: unicag is now part of unic.'
    );
    const call = api.calls('mergeCompany')[0];
    expect(call.params.name).toBe('unic');
    expect(call.body).toEqual({ sourceName: 'unicag' });
    expect(onMerged).toHaveBeenCalledWith('unic');
  });

  it.each([
    [409, 'MERGE_NOT_ALLOWED', 'This merge is not allowed. Nothing was changed.'],
    [409, 'MERGE_BLOCKED_BOTH_PARTNERS', 'Both companies are partners. Nothing was changed.'],
    [409, 'MERGE_BLOCKED', 'A service refused the merge. Nothing was changed.'],
    [
      502,
      'MERGE_INCOMPLETE',
      'Not every service completed the merge. The other company still exists; merge again to finish it.',
    ],
  ])('should_explainTheRefusal_when_mergeAnswers%s%s', async (status, code, text) => {
    api.on('getCompanyMergePreview', () => preview());
    api.on('mergeCompany', () => problem(status, code));
    renderDialog();

    const confirm = await screen.findByTestId('merge-company-confirm');
    await waitFor(() => expect(confirm).toBeEnabled());
    await userEvent.click(confirm);

    expect(await screen.findByTestId('merge-company-error')).toHaveTextContent(text);
  });

  it('should_askToReload_when_previewIncomplete', async () => {
    api.on('getCompanyMergePreview', () => problem(502, 'MERGE_PREVIEW_INCOMPLETE'));
    renderDialog();

    expect(
      await screen.findByText('Not every service answered. Please open the preview again.')
    ).toBeInTheDocument();
  });

  it('should_previewThePickedCompany_when_noPairIsPreset', async () => {
    api.on('searchCompanies', () => [
      { name: 'unicag', displayName: 'Unic AG', isVerified: false },
    ]);
    api.on('getCompanyMergePreview', () => preview());
    renderDialog({ otherName: undefined });

    expect(api.calls('getCompanyMergePreview')).toHaveLength(0);
    await userEvent.type(screen.getByRole('combobox'), 'Unic');
    await userEvent.click(await screen.findByRole('option', { name: /Unic AG/ }));

    await screen.findByTestId('merge-company-source');
    expect(api.calls('getCompanyMergePreview')[0].query.get('source')).toBe('unicag');
  });

  it.each([
    ['SAME_COMPANY', 'This is the same company.'],
    [
      'TARGET_KEY_TOO_LONG',
      "The short name of the company that stays cannot be used in people's profiles. Merge in the other direction.",
    ],
    ['BLOCKED', 'A service refuses the merge (SOME_BLOCKER).'],
  ])('should_showTheReason_when_previewRefuses%s', async (reason, text) => {
    api.on('getCompanyMergePreview', () =>
      preview({ canMerge: false, reasons: [reason], blockers: ['SOME_BLOCKER'] })
    );
    renderDialog();

    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.getByTestId('merge-company-confirm')).toBeDisabled();
  });

  it('should_openTheCompanyThatStays_when_theOpenCompanyWasMergedIn', async () => {
    api.on('getCompanyMergePreview', ({ params }) =>
      params.name === 'unic'
        ? preview()
        : preview({ target: preview().source, source: preview().target })
    );
    api.on('mergeCompany', ({ params }) => ({
      targetName: params.name,
      sourceName: 'unic',
      moved: {},
      removed: {},
      filled: [],
    }));
    renderWithProviders(
      <Routes>
        <Route
          path="/organizer/companies/unic"
          element={
            <MergeCompanyDialog open onClose={vi.fn()} companyName="unic" otherName="unicag" />
          }
        />
        <Route path="/organizer/companies/unicag" element={<div>Page of unicag</div>} />
      </Routes>,
      { initialEntries: ['/organizer/companies/unic'] }
    );

    await userEvent.click(await screen.findByTestId('merge-company-swap'));
    await waitFor(() =>
      expect(
        within(screen.getByTestId('merge-company-target')).getByText('Unic AG')
      ).toBeInTheDocument()
    );
    const confirm = screen.getByTestId('merge-company-confirm');
    await waitFor(() => expect(confirm).toBeEnabled());
    await userEvent.click(confirm);

    expect(await screen.findByText('Page of unicag')).toBeInTheDocument();
    expect(api.calls('mergeCompany')[0].params.name).toBe('unicag');
    expect(api.calls('mergeCompany')[0].body).toEqual({ sourceName: 'unic' });
  });
});
