/**
 * ProfileUpdatePage Tests (Story 6.2b)
 *
 * Tests for the speaker profile update page.
 */

import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import ProfileUpdatePage from '../ProfileUpdatePage';
import { speakerPortalService, SpeakerProfile } from '@/services/speakerPortalService';

// Mock the speaker portal service
vi.mock('@/services/speakerPortalService', () => ({
  speakerPortalService: {
    getProfile: vi.fn(),
    updateProfile: vi.fn(),
  },
}));

// Mock PublicLayout to simplify tests (avoid AuthProvider dependency)
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const mockProfile: SpeakerProfile = {
  username: 'jane.speaker',
  email: 'jane@techcorp.ch',
  firstName: 'Jane',
  lastName: 'Speaker',
  bio: 'Expert cloud architect with 10 years experience.',
  profilePictureUrl: 'https://cdn.batbern.ch/users/jane.speaker/profile.jpg',
  expertiseAreas: ['Cloud Architecture', 'Microservices'],
  speakingTopics: ['AWS', 'Kubernetes'],
  linkedInUrl: 'https://linkedin.com/in/janespeaker',
  languages: ['de', 'en'],
  profileCompleteness: 100,
  missingFields: [],
};

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

const renderWithProviders = (token: string | null = 'valid-token') => {
  const queryClient = createTestQueryClient();
  const initialEntries = token
    ? [`/speaker-portal/profile?token=${token}`]
    : ['/speaker-portal/profile'];

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/speaker-portal/profile" element={<ProfileUpdatePage />} />
          <Route path="/" element={<div>Home Page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('ProfileUpdatePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Loading State', () => {
    it('should show loading state initially', () => {
      vi.mocked(speakerPortalService.getProfile).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      renderWithProviders();

      expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
      expect(screen.getByText(/loading profile/i)).toBeInTheDocument();
    });
  });

  describe('Error States', () => {
    it('should show error when no token provided', () => {
      renderWithProviders(null);

      expect(screen.getByText(/invalid link/i)).toBeInTheDocument();
      expect(screen.getByText(/requires a valid profile link/i)).toBeInTheDocument();
    });

    it('should show error when token is expired', async () => {
      const expiredError = new Error('Token has expired') as Error & { errorCode: string };
      expiredError.errorCode = 'EXPIRED';
      vi.mocked(speakerPortalService.getProfile).mockRejectedValue(expiredError);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/link expired/i)).toBeInTheDocument();
      });
    });

    it('should show error when token is not found', async () => {
      const notFoundError = new Error('Token not found') as Error & { errorCode: string };
      notFoundError.errorCode = 'NOT_FOUND';
      vi.mocked(speakerPortalService.getProfile).mockRejectedValue(notFoundError);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/invalid link/i)).toBeInTheDocument();
      });
    });
  });

  describe('Profile Display', () => {
    it('should display profile data when loaded', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue('Jane')).toBeInTheDocument();
        expect(screen.getByDisplayValue('Speaker')).toBeInTheDocument();
        expect(screen.getByDisplayValue('jane@techcorp.ch')).toBeInTheDocument();
        expect(screen.getByDisplayValue(/expert cloud architect/i)).toBeInTheDocument();
      });
    });

    it('should display profile completeness', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText('100%')).toBeInTheDocument();
      });
    });

    it('should show missing fields alert when profile incomplete', async () => {
      const incompleteProfile = {
        ...mockProfile,
        profileCompleteness: 30,
        missingFields: ['bio', 'profilePictureUrl', 'expertiseAreas'],
      };
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(incompleteProfile);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/complete your profile/i)).toBeInTheDocument();
        expect(screen.getByText(/bio, profilePictureUrl, expertiseAreas/i)).toBeInTheDocument();
      });
    });
  });

  describe('Profile Editing', () => {
    it('should enable save button when changes made', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);
      const user = userEvent.setup();

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue('Jane')).toBeInTheDocument();
      });

      const firstNameInput = screen.getByLabelText(/first name/i);
      await user.clear(firstNameInput);
      await user.type(firstNameInput, 'Janet');

      const saveButton = screen.getByRole('button', { name: /save changes/i });
      expect(saveButton).not.toBeDisabled();
    });

    it('should call updateProfile on save', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);
      vi.mocked(speakerPortalService.updateProfile).mockResolvedValue(mockProfile);
      const user = userEvent.setup();

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue('Jane')).toBeInTheDocument();
      });

      const firstNameInput = screen.getByLabelText(/first name/i);
      await user.clear(firstNameInput);
      await user.type(firstNameInput, 'Janet');

      const saveButton = screen.getByRole('button', { name: /save changes/i });
      await user.click(saveButton);

      await waitFor(() => {
        expect(speakerPortalService.updateProfile).toHaveBeenCalled();
      });
    });

    it('should preserve hidden fields (expertise, topics, languages) in save payload', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);
      vi.mocked(speakerPortalService.updateProfile).mockResolvedValue(mockProfile);
      const user = userEvent.setup();

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue('Jane')).toBeInTheDocument();
      });

      const firstNameInput = screen.getByLabelText(/first name/i);
      await user.clear(firstNameInput);
      await user.type(firstNameInput, 'Janet');

      const saveButton = screen.getByRole('button', { name: /save changes/i });
      await user.click(saveButton);

      await waitFor(() => {
        expect(speakerPortalService.updateProfile).toHaveBeenCalledWith(
          expect.objectContaining({
            expertiseAreas: ['Cloud Architecture', 'Microservices'],
            speakingTopics: ['AWS', 'Kubernetes'],
            languages: ['de', 'en'],
          })
        );
      });
    });

    it('should show success message after save', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);
      vi.mocked(speakerPortalService.updateProfile).mockResolvedValue(mockProfile);
      const user = userEvent.setup();

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue('Jane')).toBeInTheDocument();
      });

      const firstNameInput = screen.getByLabelText(/first name/i);
      await user.clear(firstNameInput);
      await user.type(firstNameInput, 'Janet');

      const saveButton = screen.getByRole('button', { name: /save changes/i });
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/profile updated successfully/i)).toBeInTheDocument();
      });
    });
  });

  describe('Validation', () => {
    it('should show error when bio exceeds 500 characters', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);
      const user = userEvent.setup();

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue(/expert cloud architect/i)).toBeInTheDocument();
      });

      const bioInput = screen.getByLabelText(/bio/i);
      // Use fireEvent.change for long text to avoid timeout with userEvent.type
      fireEvent.change(bioInput, { target: { value: 'x'.repeat(501) } });

      const saveButton = screen.getByRole('button', { name: /save changes/i });
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/must not exceed 500 characters/i)).toBeInTheDocument();
      });
    });

    it('should show error when LinkedIn URL is invalid', async () => {
      vi.mocked(speakerPortalService.getProfile).mockResolvedValue(mockProfile);
      const user = userEvent.setup();

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue(/linkedin.com/i)).toBeInTheDocument();
      });

      const linkedInInput = screen.getByLabelText(/linkedin/i);
      await user.clear(linkedInInput);
      await user.type(linkedInInput, 'https://twitter.com/invalid');

      const saveButton = screen.getByRole('button', { name: /save changes/i });
      await user.click(saveButton);

      await waitFor(() => {
        expect(screen.getByText(/must be a valid linkedin url/i)).toBeInTheDocument();
      });
    });
  });
});
