/**
 * #1237: the organizer decides a profile merge request in the Mail Center.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProfileMergeRequestPanel } from './ProfileMergeRequestPanel';
import * as api from '@/services/api/userManagementApi';
import type { ProfileMergeRequestView } from '@/services/api/userManagementApi';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${JSON.stringify(opts)}` : key,
  }),
}));

vi.mock('@/services/api/userManagementApi', () => ({
  getProfileMergeRequest: vi.fn(),
  approveProfileMergeRequest: vi.fn(),
  declineProfileMergeRequest: vi.fn(),
}));

const profile = (username: string, email: string, hasLogin: boolean) => ({
  username,
  firstName: 'Anna',
  lastName: 'Muster',
  email,
  hasLogin,
  roles: ['ATTENDEE'],
  additionalEmails: [],
});

const open: ProfileMergeRequestView = {
  requestId: 'r-1',
  status: 'OPEN',
  requesterUsername: 'anna.muster.2',
  sourceUsername: 'anna.muster',
  note: 'Firma X bis 2024',
  createdAt: '2026-11-06T20:00:00Z',
  preview: {
    target: profile('anna.muster.2', 'anna.private@example.com', true),
    source: profile('anna.muster', 'anna@oldco.example.com', false),
    moves: { 'registrations.attendee_username': 3 },
    conflicts: {},
    blockers: [],
    canMerge: true,
    reasons: [],
  },
} as unknown as ProfileMergeRequestView;

const renderPanel = (onDecided = vi.fn()) =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <ProfileMergeRequestPanel requestId="r-1" onDecided={onDecided} />
    </QueryClientProvider>
  );

describe('ProfileMergeRequestPanel (#1237)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should_showNoteAndBothProfiles', async () => {
    vi.mocked(api.getProfileMergeRequest).mockResolvedValue(open);
    renderPanel();

    expect(await screen.findByTestId('merge-request-note')).toHaveTextContent('Firma X bis 2024');
    const table = screen.getByTestId('merge-request-profiles');
    expect(table).toHaveTextContent('anna.private@example.com');
    expect(table).toHaveTextContent('anna@oldco.example.com');
    expect(screen.getByTestId('merge-request-moves')).toHaveTextContent(
      'registrations.attendee_username: 3'
    );
  });

  it('should_approve_and_reportDecision', async () => {
    vi.mocked(api.getProfileMergeRequest).mockResolvedValue(open);
    vi.mocked(api.approveProfileMergeRequest).mockResolvedValue({
      ...open,
      status: 'APPROVED',
      decidedBy: 'orga.nizer',
      preview: null,
    });
    const onDecided = vi.fn();
    renderPanel(onDecided);

    await userEvent.click(await screen.findByTestId('merge-request-approve'));

    expect(api.approveProfileMergeRequest).toHaveBeenCalledWith('r-1');
    expect(await screen.findByTestId('merge-request-decided')).toHaveTextContent('orga.nizer');
    expect(onDecided).toHaveBeenCalled();
  });

  it('should_requireReason_before_declining', async () => {
    vi.mocked(api.getProfileMergeRequest).mockResolvedValue(open);
    vi.mocked(api.declineProfileMergeRequest).mockResolvedValue({
      ...open,
      status: 'DECLINED',
      decidedBy: 'orga.nizer',
      reason: 'Andere Person',
      preview: null,
    });
    renderPanel();

    await userEvent.click(await screen.findByTestId('merge-request-decline'));
    expect(screen.getByTestId('merge-request-decline')).toBeDisabled();
    await userEvent.type(screen.getByTestId('merge-request-reason'), 'Andere Person');
    await userEvent.click(screen.getByTestId('merge-request-decline'));

    expect(api.declineProfileMergeRequest).toHaveBeenCalledWith('r-1', 'Andere Person');
    expect(await screen.findByTestId('merge-request-decided')).toHaveTextContent('Andere Person');
  });

  it('should_notOfferApprove_when_mergeIsBlocked', async () => {
    vi.mocked(api.getProfileMergeRequest).mockResolvedValue({
      ...open,
      preview: { ...open.preview!, canMerge: false, reasons: ['SOURCE_HAS_LOGIN'] },
    } as ProfileMergeRequestView);
    renderPanel();

    expect(await screen.findByTestId('merge-request-approve')).toBeDisabled();
    expect(screen.getByTestId('merge-request-cannot-merge')).toHaveTextContent('SOURCE_HAS_LOGIN');
  });
});
