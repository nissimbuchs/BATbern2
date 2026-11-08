/**
 * PromoteSpeakerSubView — Story 7.2 / ADR-012 promote-picker behaviour.
 *
 * Guards the two changes that let a self-nominated ATTENDEE be promoted:
 *   1. A self_nomination row resolves the exact attendee by username (getUserByUsername),
 *      NOT a name-guess search — and crucially with NO SPEAKER-role filter.
 *   2. An organizer-sourced row still prefills by brainstorm-name search.
 *
 * Story 17.0d: the real screen with its real children (UserAutocomplete, UserCreateEditModal),
 * hooks and userManagementApi. The MSW harness answers GET /users/search from the
 * production-shaped people (two "Matthias Keller", more than 20 "Matthias", "Stürmer"); what the
 * picker shows is read from the real autocomplete field.
 */
import { describe, test, expect } from 'vitest';
import { render, waitFor, screen } from '@/test/test-utils';
import { api } from '@/test/msw/api';
import { aUser, shapes, toSearchResult } from '@/test/fixtures';
import { PromoteSpeakerSubView } from '../PromoteSpeakerSubView';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

const baseSpeaker: SpeakerPoolEntry = {
  id: 'pool-1',
  speakerName: 'Jane Attendee',
  status: 'CONTACTED',
} as unknown as SpeakerPoolEntry;

const pickerField = () => screen.getByTestId('promote-speaker-search-field');
const searchedQueries = () => api.calls('searchUsers').map((c) => c.query.get('query'));

describe('PromoteSpeakerSubView prefill', () => {
  test('self_nomination resolves the attendee by username, not a SPEAKER-filtered search', async () => {
    const attendee = aUser({ ...shapes.vietnamese, roles: ['ATTENDEE'] });
    api.on('getUserByUsername', ({ params }) =>
      params.username === attendee.id ? attendee : aUser({ id: params.username })
    );
    const speaker = {
      ...baseSpeaker,
      speakerName: 'Hông Ân Meier',
      source: 'self_nomination',
      proposedByUsername: attendee.id,
    } as SpeakerPoolEntry;

    render(<PromoteSpeakerSubView speaker={speaker} eventCode="BATbern73" onBack={() => {}} />);

    await waitFor(() => expect(pickerField()).toHaveValue('Hông Ân Meier'));
    expect(api.calls('getUserByUsername')[0].params.username).toBe('hongan.meier');
    // The name-guess search is skipped entirely for a self-nomination.
    expect(api.calls('searchUsers')).toHaveLength(0);
  });

  test('organizer_added row prefills by brainstorm-name search (no username resolution)', async () => {
    const speaker = {
      ...baseSpeaker,
      speakerName: 'Jean-Luc O’Neill',
      source: 'organizer_added',
    } as SpeakerPoolEntry;

    render(<PromoteSpeakerSubView speaker={speaker} eventCode="BATbern73" onBack={() => {}} />);

    await waitFor(() => expect(pickerField()).toHaveValue('Jean-Luc O’Neill'));
    const [call] = api.calls('searchUsers');
    expect(call.query.get('query')).toBe('Jean-Luc O’Neill');
    expect(call.query.get('limit')).toBe('20');
    // No SPEAKER-role filter: an ATTENDEE may be promoted.
    expect(call.query.has('role')).toBe(false);
    expect(api.calls('getUserByUsername')).toHaveLength(0);
  });

  // Bug fix 2026-09-09. The prefill used to do `setSelectedUser(users[0])` on the FIRST-NAME
  // fallback. For "Matthias Stürmer" the full-name search returned nothing (the backend had no
  // full-name matching, and the frontend double-encoded the umlaut), so the fallback searched
  // "Matthias", got an arbitrary unordered page of 32 namesakes, and silently pre-selected
  // the wrong Matthias. Pressing Promote then granted SPEAKER + a PRIMARY_SPEAKER session row to
  // the wrong person. An ambiguous fallback must select nobody and say so.
  describe('ambiguous fallback', () => {
    const speakerNamed = (speakerName: string) =>
      ({ ...baseSpeaker, speakerName, source: 'organizer_added' }) as SpeakerPoolEntry;

    test('should_selectNobody_when_twoPeopleHaveTheIdenticalFullName', async () => {
      render(
        <PromoteSpeakerSubView
          speaker={speakerNamed('Matthias Keller')}
          eventCode="BATbern73"
          onBack={() => {}}
        />
      );

      expect(await screen.findByTestId('promote-ambiguous-hint')).toBeInTheDocument();
      expect(searchedQueries()).toEqual(['Matthias Keller', 'Keller', 'Matthias']);
      expect(pickerField()).toHaveValue('');
    });

    test('should_selectNobody_when_onlyTheFirstNameMatchesManyNamesakes', async () => {
      // A Matthias the database does not know: surname misses, the first name finds 20 of the
      // more than 20 namesakes (the production shape of #1034).
      render(
        <PromoteSpeakerSubView
          speaker={speakerNamed('Matthias Unbekannt')}
          eventCode="BATbern73"
          onBack={() => {}}
        />
      );

      expect(await screen.findByTestId('promote-ambiguous-hint')).toBeInTheDocument();
      expect(searchedQueries()).toEqual(['Matthias Unbekannt', 'Unbekannt', 'Matthias']);
      expect(pickerField()).toHaveValue('');
    });

    test('should_selectTheUser_when_theFullNameSearchMatchesExactly', async () => {
      render(
        <PromoteSpeakerSubView
          speaker={speakerNamed('Matthias Stürmer')}
          eventCode="BATbern73"
          onBack={() => {}}
        />
      );

      await waitFor(() => expect(pickerField()).toHaveValue('Matthias Stürmer'));
      expect(searchedQueries()).toEqual(['Matthias Stürmer']);
      expect(screen.queryByTestId('promote-ambiguous-hint')).not.toBeInTheDocument();
    });

    test('should_selectTheUser_when_thePoolSpellsTheUmlautOut', async () => {
      // The pool says "Stuermer"; the backend search folds ü = ue and still finds him.
      render(
        <PromoteSpeakerSubView
          speaker={speakerNamed('Matthias Stuermer')}
          eventCode="BATbern73"
          onBack={() => {}}
        />
      );

      await waitFor(() => expect(pickerField()).toHaveValue('Matthias Stürmer'));
    });

    test('should_selectTheUser_when_theSurnameFallbackReturnsExactlyOne', async () => {
      api.on('searchUsers', ({ query }) =>
        query.get('query') === 'Stürmer' ? [toSearchResult(shapes.stuermer)] : []
      );

      render(
        <PromoteSpeakerSubView
          speaker={speakerNamed('Matthias Stürmer')}
          eventCode="BATbern73"
          onBack={() => {}}
        />
      );

      await waitFor(() => expect(pickerField()).toHaveValue('Matthias Stürmer'));
      expect(searchedQueries()).toEqual(['Matthias Stürmer', 'Stürmer']);
    });

    test('should_searchTheSurnameBeforeTheFirstName_when_theFullNameMisses', async () => {
      api.on('searchUsers', () => []);

      render(
        <PromoteSpeakerSubView
          speaker={speakerNamed('Matthias Stürmer')}
          eventCode="BATbern73"
          onBack={() => {}}
        />
      );

      // Surname is far more selective than "Matthias", so it must be tried first.
      await waitFor(() => expect(api.calls('searchUsers')).toHaveLength(3));
      expect(searchedQueries()).toEqual(['Matthias Stürmer', 'Stürmer', 'Matthias']);
    });
  });
});
