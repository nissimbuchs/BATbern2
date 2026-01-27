/**
 * ContentSubmissionPage Tests (Story 6.3)
 *
 * Tests for the speaker content submission page.
 * RED Phase: Tests written before implementation.
 */

import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import ContentSubmissionPage from '../ContentSubmissionPage';
import type { SpeakerContentInfo } from '@/services/speakerPortalService';

// Mock the speaker portal service
const mockGetContentInfo = vi.fn();
const mockSaveDraft = vi.fn();
const mockSubmitContent = vi.fn();

vi.mock('@/services/speakerPortalService', () => ({
  speakerPortalService: {
    getContentInfo: (...args: unknown[]) => mockGetContentInfo(...args),
    saveDraft: (...args: unknown[]) => mockSaveDraft(...args),
    submitContent: (...args: unknown[]) => mockSubmitContent(...args),
  },
}));

// Mock PublicLayout to simplify tests (avoid AuthProvider dependency)
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

// Mock PresentationUpload to simplify tests
vi.mock('@/components/speaker-portal/PresentationUpload', () => ({
  default: () => <div data-testid="presentation-upload">Presentation Upload</div>,
}));

const mockContentInfoWithSession: SpeakerContentInfo = {
  speakerName: 'Jane Speaker',
  eventCode: 'bat-bern-2026-spring',
  eventTitle: 'BATbern Spring 2026',
  hasSessionAssigned: true,
  sessionTitle: 'Cloud Architecture Best Practices',
  canSubmitContent: true,
  contentStatus: 'PENDING',
  hasDraft: false,
  draftTitle: null,
  draftAbstract: null,
  draftVersion: null,
  lastSavedAt: null,
  needsRevision: false,
  reviewerFeedback: null,
  reviewedAt: null,
  reviewedBy: null,
  hasMaterial: false,
  materialUrl: null,
  materialFileName: null,
};

const mockContentInfoNoSession: SpeakerContentInfo = {
  speakerName: 'Jane Speaker',
  eventCode: 'bat-bern-2026-spring',
  eventTitle: 'BATbern Spring 2026',
  hasSessionAssigned: false,
  sessionTitle: null,
  canSubmitContent: false,
  contentStatus: null,
  hasDraft: false,
  draftTitle: null,
  draftAbstract: null,
  draftVersion: null,
  lastSavedAt: null,
  needsRevision: false,
  reviewerFeedback: null,
  reviewedAt: null,
  reviewedBy: null,
  hasMaterial: false,
  materialUrl: null,
  materialFileName: null,
};

const mockContentInfoWithDraft: SpeakerContentInfo = {
  ...mockContentInfoWithSession,
  hasDraft: true,
  draftTitle: 'My Draft Title',
  draftAbstract: 'My draft abstract content',
  draftVersion: 1,
  lastSavedAt: '2026-01-27T10:00:00Z',
};

const mockContentInfoNeedsRevision: SpeakerContentInfo = {
  ...mockContentInfoWithSession,
  contentStatus: 'REVISION_NEEDED',
  needsRevision: true,
  reviewerFeedback: 'Please add more details about implementation',
  reviewedAt: '2026-01-26T10:00:00Z',
  reviewedBy: 'organizer.test',
  hasDraft: true,
  draftTitle: 'Original Title',
  draftAbstract: 'Original abstract',
  draftVersion: 1,
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
    ? [`/speaker-portal/content?token=${token}`]
    : ['/speaker-portal/content'];

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/speaker-portal/content" element={<ContentSubmissionPage />} />
          <Route path="/" element={<div>Home Page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('ContentSubmissionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Loading State', () => {
    it('should show loading state initially', () => {
      mockGetContentInfo.mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      renderWithProviders();

      expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
      expect(screen.getByText(/loading content/i)).toBeInTheDocument();
    });
  });

  describe('Error States', () => {
    it('should show error when no token provided', () => {
      renderWithProviders(null);

      expect(screen.getByText(/invalid link/i)).toBeInTheDocument();
      expect(screen.getByText(/requires a valid content link/i)).toBeInTheDocument();
    });

    it('should show error when token is expired', async () => {
      const expiredError = new Error('Token has expired') as Error & { errorCode: string };
      expiredError.errorCode = 'EXPIRED';
      mockGetContentInfo.mockRejectedValue(expiredError);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /link has expired/i })).toBeInTheDocument();
      });
    });

    it('should show error when token is invalid', async () => {
      const invalidError = new Error('Invalid token') as Error & { errorCode: string };
      invalidError.errorCode = 'NOT_FOUND';
      mockGetContentInfo.mockRejectedValue(invalidError);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/invalid link/i)).toBeInTheDocument();
      });
    });
  });

  // AC1: Session Assignment Check
  describe('AC1: Session Assignment Check', () => {
    it('should show blocked message when no session assigned', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoNoSession);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/session not assigned/i)).toBeInTheDocument();
        expect(screen.getByText(/contact.*organizer/i)).toBeInTheDocument();
      });
    });

    it('should show content form when session is assigned', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/cloud architecture best practices/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/presentation title/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/abstract/i)).toBeInTheDocument();
      });
    });
  });

  // AC2: Title Input
  describe('AC2: Title Input', () => {
    it('should show title input with 200 character limit', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);

      renderWithProviders();

      await waitFor(() => {
        const titleInput = screen.getByLabelText(/presentation title/i);
        expect(titleInput).toBeInTheDocument();
        expect(titleInput).toHaveAttribute('maxLength', '200');
      });
    });

    it('should show character counter for title', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/0.*\/.*200/)).toBeInTheDocument();
      });
    });
  });

  // AC3: Abstract Input
  describe('AC3: Abstract Input', () => {
    it('should show abstract textarea with 1000 character limit', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);

      renderWithProviders();

      await waitFor(() => {
        const abstractInput = screen.getByLabelText(/abstract/i);
        expect(abstractInput).toBeInTheDocument();
        expect(abstractInput).toHaveAttribute('maxLength', '1000');
      });
    });

    it('should show character counter for abstract', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/0.*\/.*1000/)).toBeInTheDocument();
      });
    });
  });

  // AC4: Draft Restoration
  describe('AC4: Draft Restoration', () => {
    it('should restore draft content on page load', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithDraft);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByDisplayValue('My Draft Title')).toBeInTheDocument();
        expect(screen.getByDisplayValue('My draft abstract content')).toBeInTheDocument();
      });
    });

    it('should show last saved timestamp when draft exists', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithDraft);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/last saved/i)).toBeInTheDocument();
      });
    });
  });

  // AC4: Auto-Save
  describe('AC4: Auto-Save', () => {
    beforeEach(() => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should auto-save draft every 30 seconds', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);
      mockSaveDraft.mockResolvedValue({
        draftId: 'draft-123',
        savedAt: new Date().toISOString(),
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByLabelText(/presentation title/i)).toBeInTheDocument();
      });

      // Type in the title field
      const titleInput = screen.getByLabelText(/presentation title/i);
      await act(async () => {
        fireEvent.change(titleInput, { target: { value: 'Test Title' } });
      });

      // Fast-forward 30 seconds
      await act(async () => {
        vi.advanceTimersByTime(30000);
      });

      await waitFor(() => {
        expect(mockSaveDraft).toHaveBeenCalled();
      });
    });

    it('should show save indicator when auto-saving', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);
      let resolvePromise: (value: { draftId: string; savedAt: string }) => void;
      mockSaveDraft.mockImplementation(
        () =>
          new Promise((resolve) => {
            resolvePromise = resolve;
          })
      );

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByLabelText(/presentation title/i)).toBeInTheDocument();
      });

      // Type in the title field
      const titleInput = screen.getByLabelText(/presentation title/i);
      await act(async () => {
        fireEvent.change(titleInput, { target: { value: 'Test Title' } });
      });

      // Fast-forward 30 seconds to trigger auto-save
      await act(async () => {
        vi.advanceTimersByTime(30000);
      });

      // Should show saving indicator
      expect(screen.getByText(/saving/i)).toBeInTheDocument();

      // Resolve the save promise
      await act(async () => {
        resolvePromise!({ draftId: 'draft-123', savedAt: new Date().toISOString() });
      });
    });
  });

  // AC5: Content Submission
  describe('AC5: Content Submission', () => {
    it('should submit content when form is valid', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);
      mockSubmitContent.mockResolvedValue({
        submissionId: 'sub-123',
        version: 1,
        status: 'SUBMITTED',
        sessionTitle: 'Cloud Architecture Best Practices',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByLabelText(/presentation title/i)).toBeInTheDocument();
      });

      // Fill in the form
      const titleInput = screen.getByLabelText(/presentation title/i);
      const abstractInput = screen.getByLabelText(/abstract/i);

      await act(async () => {
        fireEvent.change(titleInput, { target: { value: 'My Presentation Title' } });
        fireEvent.change(abstractInput, {
          target: { value: 'This is my presentation abstract with details.' },
        });
      });

      // Submit
      const submitButton = screen.getByRole('button', { name: /submit/i });
      await act(async () => {
        fireEvent.click(submitButton);
      });

      await waitFor(() => {
        expect(mockSubmitContent).toHaveBeenCalledWith({
          token: 'valid-token',
          title: 'My Presentation Title',
          contentAbstract: 'This is my presentation abstract with details.',
        });
      });
    });

    it('should show validation error when title is empty', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByLabelText(/abstract/i)).toBeInTheDocument();
      });

      // Fill only abstract
      const abstractInput = screen.getByLabelText(/abstract/i);
      await act(async () => {
        fireEvent.change(abstractInput, { target: { value: 'This is my abstract.' } });
      });

      // Try to submit
      const submitButton = screen.getByRole('button', { name: /submit/i });
      await act(async () => {
        fireEvent.click(submitButton);
      });

      await waitFor(() => {
        expect(screen.getByText(/title is required/i)).toBeInTheDocument();
      });
    });

    it('should show validation error when abstract is empty', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByLabelText(/presentation title/i)).toBeInTheDocument();
      });

      // Fill only title
      const titleInput = screen.getByLabelText(/presentation title/i);
      await act(async () => {
        fireEvent.change(titleInput, { target: { value: 'My Title' } });
      });

      // Try to submit
      const submitButton = screen.getByRole('button', { name: /submit/i });
      await act(async () => {
        fireEvent.click(submitButton);
      });

      await waitFor(() => {
        expect(screen.getByText(/abstract is required/i)).toBeInTheDocument();
      });
    });

    it('should show success message after submission', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoWithSession);
      mockSubmitContent.mockResolvedValue({
        submissionId: 'sub-123',
        version: 1,
        status: 'SUBMITTED',
        sessionTitle: 'Cloud Architecture Best Practices',
      });

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByLabelText(/presentation title/i)).toBeInTheDocument();
      });

      // Fill in the form
      const titleInput = screen.getByLabelText(/presentation title/i);
      const abstractInput = screen.getByLabelText(/abstract/i);

      await act(async () => {
        fireEvent.change(titleInput, { target: { value: 'My Presentation Title' } });
        fireEvent.change(abstractInput, { target: { value: 'This is my presentation abstract.' } });
      });

      // Submit
      const submitButton = screen.getByRole('button', { name: /submit/i });
      await act(async () => {
        fireEvent.click(submitButton);
      });

      await waitFor(() => {
        expect(screen.getByText(/submitted successfully/i)).toBeInTheDocument();
      });
    });
  });

  // AC8: Revision Support
  describe('AC8: Revision Support', () => {
    it('should show revision feedback when status is REVISION_NEEDED', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoNeedsRevision);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/revision requested/i)).toBeInTheDocument();
        expect(
          screen.getByText(/please add more details about implementation/i)
        ).toBeInTheDocument();
      });
    });

    it('should show reviewer info when revision is needed', async () => {
      mockGetContentInfo.mockResolvedValue(mockContentInfoNeedsRevision);

      renderWithProviders();

      await waitFor(() => {
        expect(screen.getByText(/organizer.test/i)).toBeInTheDocument();
      });
    });
  });
});
