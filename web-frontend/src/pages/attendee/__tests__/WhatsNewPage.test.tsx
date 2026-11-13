/**
 * "What's new" page (`/attendee/whats-new`, owner 2026-11-13): every news entry that applies to the
 * user, the new ones first and marked, a search over title and text, no close button. Opening it
 * counts as seen. Network mocked at the wire (MSW); the live list is used, so the assertions are
 * derived from it.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '@/test/msw/api';
import { aClaimCandidate, aUserSettings } from '@/test/fixtures';
import i18n from '@/i18n/config';
import { WHATS_NEW_ENTRIES, listAllEntries } from '@/features/whatsNew/catalogue';
import { anAuthState, anAuthUser, renderWithAuth } from '@/test/auth';
import WhatsNewPage from '../WhatsNewPage';

let storedSeenAt: string | undefined;

function renderPage() {
  return renderWithAuth(<WhatsNewPage />, {
    auth: anAuthState(anAuthUser({ role: 'attendee', username: 'matthias.keller.2' })),
    initialEntries: ['/attendee/whats-new'],
  });
}

const shownKeys = async () => {
  const list = await screen.findByTestId('whats-new-list');
  return within(list)
    .getAllByTestId(/^whats-new-entry-/)
    .map((el) => el.getAttribute('data-testid')!.replace('whats-new-entry-', ''));
};

const title = (key: string) => i18n.t(`events:whatsNew.entries.${key}.title`);

/** The day before the newest live entry: at least that entry is new, older ones are not. */
const dayBeforeNewest = () => {
  const day = WHATS_NEW_ENTRIES.filter((e) => !e.condition)
    .map((e) => e.releasedOn)
    .sort()
    .at(-1)!;
  return new Date(Date.parse(`${day}T00:00:00Z`) - 12 * 60 * 60 * 1000).toISOString();
};

describe('WhatsNewPage', () => {
  beforeEach(() => {
    storedSeenAt = undefined;
    api.on('getUserSettings', () =>
      aUserSettings(storedSeenAt ? { whatsNewSeenAt: storedSeenAt } : {})
    );
    api.on('updateUserSettings', ({ body }) => {
      storedSeenAt = body?.whatsNewSeenAt;
      return aUserSettings({ whatsNewSeenAt: storedSeenAt });
    });
  });

  it('should_listAllNews_withoutCloseButton_when_upToDate', async () => {
    storedSeenAt = new Date().toISOString();
    renderPage();

    const shown = await shownKeys();
    expect(shown).toEqual(
      listAllEntries(WHATS_NEW_ENTRIES, storedSeenAt, { hasClaimCandidates: false }).map(
        ({ entry }) => entry.key
      )
    );
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      i18n.t('events:whatsNew.title')
    );
    expect(screen.queryByTestId('whats-new-close')).not.toBeInTheDocument();
    expect(screen.queryByTestId(/^whats-new-new-/)).not.toBeInTheDocument();
    // Nothing was new, so nothing is stored.
    expect(api.calls('updateUserSettings')).toHaveLength(0);
    // Each entry links to where the feature is.
    const first = WHATS_NEW_ENTRIES.find((e) => e.key === shown[0])!;
    expect(screen.getByText(title(first.key)).closest('a')).toHaveAttribute('href', first.link);
  });

  it('should_markTheNewOnesFirst_andStoreSeen_when_somethingIsNew', async () => {
    const seenBefore = dayBeforeNewest();
    storedSeenAt = seenBefore;
    renderPage();

    const shown = await shownKeys();
    const expected = listAllEntries(WHATS_NEW_ENTRIES, seenBefore, { hasClaimCandidates: false });
    expect(shown).toEqual(expected.map(({ entry }) => entry.key));
    const fresh = expected.filter((e) => e.isNew).map(({ entry }) => entry.key);
    expect(fresh.length).toBeGreaterThan(0);

    // Opening the page counts as seen, once ...
    await waitFor(() => expect(api.calls('updateUserSettings')).toHaveLength(1));
    // ... and the markers of this visit stay.
    for (const key of fresh) expect(screen.getByTestId(`whats-new-new-${key}`)).toBeInTheDocument();
  });

  it('should_listTheMerge_when_theUserHasAnEarlierProfile', async () => {
    storedSeenAt = new Date().toISOString();
    api.on('listProfileClaimCandidates', () => [aClaimCandidate()]);
    renderPage();

    const merge = WHATS_NEW_ENTRIES.find((e) => e.condition === 'hasClaimCandidates')!;
    await waitFor(async () => expect(await shownKeys()).toContain(merge.key));
  });

  it('should_filterByTitleAndText_ignoringCaseAndAccents', async () => {
    storedSeenAt = new Date().toISOString();
    const user = userEvent.setup();
    renderPage();
    const all = await shownKeys();
    const target = all[all.length - 1];
    // A word from the entry's text, typed in upper case.
    const word = i18n
      .t(`events:whatsNew.entries.${target}.text`)
      .split(/\s+/)
      .find((w) => w.length > 6)!
      .replace(/[^\p{L}]/gu, '');

    await user.type(screen.getByRole('searchbox'), word.toUpperCase());

    const shown = await shownKeys();
    expect(shown).toContain(target);
    expect(shown.length).toBeLessThan(all.length);
  });

  it('should_sayNothingMatches_when_theSearchFindsNothing', async () => {
    storedSeenAt = new Date().toISOString();
    const user = userEvent.setup();
    renderPage();
    await shownKeys();

    await user.type(screen.getByRole('searchbox'), 'qqqxyz');

    expect(await screen.findByText(i18n.t('events:whatsNew.noMatch'))).toBeInTheDocument();
    expect(screen.queryByTestId('whats-new-list')).not.toBeInTheDocument();
  });
});
