/**
 * #1223 item 8: `/organizer/speakers` showed a placeholder page. It and its speaker twin now
 * redirect to the pages where that work is done.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { retiredRouteRedirects } from './retiredRoutes';

const Where = () => <p>at {useLocation().pathname}</p>;

const visit = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        {retiredRouteRedirects()}
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>
  );

describe('retired routes', () => {
  it('sends an organizer from /organizer/speakers to the users page', () => {
    visit('/organizer/speakers');

    expect(screen.getByText('at /organizer/users')).toBeInTheDocument();
  });

  it('sends a speaker from /speakers to the speaker portal', () => {
    visit('/speakers');

    expect(screen.getByText('at /speaker-portal/dashboard')).toBeInTheDocument();
  });

  it('leaves other paths alone', () => {
    visit('/organizer/users');

    expect(screen.getByText('at /organizer/users')).toBeInTheDocument();
  });
});
