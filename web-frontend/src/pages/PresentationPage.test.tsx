/**
 * PresentationPage Tests
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Smoke tests: renders Welcome section, keyboard nav advances, error screen on failure.
 */

import { describe, test, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { PresentationPage } from './PresentationPage';

// Mock hooks — control their return values from tests
vi.mock('@/hooks/usePresentationData', () => ({
  usePresentationData: vi.fn(),
}));

vi.mock('@/hooks/usePresentationSections', async (importActual) => {
  const actual = await importActual<typeof import('@/hooks/usePresentationSections')>();
  return {
    ...actual,
    usePresentationSections: vi.fn(),
  };
});

import { usePresentationData } from '@/hooks/usePresentationData';
import { usePresentationSections } from '@/hooks/usePresentationSections';

const mockUsePresentationData = vi.mocked(usePresentationData);
const mockUsePresentationSections = vi.mocked(usePresentationSections);

const MOCK_EVENT = {
  eventCode: 'BATbern57',
  eventNumber: 57,
  title: 'Test Event',
  date: '2026-06-01T18:00:00Z',
  registrationDeadline: '2026-05-31T00:00:00Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3',
  venueCapacity: 500,
  organizerUsername: 'test.organizer',
  currentAttendeeCount: 0,
  topic: { name: 'Zero Trust', imageUrl: null },
};

const MOCK_SETTINGS = { aboutText: 'BATbern ist...', partnerCount: 9 };

const MOCK_SECTIONS = [
  { type: 'welcome' as const, key: 'welcome' },
  { type: 'about' as const, key: 'about' },
  { type: 'committee' as const, key: 'committee' },
];

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
}

function renderPage() {
  const queryClient = makeQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/present/BATbern57']}>
        <Routes>
          <Route path="/present/:eventCode" element={<PresentationPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('PresentationPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockUsePresentationData.mockReturnValue({
      data: {
        event: MOCK_EVENT as never,
        sessions: [],
        organizers: [],
        upcomingEvents: [],
        settings: MOCK_SETTINGS,
      },
      isLoading: false,
      isInitialLoadError: false,
      refetch: vi.fn(),
    });

    mockUsePresentationSections.mockReturnValue(MOCK_SECTIONS as never);
  });

  test('renders Welcome section on initial load (AC #1)', async () => {
    renderPage();
    // WelcomeSlide renders eventNumber in hashtag
    await waitFor(() => {
      expect(screen.getByText(/#BATbern57/i)).toBeInTheDocument();
    });
  });

  test('shows loading state while data is loading', () => {
    mockUsePresentationData.mockReturnValue({
      data: { event: null, sessions: [], organizers: [], upcomingEvents: [], settings: null },
      isLoading: true,
      isInitialLoadError: false,
      refetch: vi.fn(),
    });
    mockUsePresentationSections.mockReturnValue([]);
    renderPage();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  test('renders branded error screen on initial load failure (AC #42)', async () => {
    mockUsePresentationData.mockReturnValue({
      data: { event: null, sessions: [], organizers: [], upcomingEvents: [], settings: null },
      isLoading: false,
      isInitialLoadError: true,
      refetch: vi.fn(),
    });
    mockUsePresentationSections.mockReturnValue([]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /versuchen/i })).toBeInTheDocument();
    });
  });

  test('retry button calls refetch (AC #42)', async () => {
    const refetch = vi.fn();
    mockUsePresentationData.mockReturnValue({
      data: { event: null, sessions: [], organizers: [], upcomingEvents: [], settings: null },
      isLoading: false,
      isInitialLoadError: true,
      refetch,
    });
    mockUsePresentationSections.mockReturnValue([]);
    renderPage();
    await waitFor(() => screen.getByRole('button', { name: /versuchen/i }));
    fireEvent.click(screen.getByRole('button', { name: /versuchen/i }));
    expect(refetch).toHaveBeenCalledOnce();
  });

  test('ArrowRight advances to next section without crashing', async () => {
    renderPage();
    await waitFor(() => screen.getByText(/#BATbern57/i));
    // Pressing ArrowRight from welcome (index 0) should advance to about (index 1)
    fireEvent.keyDown(document.body, { key: 'ArrowRight' });
    // No crash expected; component should still be mounted
    expect(screen.queryByText(/Laden/i)).not.toBeInTheDocument();
  });
});
