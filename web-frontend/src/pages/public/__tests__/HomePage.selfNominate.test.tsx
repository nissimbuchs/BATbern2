/**
 * HomePage hero: the speaking call (Story 7.2, #1263).
 *
 * On `/` the hero shows the self-nomination button to every visitor and opens its form once on
 * the return from login (`?nominate=<heroCode>`), removing the param itself. On an event's own
 * page (`/events/:code`) the hero button stays hidden until login.
 *
 * Network at the wire (MSW; other calls answer with their spec examples), real auth context,
 * real router, real i18next (English).
 */

import { describe, test, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { Route, Routes, useLocation } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import i18n from '@/i18n/config';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedInAs, signedOut } from '@/test/auth';
import type { UseAuthReturn } from '@/contexts/AuthContext';
import { anEventDetail, daysFromNow } from '@/test/fixtures/events';
import HomePage from '../HomePage';

const hero = anEventDetail({
  eventCode: 'BATbern61',
  title: 'Künstliche Intelligenz & Architektur',
  date: daysFromNow(60),
  workflowState: 'TOPIC_SELECTION',
  topic: { code: 'ai', name: 'KI', description: null, category: 'technical' },
  currentPublishedPhase: 'TOPIC',
  selfNominationOpen: true,
  sessions: [],
  // No venue map: it would load the Google Maps script from the network.
  venueAddress: undefined,
});

function Where() {
  const location = useLocation();
  return <p data-testid="where">{`${location.pathname}${location.search}`}</p>;
}

function renderHome(path: string, auth: UseAuthReturn) {
  api.on('getPublicCurrentEvent', () => hero);
  api.on('getPublicEvent', () => hero);
  // The upcoming-events section stays loading, so it never touches `nominate`: whatever clears
  // the param in these tests is the hero itself.
  api.on('listPublicEvents', () => new Promise<never>(() => {}));
  return renderWithAuth(
    <HelmetProvider>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/events/:eventCode" element={<HomePage />} />
        <Route path="/login" element={<p data-testid="login-page" />} />
      </Routes>
      <Where />
    </HelmetProvider>,
    { auth, initialEntries: [path] }
  );
}

const buttonName = () => i18n.t('common:attendee.selfNominate.button');

describe('HomePage hero self-nomination (#1263)', () => {
  test('shows the button to an anonymous visitor on the homepage', async () => {
    renderHome('/', signedOut());

    const heroPanel = await screen.findByTestId('hero-self-nominate');
    expect(within(heroPanel).getByRole('button', { name: buttonName() })).toBeInTheDocument();
  });

  test('opens the form once from ?nominate=<heroCode> and removes the param itself', async () => {
    renderHome('/?nominate=BATbern61', signedInAs('attendee'));

    expect(await screen.findByTestId('self-nominate-dialog')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent(/^\/$/));
    // Still open after the param is gone.
    expect(screen.getByTestId('self-nominate-dialog')).toBeInTheDocument();
  });

  test('keeps the param and the form closed for another event code', async () => {
    renderHome('/?nominate=BATbern99', signedInAs('attendee'));

    // Positive control: the hero button is there.
    expect(await screen.findByTestId('self-nominate-button')).toBeInTheDocument();
    expect(screen.queryByTestId('self-nominate-dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/?nominate=BATbern99');
  });

  test('hides the hero button from anonymous visitors on the event page', async () => {
    const { unmount } = renderHome('/events/BATbern61', signedInAs('attendee'));
    // Positive control: signed in, the event page hero shows it.
    expect(await screen.findByTestId('self-nominate-button')).toBeInTheDocument();
    unmount();

    renderHome('/events/BATbern61', signedOut());
    await screen.findByTestId('hero-self-nominate');
    expect(screen.queryByTestId('self-nominate-button')).not.toBeInTheDocument();
  });
});
