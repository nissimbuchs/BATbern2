/**
 * Speaker Outreach Details Drawer Tests (Story 6.1c - Task 3)
 *
 * Tests for Send Invitation button functionality in the drawer
 * TDD: Tests written BEFORE implementation
 *
 * Coverage:
 * - Test 1.1: should_showSendInvitationButton_when_speakerIsIdentified
 * - Test 1.2: should_hideSendInvitationButton_when_speakerIsInvited
 * - Test 1.3: should_disableSendButton_when_speakerHasNoEmail
 * - Test 1.4: should_showLoadingState_when_sendingInvitation
 * - Test 1.5: should_showSuccessToast_when_invitationSent
 * - Test 1.6: should_showErrorToast_when_invitationFails
 * - Test 1.7: should_closeDrawer_when_invitationSucceeds (optional behavior)
 * - Test 1.8: should_updateSpeakerStatus_when_invitationSent
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SpeakerOutreachDetailsDrawer from '../SpeakerOutreachDetailsDrawer';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import { speakerPoolService } from '@/services/speakerPoolService';
import { speakerOutreachService } from '@/services/speakerOutreachService';

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'speakerOutreach.outreachDetails': 'Outreach Details',
        'speakerOutreach.contactHistory': 'Contact History',
        'speakerOutreach.noContactHistory': 'No contact history yet',
        'speakerOutreach.markContacted': 'Mark Contacted',
        'speakerOutreach.contactMethod': 'Contact Method',
        'speakerOutreach.contactDate': 'Contact Date',
        'speakerOutreach.notes': 'Notes',
        'speakerOutreach.error.loadHistory': 'Failed to load contact history',
        'speakerBrainstorm.form.expertise': 'Expertise',
        'speakerOutreach.markContactedModal.title': 'Mark as Contacted',
        'speakerOutreach.markContactedModal.method.email': 'Email',
        'speakerOutreach.markContactedModal.method.phone': 'Phone',
        'speakerOutreach.markContactedModal.method.inPerson': 'In Person',
        'speakerOutreach.markContactedModal.error.methodRequired': 'Contact method is required',
        'speakerOutreach.markContactedModal.error.dateRequired': 'Contact date is required',
        'speakerOutreach.markContactedModal.error.failed': 'Failed to record outreach',
        'speakerOutreach.markContactedModal.notesPlaceholder': 'Add any notes about the contact...',
        'common:actions.cancel': 'Cancel',
        'common.saving': 'Saving...',
        // Send Invitation translations (Story 6.1c)
        'speakers.sendInvitation': 'Send Invitation',
        'speakers.sending': 'Sending...',
        'speakers.invitationSent': `Invitation sent to ${params?.email || 'speaker'}`,
        'speakers.invitationFailed': 'Failed to send invitation',
        'speakers.noEmailTooltip': 'Add email in details to invite',
        // Email input translations (AC4)
        'speakers.email': 'Email',
        'speakers.addEmail': 'Add Email',
        'speakers.saveEmail': 'Save Email',
        'speakers.invalidEmail': 'Invalid email format',
        'speakers.emailRequired': 'Email is required to send invitation',
      };
      return translations[key] || key;
    },
  }),
}));


// Mock services
vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: {
    sendInvitation: vi.fn(),
    getSpeakerPool: vi.fn(),
    addSpeakerToPool: vi.fn(),
    deleteSpeakerFromPool: vi.fn(),
  },
}));

vi.mock('@/services/speakerOutreachService', () => ({
  speakerOutreachService: {
    getOutreachHistory: vi.fn(),
    recordOutreach: vi.fn(),
    bulkRecordOutreach: vi.fn(),
  },
}));

describe('SpeakerOutreachDetailsDrawer - Send Invitation (Story 6.1c)', () => {
  let queryClient: QueryClient;
  const mockOnClose = vi.fn();
  const eventCode = 'BATbern56';

  const createSpeaker = (overrides: Partial<SpeakerPoolEntry> = {}): SpeakerPoolEntry => ({
    id: 'speaker-123',
    eventId: 'event-456',
    speakerName: 'Dr. Jane Smith',
    company: 'Tech Corp',
    expertise: 'Cloud Architecture',
    status: 'IDENTIFIED',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  });

  // Speaker with email
  const speakerWithEmail = createSpeaker({
    id: 'speaker-with-email',
    speakerName: 'Dr. Jane Smith',
    status: 'IDENTIFIED',
  });

  // Speaker without email (no email field)
  const speakerWithoutEmail = createSpeaker({
    id: 'speaker-no-email',
    speakerName: 'John Doe',
    status: 'IDENTIFIED',
  });

  // Speaker already invited
  const speakerInvited = createSpeaker({
    id: 'speaker-invited',
    speakerName: 'Alice Johnson',
    status: 'INVITED',
  });

  // Speaker accepted
  const speakerAccepted = createSpeaker({
    id: 'speaker-accepted',
    speakerName: 'Bob Wilson',
    status: 'ACCEPTED',
  });

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    vi.clearAllMocks();

    // Default mock: empty outreach history
    vi.mocked(speakerOutreachService.getOutreachHistory).mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  const renderDrawer = (speaker: SpeakerPoolEntry | null = speakerWithEmail) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <SpeakerOutreachDetailsDrawer
          open={true}
          onClose={mockOnClose}
          speaker={speaker}
          eventCode={eventCode}
        />
      </QueryClientProvider>
    );
  };

  describe('AC1: Send Invitation Button Visibility', () => {
    // Test 1.1: should_showSendInvitationButton_when_speakerIsIdentified
    it('should_showSendInvitationButton_when_speakerIsIdentified', async () => {
      // Add email to speaker for this test (email needed for button to be enabled)
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'jane@example.com' };
      renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });
    });

    // Test 1.2: should_hideSendInvitationButton_when_speakerIsInvited
    it('should_hideSendInvitationButton_when_speakerIsInvited', async () => {
      renderDrawer(speakerInvited);

      await waitFor(() => {
        expect(screen.queryByRole('button', { name: /send invitation/i })).not.toBeInTheDocument();
      });
    });

    it('should_hideSendInvitationButton_when_speakerIsAccepted', async () => {
      renderDrawer(speakerAccepted);

      await waitFor(() => {
        expect(screen.queryByRole('button', { name: /send invitation/i })).not.toBeInTheDocument();
      });
    });
  });

  describe('AC1/AC4: Email Requirement', () => {
    // Test 1.3: should_disableSendButton_when_speakerHasNoEmail
    it('should_disableSendButton_when_speakerHasNoEmail', async () => {
      renderDrawer(speakerWithoutEmail);

      await waitFor(() => {
        const sendButton = screen.queryByRole('button', { name: /send invitation/i });
        // Button should either be disabled or not present when no email
        if (sendButton) {
          expect(sendButton).toBeDisabled();
        }
      });
    });
  });

  describe('AC2: API Integration', () => {
    // Test 1.4: should_showLoadingState_when_sendingInvitation
    it('should_showLoadingState_when_sendingInvitation', async () => {
      const user = userEvent.setup();
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'jane@example.com' };

      // Create a pending promise to control timing
      let resolvePromise: (value: unknown) => void;
      const pendingPromise = new Promise((resolve) => {
        resolvePromise = resolve;
      });
      vi.mocked(speakerPoolService.sendInvitation).mockReturnValue(pendingPromise as Promise<any>);

      renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });

      const sendButton = screen.getByRole('button', { name: /send invitation/i });
      await user.click(sendButton);

      // Should show loading state
      await waitFor(() => {
        expect(screen.getByRole('button', { name: /sending/i })).toBeInTheDocument();
      });

      // Cleanup: resolve promise
      resolvePromise!({
        token: 'abc',
        workflowState: 'CONTACTED',
        invitedAt: '2026-01-25T12:00:00Z',
        email: 'jane@example.com',
      });
    });

    // Test 1.5: should_showSuccessToast_when_invitationSent
    it('should_showSuccessToast_when_invitationSent', async () => {
      const user = userEvent.setup();
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'jane@example.com' };

      vi.mocked(speakerPoolService.sendInvitation).mockResolvedValue({
        token: 'magic-token-123',
        workflowState: 'CONTACTED',
        invitedAt: '2026-01-25T12:00:00Z',
        email: 'jane@example.com',
      });

      renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });

      const sendButton = screen.getByRole('button', { name: /send invitation/i });
      await user.click(sendButton);

      // Should show success alert in Snackbar
      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
        expect(screen.getByText(/invitation sent/i)).toBeInTheDocument();
      });
    });

    // Test 1.6: should_showErrorToast_when_invitationFails
    it('should_showErrorToast_when_invitationFails', async () => {
      const user = userEvent.setup();
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'jane@example.com' };

      vi.mocked(speakerPoolService.sendInvitation).mockRejectedValue(
        new Error('SPEAKER_NOT_FOUND')
      );

      renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });

      const sendButton = screen.getByRole('button', { name: /send invitation/i });
      await user.click(sendButton);

      // Should show error alert in Snackbar
      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
        expect(screen.getByText(/failed to send invitation/i)).toBeInTheDocument();
      });
    });

    // Test 1.8: should_updateSpeakerStatus_when_invitationSent (via cache invalidation)
    it('should_callSendInvitationAPI_when_buttonClicked', async () => {
      const user = userEvent.setup();
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'jane@example.com' };

      vi.mocked(speakerPoolService.sendInvitation).mockResolvedValue({
        token: 'magic-token-123',
        workflowState: 'CONTACTED',
        invitedAt: '2026-01-25T12:00:00Z',
        email: 'jane@example.com',
      });

      renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });

      const sendButton = screen.getByRole('button', { name: /send invitation/i });
      await user.click(sendButton);

      await waitFor(() => {
        expect(speakerPoolService.sendInvitation).toHaveBeenCalledWith(
          eventCode,
          speakerWithEmailProp.id,
          undefined
        );
      });
    });
  });

  describe('Drawer Behavior', () => {
    // Test 1.7: should_closeDrawer_when_invitationSucceeds (optional - based on implementation)
    it('should_notCrash_when_drawerClosedDuringMutation', async () => {
      const user = userEvent.setup();
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'jane@example.com' };

      vi.mocked(speakerPoolService.sendInvitation).mockResolvedValue({
        token: 'magic-token-123',
        workflowState: 'CONTACTED',
        invitedAt: '2026-01-25T12:00:00Z',
        email: 'jane@example.com',
      });

      const { rerender } = renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });

      const sendButton = screen.getByRole('button', { name: /send invitation/i });
      await user.click(sendButton);

      // Close drawer while mutation is in progress
      rerender(
        <QueryClientProvider client={queryClient}>
          <SpeakerOutreachDetailsDrawer
            open={false}
            onClose={mockOnClose}
            speaker={speakerWithEmailProp}
            eventCode={eventCode}
          />
        </QueryClientProvider>
      );

      // Should not crash
      await waitFor(() => {
        expect(speakerPoolService.sendInvitation).toHaveBeenCalled();
      });
    });

    it('should_reenableButton_when_invitationFails', async () => {
      const user = userEvent.setup();
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'jane@example.com' };

      vi.mocked(speakerPoolService.sendInvitation).mockRejectedValue(new Error('Network error'));

      renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });

      const sendButton = screen.getByRole('button', { name: /send invitation/i });
      await user.click(sendButton);

      // Wait for error alert
      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });

      // Button should be re-enabled
      await waitFor(() => {
        const button = screen.getByRole('button', { name: /send invitation/i });
        expect(button).not.toBeDisabled();
      });
    });
  });

  describe('AC4: Email Input for Speakers without Email', () => {
    // Test 4.1: should_showEmailInput_when_speakerHasNoEmail
    it('should_showEmailInput_when_speakerHasNoEmail', async () => {
      renderDrawer(speakerWithoutEmail);

      await waitFor(() => {
        // Should show email input field when speaker is IDENTIFIED but has no email
        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      });
    });

    // Test 4.2: should_validateEmailFormat_when_emailEntered
    it('should_validateEmailFormat_when_emailEntered', async () => {
      const user = userEvent.setup();
      renderDrawer(speakerWithoutEmail);

      await waitFor(() => {
        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      });

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'invalid-email');

      // Trigger validation by blurring
      await user.tab();

      // Should show validation error for invalid format
      await waitFor(() => {
        expect(screen.getByText(/invalid email/i)).toBeInTheDocument();
      });
    });

    // Test 4.3: should_showValidationError_when_invalidEmail
    it('should_showValidationError_when_invalidEmail', async () => {
      const user = userEvent.setup();
      renderDrawer(speakerWithoutEmail);

      await waitFor(() => {
        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      });

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'not-an-email');
      await user.tab();

      await waitFor(() => {
        expect(screen.getByText(/invalid email/i)).toBeInTheDocument();
      });
    });

    // Test 4.4: should_enableSaveButton_when_validEmailEntered
    it('should_enableSaveButton_when_validEmailEntered', async () => {
      const user = userEvent.setup();
      renderDrawer(speakerWithoutEmail);

      await waitFor(() => {
        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      });

      const emailInput = screen.getByLabelText(/email/i);
      await user.type(emailInput, 'valid@example.com');

      // Save button should be enabled
      await waitFor(() => {
        const saveButton = screen.getByRole('button', { name: /save email/i });
        expect(saveButton).not.toBeDisabled();
      });
    });

    // Test 4.6: should_enableSendButton_after_emailSaved
    it('should_enableSendButton_after_emailSaved', async () => {
      // Speaker starts without email, we simulate adding one
      const speakerWithEmailAdded = { ...speakerWithoutEmail, email: 'added@example.com' };

      const { rerender } = renderDrawer(speakerWithoutEmail);

      await waitFor(() => {
        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      });

      // Simulate email being saved by re-rendering with email
      rerender(
        <QueryClientProvider client={queryClient}>
          <SpeakerOutreachDetailsDrawer
            open={true}
            onClose={mockOnClose}
            speaker={speakerWithEmailAdded}
            eventCode={eventCode}
          />
        </QueryClientProvider>
      );

      // Send button should now be enabled
      await waitFor(() => {
        const sendButton = screen.getByRole('button', { name: /send invitation/i });
        expect(sendButton).not.toBeDisabled();
      });
    });

    it('should_notShowEmailInput_when_speakerHasEmail', async () => {
      const speakerWithEmailProp = { ...speakerWithEmail, email: 'existing@example.com' };
      renderDrawer(speakerWithEmailProp);

      await waitFor(() => {
        // Should show send button but NOT email input when speaker already has email
        expect(screen.getByRole('button', { name: /send invitation/i })).toBeInTheDocument();
      });

      // No email input should be visible
      expect(screen.queryByRole('textbox', { name: /email/i })).not.toBeInTheDocument();
    });

    it('should_notShowEmailInput_when_speakerIsNotIdentified', async () => {
      renderDrawer(speakerInvited);

      await waitFor(() => {
        // Should NOT show email input for non-IDENTIFIED speakers
        expect(screen.queryByRole('textbox', { name: /email/i })).not.toBeInTheDocument();
      });
    });
  });
});
