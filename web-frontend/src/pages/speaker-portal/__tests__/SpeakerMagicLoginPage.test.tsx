/**
 * SpeakerMagicLoginPage Component Tests (Story 9.1)
 *
 * Tests for the JWT magic link authentication landing page.
 * Covers: loading state, JWT validation, redirect on success, error display.
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SpeakerMagicLoginPage from '../SpeakerMagicLoginPage';
import { speakerAuthService } from '@/services/speakerAuthService';

// Mock speakerAuthService
vi.mock('@/services/speakerAuthService', () => ({
  speakerAuthService: {
    validateMagicLink: vi.fn(),
  },
}));

// Mock PublicLayout to simplify tests
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('SpeakerMagicLoginPage Component', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const renderPage = (jwt?: string) => {
    const initialEntry = jwt
      ? `/speaker-portal/magic-login?jwt=${jwt}`
      : '/speaker-portal/magic-login';

    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/speaker-portal/magic-login" element={<SpeakerMagicLoginPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
    mockNavigate.mockClear();
  });

  describe('Error States', () => {
    test('should_showError_when_noJwtParam', () => {
      renderPage(); // No JWT

      expect(screen.getByText('Link ungültig')).toBeInTheDocument();
      expect(
        screen.getByText('Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator.')
      ).toBeInTheDocument();
    });

    test('should_showError_when_backendReturns401', async () => {
      const error = {
        response: {
          status: 401,
          data: {
            error: 'INVALID_TOKEN',
            message: 'Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator.',
          },
        },
      };
      vi.mocked(speakerAuthService.validateMagicLink).mockRejectedValue(error);

      renderPage('invalid.jwt.token');

      await waitFor(() => {
        expect(screen.getByText('Link ungültig')).toBeInTheDocument();
      });
      expect(
        screen.getByText('Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator.')
      ).toBeInTheDocument();
    });

    test('should_showOrganizerEmail_when_errorDisplayed', () => {
      renderPage(); // No JWT - triggers error state immediately

      expect(screen.getByText('events@batbern.ch')).toBeInTheDocument();
    });
  });

  describe('Success Flow', () => {
    test('should_redirectToDashboard_when_validJwt', async () => {
      vi.mocked(speakerAuthService.validateMagicLink).mockResolvedValue({
        speakerPoolId: '123e4567-e89b-12d3-a456-426614174000',
        speakerName: 'John Doe',
        eventCode: 'BAT2026',
        eventTitle: 'BATbern 2026',
        sessionToken: 'opaque-session-token-xyz',
      });

      renderPage('valid.jwt.token');

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith(
          '/speaker-portal/dashboard?token=opaque-session-token-xyz'
        );
      });
    });

    test('should_callValidateMagicLink_with_jwtFromUrl', async () => {
      vi.mocked(speakerAuthService.validateMagicLink).mockResolvedValue({
        speakerPoolId: '123e4567-e89b-12d3-a456-426614174000',
        speakerName: 'John Doe',
        eventCode: 'BAT2026',
        eventTitle: 'BATbern 2026',
        sessionToken: 'opaque-session-token-xyz',
      });

      renderPage('my.jwt.token');

      await waitFor(() => {
        expect(speakerAuthService.validateMagicLink).toHaveBeenCalledWith('my.jwt.token');
      });
    });
  });
});
