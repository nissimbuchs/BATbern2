/**
 * LiveControlPage (#1219): the header and the connection badge follow the UI language.
 *
 * Real `useLiveSessionControl` polling `GET /events/{eventCode}/live-timing` through the MSW
 * harness, real AuthContext (`renderWithAuth`) and real i18next (en). No own-code mocks.
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedInAs } from '@/test/auth';
import LiveControlPage from '../LiveControlPage';

const renderPage = () =>
  renderWithAuth(
    <Routes>
      <Route path="/organizer/events/:eventCode/live-control" element={<LiveControlPage />} />
    </Routes>,
    {
      auth: signedInAs('organizer'),
      initialEntries: ['/organizer/events/BATbern61/live-control'],
    }
  );

describe('LiveControlPage — English labels (#1219)', () => {
  it('shows the English title and "Connecting..." before the first poll, then "Connected"', async () => {
    renderPage();

    expect(screen.getByText('Live Control')).toBeInTheDocument();
    expect(screen.getByText('Connecting...')).toBeInTheDocument();
    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(api.calls('getLiveTiming').length).toBeGreaterThan(0);
    expect(screen.queryByText(/Verbunden|Verbinde|Steuerung/)).not.toBeInTheDocument();
  });

  it('shows "Connecting..." in English while the poll fails (reconnecting)', async () => {
    api.on('getLiveTiming', () =>
      HttpResponse.json(
        { type: 'about:blank', title: 'Service Unavailable', status: 503 },
        { status: 503 }
      )
    );

    renderPage();

    // The first poll has failed once the loader gives way to the (empty) session card.
    expect(await screen.findByText('No active session')).toBeInTheDocument();
    expect(api.calls('getLiveTiming').length).toBeGreaterThan(0);
    expect(screen.getByText('Connecting...')).toBeInTheDocument();
    expect(screen.queryByText('Connected')).not.toBeInTheDocument();
  });
});

const OFFLINE_HINT =
  'Live updates unavailable. The page reconnects by itself when the network is back.';

describe('LiveControlPage — browser offline', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const setNavigatorOnline = (online: boolean) =>
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(online);

  const unavailable = () =>
    HttpResponse.json(
      { type: 'about:blank', title: 'Service Unavailable', status: 503 },
      { status: 503 }
    );

  it('shows the Offline badge and hint when the browser goes offline', async () => {
    renderPage();
    expect(await screen.findByText('Connected')).toBeInTheDocument();

    setNavigatorOnline(false);
    act(() => {
      window.dispatchEvent(new Event('offline'));
    });

    expect(screen.getByText('Offline')).toBeInTheDocument();
    expect(screen.getByText(OFFLINE_HINT)).toBeInTheDocument();
    expect(screen.queryByText('Connected')).not.toBeInTheDocument();
  });

  it('keeps Offline while polls fail; back online it polls at once and shows Connected only after that answer', async () => {
    setNavigatorOnline(false);
    api.on('getLiveTiming', unavailable);

    renderPage();

    // The first poll failed (503): the loader gave way, the badge stays Offline.
    expect(await screen.findByText('No active session')).toBeInTheDocument();
    expect(screen.getByText('Offline')).toBeInTheDocument();
    expect(screen.getByText(OFFLINE_HINT)).toBeInTheDocument();

    // The network is back, but the server's answer is held until we release it.
    let release!: () => void;
    const answered = new Promise<void>((resolve) => {
      release = resolve;
    });
    api.on('getLiveTiming', async () => {
      await answered;
      return {
        eventCode: 'BATbern61',
        version: 1,
        organizerPresent: true,
        currentSessionSlug: null,
        arrivedSpeakerCount: 0,
        totalSpeakerCount: 0,
        sessions: [],
      };
    });
    const pollsBeforeOnline = api.calls('getLiveTiming').length;

    setNavigatorOnline(true);
    act(() => {
      window.dispatchEvent(new Event('online'));
    });

    // Polls at once: the next scheduled poll is 15 s away and waitFor gives up after 1 s.
    await waitFor(() => expect(api.calls('getLiveTiming').length).toBe(pollsBeforeOnline + 1));
    // The last poll failed, so until the new answer arrives the badge says Connecting...
    expect(screen.getByText('Connecting...')).toBeInTheDocument();
    expect(screen.queryByText('Connected')).not.toBeInTheDocument();
    expect(screen.queryByText('Offline')).not.toBeInTheDocument();
    expect(screen.queryByText(OFFLINE_HINT)).not.toBeInTheDocument();

    release();

    expect(await screen.findByText('Connected')).toBeInTheDocument();
  });

  it('clears Offline when a poll succeeds even without an online event', async () => {
    setNavigatorOnline(false);

    renderPage();

    // The server answered the first poll, so the network is there.
    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(screen.queryByText('Offline')).not.toBeInTheDocument();
    expect(screen.queryByText(OFFLINE_HINT)).not.toBeInTheDocument();
  });
});
