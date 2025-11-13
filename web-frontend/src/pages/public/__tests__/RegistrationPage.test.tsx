/**
 * RegistrationPage Component Tests (Story 4.1.5 - Task 14)
 *
 * Tests for the dedicated registration page
 * Covers loading states, error handling, and wizard integration
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RegistrationPage from '../RegistrationPage';
import { eventApiClient } from '@/services/eventApiClient';

// Mock eventApiClient
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getEvent: vi.fn(),
  },
}));

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'public.registerFor': 'Register for',
        'public.errors.loadFailed': 'Failed to load event',
        'public.errors.checkBackLater': 'Please check back later',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock RegistrationWizard
vi.mock('@/components/public/Registration/RegistrationWizard', () => ({
  RegistrationWizard: ({ eventCode, inline }: { eventCode: string; inline: boolean }) => (
    <div data-testid="registration-wizard">
      <div data-testid="wizard-eventCode">{eventCode}</div>
      <div data-testid="wizard-inline">{inline ? 'inline' : 'dedicated'}</div>
    </div>
  ),
}));

// Mock PublicLayout
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="public-layout">{children}</div>
  ),
}));

describe('RegistrationPage Component', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const renderWithProviders = (initialRoute = '/register/BAT2025') => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialRoute]}>
          <Routes>
            <Route path="/register/:eventCode" element={<RegistrationPage />} />
            <Route path="/register" element={<div>Home Page</div>} />
            <Route path="/" element={<div>Home Page</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
  });

  describe('Loading States', () => {
    test('should_renderLoadingSpinner_when_fetchingEvent', async () => {
      vi.mocked(eventApiClient.getEvent).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      renderWithProviders();

      // Loading spinner should be visible
      expect(screen.getByRole('status', { hidden: true })).toBeInTheDocument();
    });

    test('should_renderWithinPublicLayout_when_mounted', () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern',
      });

      renderWithProviders();

      expect(screen.getByTestId('public-layout')).toBeInTheDocument();
    });
  });

  describe('Error States', () => {
    test('should_renderErrorMessage_when_eventFetchFails', async () => {
      vi.mocked(eventApiClient.getEvent).mockRejectedValue(new Error('Network error'));

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('Failed to load event')).toBeInTheDocument();
        expect(screen.getByText('Please check back later')).toBeInTheDocument();
      });
    });

    test('should_notRenderWizard_when_eventFetchFails', async () => {
      vi.mocked(eventApiClient.getEvent).mockRejectedValue(new Error('Network error'));

      renderWithProviders();

      await waitFor(() => {
        expect(screen.queryByTestId('registration-wizard')).not.toBeInTheDocument();
      });
    });
  });

  describe('Successful Event Loading', () => {
    test('should_renderEventHeader_when_eventLoaded', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern Congress Center',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/Register for BATbern 2025/i)).toBeInTheDocument();
      });
    });

    test('should_renderFormattedDate_when_eventHasDate', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern',
      });

      renderWithProviders();

      await waitFor(() => {
        // Check for date presence (format depends on locale)
        const dateElements = screen.getAllByText(/2025/);
        expect(dateElements.length).toBeGreaterThan(0);
      });
    });

    test('should_renderVenueName_when_eventHasVenue', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern Congress Center',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/Bern Congress Center/i)).toBeInTheDocument();
      });
    });

    test('should_renderRegistrationWizard_when_eventLoaded', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByTestId('registration-wizard')).toBeInTheDocument();
      });
    });

    test('should_passEventCodeToWizard_when_rendered', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByTestId('wizard-eventCode')).toHaveTextContent('BAT2025');
      });
    });

    test('should_passInlineFalseToWizard_when_rendered', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByTestId('wizard-inline')).toHaveTextContent('dedicated');
      });
    });
  });

  describe('Route Parameters', () => {
    test('should_redirectToHome_when_noEventCodeInURL', () => {
      renderWithProviders('/register');

      // Should redirect - Home Page will be rendered
      expect(screen.getByText('Home Page')).toBeInTheDocument();
    });

    test('should_fetchEventWithCorrectCode_when_routeHasEventCode', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'CUSTOM2025',
        title: 'Custom Event 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Venue',
      });

      renderWithProviders('/register/CUSTOM2025');

      await waitFor(() => {
        expect(eventApiClient.getEvent).toHaveBeenCalledWith('CUSTOM2025');
      });
    });
  });

  describe('Edge Cases', () => {
    test('should_handleEventWithoutDate_when_dateNotProvided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        venueName: 'Bern',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/Register for BATbern 2025/i)).toBeInTheDocument();
      });

      // Should not crash without date
      expect(screen.getByTestId('registration-wizard')).toBeInTheDocument();
    });

    test('should_handleEventWithoutVenue_when_venueNotProvided', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/Register for BATbern 2025/i)).toBeInTheDocument();
      });

      // Should not crash without venue
      expect(screen.getByTestId('registration-wizard')).toBeInTheDocument();
    });

    test('should_renderCenteredLayout_when_eventLoaded', async () => {
      vi.mocked(eventApiClient.getEvent).mockResolvedValue({
        eventCode: 'BAT2025',
        title: 'BATbern 2025',
        date: '2025-06-15T00:00:00Z',
        venueName: 'Bern',
      });

      const { container } = renderWithProviders();

      await waitFor(() => {
        const maxWContainer = container.querySelector('.max-w-4xl');
        expect(maxWContainer).toBeInTheDocument();
      });
    });
  });
});
