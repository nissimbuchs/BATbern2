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
  UserAutocomplete: ({ value }: { value: { id: string } | null }) => (
    <div data-testid="user-autocomplete-stub" data-selected={value?.id ?? ''} />
  ),
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

  // Bug fix 2026-09-09. The prefill used to do `setSelectedUser(users[0])` on the FIRST-NAME
  // fallback. For "Matthias Stürmer" the full-name search returned nothing (the backend had no
  // full-name matching, and the frontend double-encoded the umlaut), so the fallback searched
  // "Matthias", got an arbitrary unordered page of 32 namesakes, and silently pre-selected
  // Matthias GERMANN. Pressing Promote then granted SPEAKER + a PRIMARY_SPEAKER session row to
  // the wrong person. An ambiguous fallback must select nobody and say so.
  describe('ambiguous fallback', () => {
    const stuermerSpeaker = {
      ...baseSpeaker,
      speakerName: 'Matthias Stürmer',
      source: 'organizer_added',
    } as SpeakerPoolEntry;

    test('should_selectNobody_when_theFallbackIsAmbiguous', async () => {
      searchUsers.mockImplementation((q: string) => {
        if (q === 'Matthias Stürmer') return Promise.resolve([]);
        return Promise.resolve([
          { id: 'matthias.germann', email: 'g@x.ch', firstName: 'Matthias', lastName: 'Germann' },
          { id: 'matthias.imsand', email: 'i@x.ch', firstName: 'Matthias', lastName: 'Imsand' },
        ]);
      });

      const { getByTestId } = render(
        <PromoteSpeakerSubView speaker={stuermerSpeaker} eventCode="BATbern73" onBack={() => {}} />
      );

      await waitFor(() => expect(searchUsers).toHaveBeenCalledTimes(3));
      expect(getByTestId('user-autocomplete-stub')).toHaveAttribute('data-selected', '');
      expect(getByTestId('promote-ambiguous-hint')).toBeInTheDocument();
    });

    test('should_selectTheUser_when_theFullNameSearchMatchesExactly', async () => {
      searchUsers.mockImplementation((q: string) =>
        q === 'Matthias Stürmer'
          ? Promise.resolve([
              {
                id: 'matthias.stuermer',
                email: 'matthias.stuermer@bfh.ch',
                firstName: 'Matthias',
                lastName: 'Stürmer',
              },
            ])
          : Promise.resolve([])
      );

      const { getByTestId } = render(
        <PromoteSpeakerSubView speaker={stuermerSpeaker} eventCode="BATbern73" onBack={() => {}} />
      );

      await waitFor(() =>
        expect(getByTestId('user-autocomplete-stub')).toHaveAttribute(
          'data-selected',
          'matthias.stuermer'
        )
      );
    });

    test('should_selectTheUser_when_theFallbackReturnsExactlyOne', async () => {
      searchUsers.mockImplementation((q: string) =>
        q === 'Stürmer'
          ? Promise.resolve([
              {
                id: 'matthias.stuermer',
                email: 'matthias.stuermer@bfh.ch',
                firstName: 'Matthias',
                lastName: 'Stürmer',
              },
            ])
          : Promise.resolve([])
      );

      const { getByTestId } = render(
        <PromoteSpeakerSubView speaker={stuermerSpeaker} eventCode="BATbern73" onBack={() => {}} />
      );

      await waitFor(() =>
        expect(getByTestId('user-autocomplete-stub')).toHaveAttribute(
          'data-selected',
          'matthias.stuermer'
        )
      );
    });

    test('should_searchTheSurnameBeforeTheFirstName_when_theFullNameMisses', async () => {
      searchUsers.mockResolvedValue([]);

      render(
        <PromoteSpeakerSubView speaker={stuermerSpeaker} eventCode="BATbern73" onBack={() => {}} />
      );

      // Surname is far more selective than "Matthias", so it must be tried first.
      await waitFor(() => expect(searchUsers).toHaveBeenCalledTimes(3));
      expect(searchUsers.mock.calls.map((c: unknown[]) => c[0])).toEqual([
        'Matthias Stürmer',
        'Stürmer',
        'Matthias',
      ]);
    });
  });
});
