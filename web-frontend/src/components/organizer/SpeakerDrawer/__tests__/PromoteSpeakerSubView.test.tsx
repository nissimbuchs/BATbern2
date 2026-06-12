/**
 * PromoteSpeakerSubView — Story 7.2 / ADR-012 promote-picker behaviour.
 *
 * Guards the two changes that let a self-nominated ATTENDEE be promoted:
 *   1. A self_nomination row resolves the exact attendee by username (getUserByUsername),
 *      NOT a name-guess search — and crucially with NO SPEAKER-role filter.
 *   2. An organizer-sourced row still prefills by brainstorm-name search.
 *
 * The heavy children (UserAutocomplete, UserCreateEditModal) and the mutation hook are stubbed
 * so the test isolates the prefill/resolution logic.
 */
import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@/test/test-utils';
import { PromoteSpeakerSubView } from '../PromoteSpeakerSubView';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

const getUserByUsername = vi.fn();
const searchUsers = vi.fn();
vi.mock('@/services/api/userManagementApi', () => ({
  getUserByUsername: (u: string) => getUserByUsername(u),
  searchUsers: (q: string, n: number) => searchUsers(q, n),
}));

vi.mock('@/hooks/useSpeakerPool', () => ({
  usePromoteSpeakerToReady: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
}));

// Stub the heavy children so we exercise only the prefill logic.
vi.mock('@/components/shared/UserAutocomplete', () => ({
  UserAutocomplete: () => <div data-testid="user-autocomplete-stub" />,
}));
vi.mock('@/components/organizer/UserManagement/UserCreateEditModal', () => ({
  default: () => <div data-testid="user-modal-stub" />,
}));

const baseSpeaker: SpeakerPoolEntry = {
  id: 'pool-1',
  speakerName: 'Jane Attendee',
  status: 'CONTACTED',
} as unknown as SpeakerPoolEntry;

describe('PromoteSpeakerSubView prefill', () => {
  beforeEach(() => {
    getUserByUsername.mockReset();
    searchUsers.mockReset();
    getUserByUsername.mockResolvedValue({
      id: 'jane.attendee',
      email: 'jane@example.com',
      firstName: 'Jane',
      lastName: 'Attendee',
      roles: ['ATTENDEE'],
    });
    searchUsers.mockResolvedValue([]);
  });

  test('self_nomination resolves the attendee by username, not a SPEAKER-filtered search', async () => {
    const speaker = {
      ...baseSpeaker,
      source: 'self_nomination',
      proposedByUsername: 'jane.attendee',
    } as SpeakerPoolEntry;

    render(<PromoteSpeakerSubView speaker={speaker} eventCode="BATbern73" onBack={() => {}} />);

    await waitFor(() => expect(getUserByUsername).toHaveBeenCalledWith('jane.attendee'));
    // The name-guess search is skipped entirely for a self-nomination.
    expect(searchUsers).not.toHaveBeenCalled();
  });

  test('organizer_added row prefills by brainstorm-name search (no username resolution)', async () => {
    const speaker = { ...baseSpeaker, source: 'organizer_added' } as SpeakerPoolEntry;

    render(<PromoteSpeakerSubView speaker={speaker} eventCode="BATbern73" onBack={() => {}} />);

    await waitFor(() => expect(searchUsers).toHaveBeenCalledWith('Jane Attendee', 20));
    expect(getUserByUsername).not.toHaveBeenCalled();
  });
});
