/**
 * The app's own route table (App.tsx) sends the retired speaker placeholder paths on (#1223 item
 * 8). Rendered as the browser does: the real App with its BrowserRouter and AuthProvider, nobody
 * signed in (Amplify is the boundary, mocked in setup.ts). The redirect target is protected, so
 * the visitor lands on the login page with the target remembered as `from`.
 */
import { describe, it, expect, afterEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import App from './App';

type HistoryState = { usr?: { from?: { pathname?: string } } } | null;

describe('App route table: retired paths', () => {
  afterEach(() => window.history.replaceState(null, '', '/'));

  it('sends /organizer/speakers on to /organizer/users, not to a placeholder', async () => {
    window.history.replaceState(null, '', '/organizer/speakers');

    render(<App />);

    await waitFor(() => expect(window.location.pathname).toBe('/login'), { timeout: 5000 });
    expect((window.history.state as HistoryState)?.usr?.from?.pathname).toBe('/organizer/users');
  });
});
