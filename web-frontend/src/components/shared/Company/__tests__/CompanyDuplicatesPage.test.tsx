/**
 * #1251 story 6: organizers work through the candidate duplicate companies: compare and merge
 * (the merge dialog, preset with the pair) or mark a pair as "not a duplicate". The network is the
 * MSW harness, i18n the real `en`.
 */
import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { CompanyDuplicatesPage } from '@/components/shared/Company/CompanyDuplicatesPage';
import { renderWithProviders } from '@/test/test-utils';
import { api } from '@/test/msw/api';
import type { CompanyDuplicateCandidate } from '@/services/api/companyMergeApi';

type Side = CompanyDuplicateCandidate['target'];

const side = (name: string, displayName: string, overrides: Partial<Side> = {}): Side => ({
  name,
  displayName,
  userCount: 0,
  registrationCount: 0,
  isPartner: false,
  createdAt: '2025-03-01T09:00:00Z',
  createdBy: 'system',
  ...overrides,
});

const pairs: CompanyDuplicateCandidate[] = [
  {
    rule: 'SAME_DISPLAY_NAME',
    confidence: 'HIGH',
    organizerCanMerge: true,
    target: side('post', 'Die Post', {
      userCount: 12,
      registrationCount: 30,
      isPartner: true,
      website: 'https://www.post.ch',
    }),
    source: side('diepost', 'Die Post', { userCount: 2, registrationCount: 5 }),
  },
  {
    rule: 'SAME_WEBSITE',
    confidence: 'HIGH',
    organizerCanMerge: false,
    target: side('sbb', 'SBB', { isPartner: true }),
    source: side('sbbcffffs', 'SBB CFF FFS', { isPartner: true }),
  },
  {
    rule: 'SLUG_PREFIX',
    confidence: 'LOW',
    organizerCanMerge: true,
    target: side('puzzle', 'Puzzle'),
    source: side('puzzleitc', 'Puzzle ITC'),
  },
];

const renderPage = () => renderWithProviders(<CompanyDuplicatesPage />);

describe('CompanyDuplicatesPage', () => {
  it('should_listEveryPairWithRuleConfidenceAndBothSides', async () => {
    api.on('listCompanyDuplicateCandidates', () => pairs);
    renderPage();

    const rows = await screen.findAllByTestId('company-duplicate-pair');
    expect(rows).toHaveLength(3);
    const first = within(rows[0]);
    expect(first.getByText('Same name')).toBeInTheDocument();
    expect(first.getByText('Very likely')).toBeInTheDocument();
    expect(first.getByText('post')).toBeInTheDocument();
    expect(first.getByText('diepost')).toBeInTheDocument();
    expect(first.getByText('People: 12')).toBeInTheDocument();
    expect(first.getByText('Registrations: 5')).toBeInTheDocument();
    expect(first.getByText('Partner')).toBeInTheDocument();
    expect(first.getByText('https://www.post.ch')).toBeInTheDocument();
    expect(screen.getByTestId('company-duplicates-count')).toHaveTextContent('Pairs: 3');
  });

  it('should_showOnlyThatConfidence_when_filtered', async () => {
    api.on('listCompanyDuplicateCandidates', () => pairs);
    renderPage();
    await screen.findAllByTestId('company-duplicate-pair');

    await userEvent.click(screen.getByRole('button', { name: 'Hint only' }));

    const rows = screen.getAllByTestId('company-duplicate-pair');
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('puzzleitc')).toBeInTheDocument();
  });

  it('should_offerNoMerge_when_bothArePartners', async () => {
    api.on('listCompanyDuplicateCandidates', () => pairs);
    renderPage();
    const rows = await screen.findAllByTestId('company-duplicate-pair');

    expect(within(rows[0]).getByTestId('company-duplicate-merge')).toBeInTheDocument();
    expect(within(rows[1]).queryByTestId('company-duplicate-merge')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText(/two partner companies cannot be merged/)).toBeInTheDocument();
  });

  it('should_dismissThePair_when_notADuplicate', async () => {
    api.on('listCompanyDuplicateCandidates', () => pairs);
    renderPage();
    const rows = await screen.findAllByTestId('company-duplicate-pair');

    await userEvent.click(within(rows[2]).getByTestId('company-duplicate-dismiss'));

    await waitFor(() => expect(screen.getAllByTestId('company-duplicate-pair')).toHaveLength(2));
    expect(api.calls('dismissCompanyDuplicateCandidate')[0].body).toEqual({
      nameA: 'puzzle',
      nameB: 'puzzleitc',
    });
    expect(screen.queryByText('puzzleitc')).not.toBeInTheDocument();
  });

  it('should_openThePresetMergeAndReloadTheList_when_merged', async () => {
    api.on('listCompanyDuplicateCandidates', () => pairs);
    renderPage();
    const rows = await screen.findAllByTestId('company-duplicate-pair');

    await userEvent.click(within(rows[0]).getByTestId('company-duplicate-merge'));

    await screen.findByTestId('merge-company-target');
    const preview = api.calls('getCompanyMergePreview')[0];
    expect(preview.params.name).toBe('post');
    expect(preview.query.get('source')).toBe('diepost');

    const confirm = screen.getByTestId('merge-company-confirm');
    await waitFor(() => expect(confirm).toBeEnabled());
    await userEvent.click(confirm);
    await screen.findByTestId('merge-company-success');

    expect(api.calls('mergeCompany')[0].body).toEqual({ sourceName: 'diepost' });
    await waitFor(() => expect(api.calls('listCompanyDuplicateCandidates')).toHaveLength(2));
  });

  it('should_sayNoCandidates_when_listEmpty', async () => {
    api.on('listCompanyDuplicateCandidates', () => []);
    renderPage();

    expect(await screen.findByTestId('company-duplicates-empty')).toHaveTextContent(
      'No possible duplicate companies found.'
    );
  });

  it('should_explainAndOfferRetry_when_countsIncomplete', async () => {
    api.on('listCompanyDuplicateCandidates', () =>
      HttpResponse.json(
        {
          status: 502,
          error: 'Bad Gateway',
          errorCode: 'DUPLICATE_COUNTS_INCOMPLETE',
          message: 'x',
          timestamp: '2026-11-20T10:30:00Z',
        },
        { status: 502 }
      )
    );
    renderPage();

    expect(
      await screen.findByText('Not every service delivered its numbers. Please try again.')
    ).toBeInTheDocument();
    api.on('listCompanyDuplicateCandidates', () => pairs);
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findAllByTestId('company-duplicate-pair')).toHaveLength(3);
  });

  it('should_keepThePairAndSaySo_when_dismissFails', async () => {
    api.on('listCompanyDuplicateCandidates', () => pairs);
    api.on('dismissCompanyDuplicateCandidate', () =>
      HttpResponse.json({ message: 'not found' }, { status: 404 })
    );
    renderPage();
    const rows = await screen.findAllByTestId('company-duplicate-pair');

    await userEvent.click(within(rows[2]).getByTestId('company-duplicate-dismiss'));

    expect(
      await screen.findByText('The pair could not be marked. Please try again.')
    ).toBeInTheDocument();
    expect(screen.getAllByTestId('company-duplicate-pair')).toHaveLength(3);
  });
});
