/**
 * EventsParticipatedTable Tests
 *
 * Covers the read-only participation list and its responsive behaviour:
 * a desktop table at ≥md and a card view at <md (organizer mobile, round 3).
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { EventsParticipatedTable } from './EventsParticipatedTable';
import MockAdapter from 'axios-mock-adapter';
import apiClient from '@/services/api/apiClient';

// HTTP is stubbed at the boundary: the real service and apiClient run.
let http: MockAdapter;

const mockRegistrations = [
  {
    eventCode: 'BATbern56',
    eventTitle: 'Resilient Systems',
    eventDate: '2026-03-10T18:00:00Z',
    status: 'ATTENDED',
  },
  {
    eventCode: 'BATbern57',
    eventTitle: 'Zero Trust',
    eventDate: '2026-05-20T18:00:00Z',
    status: 'REGISTERED',
  },
];

const renderTable = () =>
  render(
    <I18nextProvider i18n={i18n}>
      <EventsParticipatedTable userId="alice.smith" />
    </I18nextProvider>
  );

/** Deterministic matchMedia mock for a viewport of the given width. */
const installMatchMediaForWidth = (viewportWidth: number) => {
  window.matchMedia = vi.fn((query: string) => {
    const maxMatch = /max-width:\s*([\d.]+)px/.exec(query);
    const minMatch = /min-width:\s*([\d.]+)px/.exec(query);
    let matches = false;
    if (maxMatch) matches = viewportWidth <= parseFloat(maxMatch[1]);
    else if (minMatch) matches = viewportWidth >= parseFloat(minMatch[1]);
    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    };
  }) as unknown as typeof window.matchMedia;
};

describe('EventsParticipatedTable', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    http = new MockAdapter(apiClient);
    http.onGet('/events/registrations').reply(200, mockRegistrations);
    installMatchMediaForWidth(1280);
  });

  afterEach(() => {
    http.restore();
    vi.clearAllMocks();
    window.matchMedia = originalMatchMedia;
  });

  it('should_renderTable_when_desktopViewport', async () => {
    installMatchMediaForWidth(1280);
    renderTable();

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.queryByTestId('events-participated-cards')).not.toBeInTheDocument();
    expect(screen.getByText('BATbern56')).toBeInTheDocument();
    expect(screen.getByText('Resilient Systems')).toBeInTheDocument();
    expect(http.history.get[0].headers?.['X-Attendee-Username']).toBe('alice.smith');
  });

  it('should_renderCards_when_mobileViewport', async () => {
    installMatchMediaForWidth(375);
    renderTable();

    expect(await screen.findByTestId('events-participated-cards')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();

    const cards = screen.getAllByTestId('event-participation-card');
    expect(cards).toHaveLength(2);
    // Event code (bold) + title + date are present in the card.
    expect(screen.getByText('BATbern56')).toBeInTheDocument();
    expect(screen.getByText('Resilient Systems')).toBeInTheDocument();
    // Swiss de-CH date format (dd.mm.yyyy) preserved in the card view.
    expect(screen.getByText('10.03.2026')).toBeInTheDocument();
    // Status text rendered (ATTENDED → translated label).
    expect(screen.getByText('Attended')).toBeInTheDocument();
  });

  it('should_showLoadError_when_requestFails', async () => {
    http.reset();
    http.onGet('/events/registrations').reply(500, { message: 'boom' });
    renderTable();

    expect(
      await screen.findByText(/failed to load event participation history/i)
    ).toBeInTheDocument();
  });

  it('should_showEmptyState_when_noParticipations', async () => {
    http.reset();
    http.onGet('/events/registrations').reply(200, []);
    installMatchMediaForWidth(375);
    renderTable();

    // Settle on the async empty-state first (positive assertion), then assert no cards.
    expect(await screen.findByText(/has not participated/i)).toBeInTheDocument();
    expect(screen.queryByTestId('events-participated-cards')).not.toBeInTheDocument();
  });
});
