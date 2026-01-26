/**
 * InvitationResponsePage Component Tests (Story 6.2a - Task 7)
 *
 * Tests for the speaker invitation response page.
 * Covers token validation, response form, submission, and error states.
 */

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import InvitationResponsePage from '../InvitationResponsePage';
import { speakerPortalService } from '@/services/speakerPortalService';

// Mock speakerPortalService
vi.mock('@/services/speakerPortalService', () => ({
  speakerPortalService: {
    validateToken: vi.fn(),
    respond: vi.fn(),
  },
}));

// Mock PublicLayout to simplify tests
vi.mock('@/components/public/PublicLayout', () => ({
  PublicLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

describe('InvitationResponsePage Component', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  });

  const renderWithProviders = (token?: string) => {
    const initialEntries = token
      ? [`/speaker-portal/respond?token=${token}`]
      : ['/speaker-portal/respond'];

    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path="/speaker-portal/respond" element={<InvitationResponsePage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient.clear();
  });

  describe('Token Validation', () => {
    test('should_showInvalidLinkError_when_noTokenProvided', () => {
      renderWithProviders(); // No token

      expect(screen.getByText('Invalid Link')).toBeInTheDocument();
      expect(
        screen.getByText('This page requires a valid invitation link from your email.')
      ).toBeInTheDocument();
    });

    test('should_showLoadingState_when_validatingToken', async () => {
      vi.mocked(speakerPortalService.validateToken).mockImplementation(
        () => new Promise(() => {}) // Never resolves - stays in loading state
      );

      renderWithProviders('valid-token');

      expect(screen.getByText('Loading Invitation...')).toBeInTheDocument();
      expect(screen.getByText('Please wait while we verify your link.')).toBeInTheDocument();
    });

    test('should_showExpiredError_when_tokenExpired', async () => {
      const expiredError = new Error('Link expired') as Error & { errorCode?: string };
      expiredError.errorCode = 'EXPIRED';
      vi.mocked(speakerPortalService.validateToken).mockRejectedValue(expiredError);

      renderWithProviders('expired-token');

      await waitFor(() => {
        expect(screen.getByText('Link Expired')).toBeInTheDocument();
      });
      expect(
        screen.getByText('This invitation link has expired. Please contact the event organizers.')
      ).toBeInTheDocument();
    });

    test('should_showUsedError_when_tokenAlreadyUsed', async () => {
      const usedError = new Error('Token already used') as Error & { errorCode?: string };
      usedError.errorCode = 'ALREADY_USED';
      vi.mocked(speakerPortalService.validateToken).mockRejectedValue(usedError);

      renderWithProviders('used-token');

      await waitFor(() => {
        expect(screen.getByText('Link Already Used')).toBeInTheDocument();
      });
    });

    test('should_showNotFoundError_when_tokenInvalid', async () => {
      const notFoundError = new Error('Token not found') as Error & { errorCode?: string };
      notFoundError.errorCode = 'NOT_FOUND';
      vi.mocked(speakerPortalService.validateToken).mockRejectedValue(notFoundError);

      renderWithProviders('invalid-token');

      await waitFor(() => {
        expect(screen.getByText('Invalid Link')).toBeInTheDocument();
      });
    });

    test('should_showAlreadyRespondedState_when_previouslyResponded', async () => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue({
        valid: true,
        speakerName: 'John Doe',
        eventCode: 'BAT2025',
        eventTitle: 'BATbern 2025',
        eventDate: '20. November 2025',
        alreadyResponded: true,
        previousResponse: 'ACCEPTED',
        previousResponseDate: '2025-01-15T10:30:00Z',
      });

      renderWithProviders('responded-token');

      await waitFor(() => {
        expect(screen.getByText('Already Responded')).toBeInTheDocument();
      });
      expect(screen.getByText(/Accepted/i)).toBeInTheDocument();
    });
  });

  describe('Form Display', () => {
    const validInvitation = {
      valid: true,
      speakerName: 'Jane Smith',
      eventCode: 'BAT2025',
      eventTitle: 'BATbern 2025',
      eventDate: '20. November 2025',
      sessionTitle: 'Architecture Keynote',
      invitationMessage: 'We think your expertise would be perfect for this event.',
      responseDeadline: '10. November 2025',
      alreadyResponded: false,
    };

    beforeEach(() => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue(validInvitation);
    });

    test('should_renderThreeResponseButtons_when_invitationValid', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Decline/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Maybe/i })).toBeInTheDocument();
      });
    });

    test('should_displayEventDetails_when_invitationValid', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByText(/BATbern 2025/)).toBeInTheDocument();
      });
      expect(screen.getByText('20. November 2025')).toBeInTheDocument();
      expect(screen.getByText('Architecture Keynote')).toBeInTheDocument();
    });

    test('should_displayInvitationMessage_when_provided', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(
          screen.getByText(/We think your expertise would be perfect for this event/i)
        ).toBeInTheDocument();
      });
    });

    test('should_displayResponseDeadline_when_provided', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByText(/Respond by 10. November 2025/i)).toBeInTheDocument();
      });
    });

    test('should_displaySpeakerName_when_invitationValid', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByText(/Jane Smith/)).toBeInTheDocument();
      });
    });
  });

  describe('Accept Response Flow', () => {
    const validInvitation = {
      valid: true,
      speakerName: 'Jane Smith',
      eventCode: 'BAT2025',
      eventTitle: 'BATbern 2025',
      eventDate: '20. November 2025',
      alreadyResponded: false,
    };

    beforeEach(() => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue(validInvitation);
    });

    test('should_showPreferencesForm_when_acceptClicked', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));

      await waitFor(() => {
        expect(screen.getByText(/Preferred Time Slot/i)).toBeInTheDocument();
        expect(screen.getByText(/Travel Requirements/i)).toBeInTheDocument();
        expect(screen.getByText(/Technical Requirements/i)).toBeInTheDocument();
      });
    });

    test('should_enableSubmitButton_when_acceptSelected', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));

      await waitFor(() => {
        const submitButton = screen.getByRole('button', { name: /Submit Response/i });
        expect(submitButton).not.toBeDisabled();
      });
    });

    test('should_submitAcceptResponse_when_formSubmitted', async () => {
      vi.mocked(speakerPortalService.respond).mockResolvedValue({
        success: true,
        speakerName: 'Jane Smith',
        eventName: 'BATbern 2025',
        nextSteps: ['Complete your profile', 'Submit your presentation title'],
        contentDeadline: '1. November 2025',
      });

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Submit Response/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(speakerPortalService.respond).toHaveBeenCalledWith(
          expect.objectContaining({
            token: 'valid-token',
            response: 'ACCEPT',
          })
        );
      });
    });

    test('should_includePreferences_when_acceptWithPreferences', async () => {
      vi.mocked(speakerPortalService.respond).mockResolvedValue({
        success: true,
        speakerName: 'Jane Smith',
        eventName: 'BATbern 2025',
        nextSteps: ['Complete your profile'],
      });

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));

      // Fill in preferences
      await waitFor(() => {
        expect(screen.getByText(/Preferred Time Slot/i)).toBeInTheDocument();
      });

      // Select time slot preference - find the select following the label
      const timeSlotLabel = screen.getByText(/Preferred Time Slot/i);
      const timeSlotSelect = timeSlotLabel.parentElement?.querySelector('select');
      if (timeSlotSelect) {
        fireEvent.change(timeSlotSelect, { target: { value: 'morning' } });
      }

      // Fill in technical requirements
      const techInput = screen.getByPlaceholderText(/Mac adapter/i);
      fireEvent.change(techInput, { target: { value: 'Need HDMI adapter' } });

      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(speakerPortalService.respond).toHaveBeenCalledWith(
          expect.objectContaining({
            token: 'valid-token',
            response: 'ACCEPT',
            preferences: expect.objectContaining({
              timeSlot: 'morning',
              technicalRequirements: 'Need HDMI adapter',
            }),
          })
        );
      });
    });
  });

  describe('Decline Response Flow', () => {
    const validInvitation = {
      valid: true,
      speakerName: 'Jane Smith',
      eventCode: 'BAT2025',
      eventTitle: 'BATbern 2025',
      eventDate: '20. November 2025',
      alreadyResponded: false,
    };

    beforeEach(() => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue(validInvitation);
    });

    test('should_showReasonInput_when_declineClicked', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Decline/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Decline/i }));

      await waitFor(() => {
        expect(screen.getByText(/Reason for declining/i)).toBeInTheDocument();
      });
    });

    test('should_requireReason_when_declineWithoutReason', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Decline/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Decline/i }));

      await waitFor(() => {
        expect(screen.getByText(/A reason is required to decline/i)).toBeInTheDocument();
      });

      const submitButton = screen.getByRole('button', { name: /Submit Response/i });
      expect(submitButton).toBeDisabled();
    });

    test('should_enableSubmit_when_declineWithReason', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Decline/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Decline/i }));

      const reasonTextarea = screen.getByPlaceholderText(/Please let us know why/i);
      fireEvent.change(reasonTextarea, { target: { value: 'Schedule conflict' } });

      await waitFor(() => {
        const submitButton = screen.getByRole('button', { name: /Submit Response/i });
        expect(submitButton).not.toBeDisabled();
      });
    });

    test('should_submitDeclineResponse_when_reasonProvided', async () => {
      vi.mocked(speakerPortalService.respond).mockResolvedValue({
        success: true,
        speakerName: 'Jane Smith',
        eventName: 'BATbern 2025',
        nextSteps: ['Thank you for letting us know'],
      });

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Decline/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Decline/i }));

      const reasonTextarea = screen.getByPlaceholderText(/Please let us know why/i);
      fireEvent.change(reasonTextarea, { target: { value: 'Schedule conflict' } });

      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(speakerPortalService.respond).toHaveBeenCalledWith(
          expect.objectContaining({
            token: 'valid-token',
            response: 'DECLINE',
            reason: 'Schedule conflict',
          })
        );
      });
    });
  });

  describe('Tentative Response Flow', () => {
    const validInvitation = {
      valid: true,
      speakerName: 'Jane Smith',
      eventCode: 'BAT2025',
      eventTitle: 'BATbern 2025',
      eventDate: '20. November 2025',
      alreadyResponded: false,
    };

    beforeEach(() => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue(validInvitation);
    });

    test('should_showReasonInput_when_tentativeClicked', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Maybe/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Maybe/i }));

      await waitFor(() => {
        expect(screen.getByText(/What's holding you back/i)).toBeInTheDocument();
      });
    });

    test('should_requireReason_when_tentativeWithoutReason', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Maybe/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Maybe/i }));

      await waitFor(() => {
        expect(
          screen.getByText(/Please let us know what you need to confirm/i)
        ).toBeInTheDocument();
      });

      const submitButton = screen.getByRole('button', { name: /Submit Response/i });
      expect(submitButton).toBeDisabled();
    });

    test('should_submitTentativeResponse_when_reasonProvided', async () => {
      vi.mocked(speakerPortalService.respond).mockResolvedValue({
        success: true,
        speakerName: 'Jane Smith',
        eventName: 'BATbern 2025',
        nextSteps: ['Organizers will follow up'],
      });

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Maybe/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Maybe/i }));

      const reasonTextarea = screen.getByPlaceholderText(/Need to check travel dates/i);
      fireEvent.change(reasonTextarea, { target: { value: 'Awaiting budget approval' } });

      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(speakerPortalService.respond).toHaveBeenCalledWith(
          expect.objectContaining({
            token: 'valid-token',
            response: 'TENTATIVE',
            reason: 'Awaiting budget approval',
          })
        );
      });
    });
  });

  describe('Success State', () => {
    const validInvitation = {
      valid: true,
      speakerName: 'Jane Smith',
      eventCode: 'BAT2025',
      eventTitle: 'BATbern 2025',
      eventDate: '20. November 2025',
      alreadyResponded: false,
    };

    beforeEach(() => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue(validInvitation);
    });

    test('should_showSuccessMessage_when_responseSubmitted', async () => {
      vi.mocked(speakerPortalService.respond).mockResolvedValue({
        success: true,
        speakerName: 'Jane Smith',
        eventName: 'BATbern 2025',
        nextSteps: ['Complete your profile', 'Submit title and abstract'],
        contentDeadline: '1. November 2025',
      });

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));
      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(screen.getByText('Response Submitted!')).toBeInTheDocument();
      });
      expect(screen.getByText(/Thank you, Jane Smith/i)).toBeInTheDocument();
    });

    test('should_showNextSteps_when_responseSubmitted', async () => {
      vi.mocked(speakerPortalService.respond).mockResolvedValue({
        success: true,
        speakerName: 'Jane Smith',
        eventName: 'BATbern 2025',
        nextSteps: ['Complete your profile', 'Submit title and abstract'],
      });

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));
      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(screen.getByText('Complete your profile')).toBeInTheDocument();
        expect(screen.getByText('Submit title and abstract')).toBeInTheDocument();
      });
    });

    test('should_showContentDeadline_when_provided', async () => {
      vi.mocked(speakerPortalService.respond).mockResolvedValue({
        success: true,
        speakerName: 'Jane Smith',
        eventName: 'BATbern 2025',
        nextSteps: [],
        contentDeadline: '1. November 2025',
      });

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));
      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(
          screen.getByText(/Content submission deadline: 1. November 2025/i)
        ).toBeInTheDocument();
      });
    });
  });

  describe('Error Handling', () => {
    const validInvitation = {
      valid: true,
      speakerName: 'Jane Smith',
      eventCode: 'BAT2025',
      eventTitle: 'BATbern 2025',
      eventDate: '20. November 2025',
      alreadyResponded: false,
    };

    beforeEach(() => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue(validInvitation);
    });

    test('should_showError_when_submissionFails', async () => {
      vi.mocked(speakerPortalService.respond).mockRejectedValue(new Error('Network error'));

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));
      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(screen.getByText(/Network error/i)).toBeInTheDocument();
      });
    });

    test('should_showAlreadyRespondedError_when_409Conflict', async () => {
      const conflictError = new Error('Already responded') as Error & {
        errorCode?: string;
        previousResponse?: string;
      };
      conflictError.errorCode = 'ALREADY_RESPONDED';
      conflictError.previousResponse = 'ACCEPTED';
      vi.mocked(speakerPortalService.respond).mockRejectedValue(conflictError);

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));
      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(screen.getByText(/Already responded/i)).toBeInTheDocument();
      });
    });

    test('should_showLoadingState_when_submitting', async () => {
      vi.mocked(speakerPortalService.respond).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Accept/i }));
      fireEvent.click(screen.getByRole('button', { name: /Submit Response/i }));

      await waitFor(() => {
        expect(screen.getByText('Submitting...')).toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    const validInvitation = {
      valid: true,
      speakerName: 'Jane Smith',
      eventCode: 'BAT2025',
      eventTitle: 'BATbern 2025',
      eventDate: '20. November 2025',
      alreadyResponded: false,
    };

    beforeEach(() => {
      vi.mocked(speakerPortalService.validateToken).mockResolvedValue(validInvitation);
    });

    test('should_haveLoadingAriaLabel_when_loading', () => {
      vi.mocked(speakerPortalService.validateToken).mockImplementation(() => new Promise(() => {}));

      renderWithProviders('valid-token');

      expect(screen.getByRole('status', { name: /loading invitation/i })).toBeInTheDocument();
    });

    test('should_haveAccessibleButtons_when_formDisplayed', async () => {
      renderWithProviders('valid-token');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Accept/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Decline/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Maybe/i })).toBeInTheDocument();
      });
    });
  });
});
