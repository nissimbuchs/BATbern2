/**
 * LiveControlPage (#1219): the header and the connection badge follow the UI language.
 *
 * Real `useLiveSessionControl` polling `GET /events/{eventCode}/live-timing` through the MSW
 * harness, real AuthContext (`renderWithAuth`) and real i18next (en). No own-code mocks.
 */
import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
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
