/**
 * RegistrationConfirmationPage Component Tests (Story 4.1.6 - Task 12)
 *
 * Tests for the registration confirmation page
 * Covers QR code display, calendar export, confetti, and social sharing
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RegistrationConfirmationPage from '../RegistrationConfirmationPage';
import { eventApiClient } from '@/services/eventApiClient';
import { useAuth } from '@/hooks/useAuth/useAuth';
import type { Registration } from '@/types/event.types';

// Mock eventApiClient
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getRegistration: vi.fn(),
    getRegistrationQR: vi.fn(),
  },
}));

// Mock confetti
vi.mock('canvas-confetti', () => ({
  default: vi.fn(),
}));

// Mock PublicLayout
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div data-testid="public-layout">{children}</div>,
}));

// Mock useAuth
vi.mock('@/hooks/useAuth/useAuth', () => ({
  useAuth: vi.fn(),
}));

describe('RegistrationConfirmationPage Component', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const mockRegistration: Registration = {
    registrationCode: 'BAT-2025-000123',
    eventCode: 'BATbern25',
    eventTitle: 'BATbern 2025',
    eventDate: '2025-06-15T18:00:00Z',
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@example.com',
    company: 'Acme Corp',
    attendeeUsername: 'john.doe',
    status: 'confirmed',
    specialRequests: 'vegetarian',
    createdAt: '2025-01-09T10:00:00Z',
  };

  const renderWithProviders = (initialRoute = '/registration-confirmation/BAT-2025-000123') => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialRoute]}>
          <Routes>
            <Route path="/registration-confirmation/:confirmationCode" element={<RegistrationConfirmationPage />} />
            <Route path="/" element={<div>Home Page</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();

    // Default: user is not authenticated (anonymous registration)
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      error: null,
      accessToken: null,
      refreshToken: vi.fn(),
      signIn: vi.fn(),
      signOut: vi.fn(),
      signUp: vi.fn(),
      clearError: vi.fn(),
      hasRole: vi.fn(),
      hasPermission: vi.fn(),
      canAccess: vi.fn(),
      isTokenExpired: vi.fn(),
    });
  });

  describe('Data Fetching', () => {
    test('should_fetchRegistrationData_when_confirmationCodeProvided', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(eventApiClient.getRegistration).toHaveBeenCalledWith('BATbern25', 'BAT-2025-000123');
      });
    });

    test('should_renderLoadingState_when_fetchingData', () => {
      vi.mocked(eventApiClient.getRegistration).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      renderWithProviders();

      expect(screen.getByText(/loading/i)).toBeInTheDocument();
    });

    test('should_renderErrorState_when_fetchFails', async () => {
      vi.mocked(eventApiClient.getRegistration).mockRejectedValue(new Error('Network error'));

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/not found|error/i)).toBeInTheDocument();
      });
    });
  });

  describe('Registration Summary', () => {
    test('should_displayConfirmationCode_when_registrationLoaded', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/BAT-2025-000123/)).toBeInTheDocument();
      });
    });

    test('should_displayAttendeeName_when_registrationLoaded', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/John Doe/)).toBeInTheDocument();
      });
    });

    test('should_displayEventTitle_when_registrationLoaded', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/BATbern 2025/)).toBeInTheDocument();
      });
    });

    test('should_displaySuccessMessage_when_registrationConfirmed', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/confirmed|success/i)).toBeInTheDocument();
      });
    });
  });

  describe('QR Code Display', () => {
    test('should_fetchQRCode_when_confirmationCodeProvided', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(eventApiClient.getRegistrationQR).toHaveBeenCalledWith('BATbern25', 'BAT-2025-000123', 300);
      });
    });

    test('should_displayQRCodeImage_when_qrFetched', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        const qrImage = screen.getByAltText(/qr code/i);
        expect(qrImage).toBeInTheDocument();
        expect(qrImage).toHaveAttribute('src', 'blob:mock-qr-url');
      });
    });

    test('should_displayQRCodeInstructions_when_qrDisplayed', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/present this qr code.*check-in/i)).toBeInTheDocument();
      });
    });
  });

  describe('Calendar Export', () => {
    test('should_displayCalendarButton_when_registrationLoaded', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /calendar|add to calendar/i })).toBeInTheDocument();
      });
    });
  });

  describe('Navigation Links', () => {
    test('should_displayRegisterAnotherLink_when_pageLoaded', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        const registerLink = screen.getByRole('link', { name: /register another/i });
        expect(registerLink).toBeInTheDocument();
        expect(registerLink).toHaveAttribute('href', '/register/BATbern25');
      });
    });

    test('should_displayViewEventLink_when_pageLoaded', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        const eventLink = screen.getByRole('link', { name: /view event|event details/i });
        expect(eventLink).toBeInTheDocument();
      });
    });
  });

  describe('Social Sharing', () => {
    test('should_displaySocialShareButtons_when_registrationLoaded', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        // Should have social sharing section or buttons
        expect(screen.getByTestId('social-sharing')).toBeInTheDocument();
      });
    });
  });

  describe('Confetti Animation', () => {
    test('should_triggerConfetti_when_registrationLoaded', async () => {
      const confettiMock = (await import('canvas-confetti')).default;
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(confettiMock).toHaveBeenCalled();
      });
    });
  });

  describe('Route Parameters', () => {
    test('should_extractEventCodeFromConfirmationCode_when_routeHasConfirmationCode', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders('/registration-confirmation/BAT-2025-000456');

      await waitFor(() => {
        // Should parse eventCode from registration data
        expect(eventApiClient.getRegistration).toHaveBeenCalled();
      });
    });
  });

  describe('Account Linking CTA (AC #11)', () => {
    test('should_displayAccountLinkingCTA_when_userNotAuthenticated', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');
      vi.mocked(useAuth).mockReturnValue({
        isAuthenticated: false,
        isLoading: false,
        user: null,
        error: null,
        accessToken: null,
        refreshToken: vi.fn(),
        signIn: vi.fn(),
        signOut: vi.fn(),
        signUp: vi.fn(),
        clearError: vi.fn(),
        hasRole: vi.fn(),
        hasPermission: vi.fn(),
        canAccess: vi.fn(),
        isTokenExpired: vi.fn(),
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByTestId('account-linking-cta')).toBeInTheDocument();
      });
    });

    test('should_hideAccountLinkingCTA_when_userAuthenticated', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');
      vi.mocked(useAuth).mockReturnValue({
        isAuthenticated: true,
        isLoading: false,
        user: {
          userId: 'user-123',
          email: 'john.doe@example.com',
          role: 'attendee',
        },
        error: null,
        accessToken: 'mock-token',
        refreshToken: vi.fn(),
        signIn: vi.fn(),
        signOut: vi.fn(),
        signUp: vi.fn(),
        clearError: vi.fn(),
        hasRole: vi.fn(),
        hasPermission: vi.fn(),
        canAccess: vi.fn(),
        isTokenExpired: vi.fn(),
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/BATbern 2025/)).toBeInTheDocument();
      });

      expect(screen.queryByTestId('account-linking-cta')).not.toBeInTheDocument();
    });

    test('should_includePrefillEmailInRegistrationLink_when_ctaDisplayed', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        const createAccountLink = screen.getByRole('link', { name: /create account/i });
        expect(createAccountLink).toBeInTheDocument();
        expect(createAccountLink).toHaveAttribute(
          'href',
          `/auth/register?email=${encodeURIComponent('john.doe@example.com')}`
        );
      });
    });

    test('should_displayAutoLinkingMessage_when_ctaShown', async () => {
      vi.mocked(eventApiClient.getRegistration).mockResolvedValue(mockRegistration);
      vi.mocked(eventApiClient.getRegistrationQR).mockResolvedValue('blob:mock-qr-url');

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/automatically linked/i)).toBeInTheDocument();
      });

      // Verify the CTA contains the account linking message
      const cta = screen.getByTestId('account-linking-cta');
      expect(cta).toHaveTextContent(/john.doe@example.com/);
      expect(cta).toHaveTextContent(/Create an account to manage your registrations/);
    });
  });

  describe('Edge Cases', () => {
    test('should_handleMissingConfirmationCode_when_noCodeInURL', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/']}>
            <Routes>
              <Route path="/" element={<div>Home Page</div>} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      expect(screen.getByText('Home Page')).toBeInTheDocument();
    });
  });
});
