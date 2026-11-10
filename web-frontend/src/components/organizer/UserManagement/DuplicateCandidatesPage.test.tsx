/**
 * Organizers work through the probable duplicate profiles: compare and merge (the existing merge
 * dialog, preset with the pair) or mark a pair as "not a duplicate".
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { DuplicateCandidatesPage } from './DuplicateCandidatesPage';
import * as api from '@/services/api/userManagementApi';
import type { DuplicateCandidate } from '@/services/api/userManagementApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${JSON.stringify(opts)}` : key,
  }),
}));

vi.mock('@/services/api/userManagementApi', () => ({
  listDuplicateCandidates: vi.fn(),
  dismissDuplicateCandidate: vi.fn(),
}));

const dialogProps = vi.fn();
vi.mock('./MergeProfileDialog', () => ({
  MergeProfileDialog: (props: Record<string, unknown>) => {
    dialogProps(props);
    return props.open ? <div data-testid="merge-dialog-stub" /> : null;
  },
}));

const profile = (username: string, hasLogin: boolean, placeholderAddress = false) => ({
  username,
  firstName: 'Anna',
  lastName: 'Muster',
  email: `${username}@example.com`,
  companyId: 'acme',
  hasLogin,
  placeholderAddress,
  createdAt: '2025-03-01T09:00:00Z',
});

const pairs: DuplicateCandidate[] = [
  {
    rule: 'SAME_NAME',
    confidence: 'HIGH',
    organizerCanMerge: true,
    target: profile('anna.muster.2', true),
    source: profile('anna.muster', false, true),
  },
  {
    rule: 'SAME_NAME',
    confidence: 'MEDIUM',
    organizerCanMerge: false,
    target: profile('markus.maeder', true),
    source: profile('markus.maeder.2', true),
  },
  {
    rule: 'SAME_ADDRESS_NAME',
    confidence: 'LOW',
    organizerCanMerge: true,
    target: profile('tom.klingl', false),
    source: profile('thomas.klingl', false),
  },
];

const renderPage = () =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <MemoryRouter>
        <DuplicateCandidatesPage />
      </MemoryRouter>
    </QueryClientProvider>
  );

describe('DuplicateCandidatesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.listDuplicateCandidates).mockResolvedValue(pairs);
  });

  it('lists every pair with rule, confidence and both profiles', async () => {
    renderPage();

    const rows = await screen.findAllByTestId('duplicate-pair');
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByText('anna.muster.2@example.com')).toBeInTheDocument();
    expect(within(rows[0]).getByText('anna.muster@example.com')).toBeInTheDocument();
    expect(within(rows[0]).getByText('duplicates.confidence.HIGH')).toBeInTheDocument();
    expect(within(rows[0]).getByText('duplicates.placeholder')).toBeInTheDocument();
    expect(screen.getByTestId('duplicates-count')).toHaveTextContent('"count":3');
  });

  it('filters by confidence', async () => {
    renderPage();
    await screen.findAllByTestId('duplicate-pair');

    await userEvent.click(screen.getByRole('button', { name: 'duplicates.confidence.HIGH' }));

    expect(screen.getAllByTestId('duplicate-pair')).toHaveLength(1);
  });

  it('opens the merge dialog preset with the pair, keeping the suggested target', async () => {
    renderPage();
    const rows = await screen.findAllByTestId('duplicate-pair');

    await userEvent.click(within(rows[0]).getByTestId('duplicate-merge'));

    expect(screen.getByTestId('merge-dialog-stub')).toBeInTheDocument();
    expect(dialogProps).toHaveBeenLastCalledWith(
      expect.objectContaining({
        open: true,
        username: 'anna.muster.2',
        otherUsername: 'anna.muster',
        stayOnPage: true,
      })
    );
  });

  it('two logins: no merge button, the hint says only the person can merge', async () => {
    renderPage();
    const rows = await screen.findAllByTestId('duplicate-pair');

    expect(within(rows[1]).queryByTestId('duplicate-merge')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText('duplicates.bothLogin')).toBeInTheDocument();
  });

  it('"not a duplicate" dismisses the pair', async () => {
    vi.mocked(api.dismissDuplicateCandidate).mockResolvedValue();
    renderPage();
    const rows = await screen.findAllByTestId('duplicate-pair');

    await userEvent.click(within(rows[2]).getByTestId('duplicate-dismiss'));

    await waitFor(() =>
      expect(api.dismissDuplicateCandidate).toHaveBeenCalledWith('tom.klingl', 'thomas.klingl')
    );
    await waitFor(() => expect(screen.getAllByTestId('duplicate-pair')).toHaveLength(2));
  });

  it('says so when there are no candidates', async () => {
    vi.mocked(api.listDuplicateCandidates).mockResolvedValue([]);
    renderPage();

    expect(await screen.findByTestId('duplicates-empty')).toBeInTheDocument();
  });
});
