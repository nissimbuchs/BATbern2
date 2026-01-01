import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PublishingControls } from './PublishingControls';
import * as usePublishingHook from '@/hooks/usePublishing/usePublishing';

// Mock react-i18next with proper translations
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'publishing.controls.mode': 'Publishing Mode',
        'publishing.controls.modeDraft': 'Draft',
        'publishing.controls.modeProgressive': 'Progressive',
        'publishing.controls.modeComplete': 'Complete',
        'publishing.controls.notifySubscribers': 'Notify subscribers when publishing',
        'publishing.controls.publishPhases': 'Publish Phases',
        'publishing.controls.publishPhase': params?.phase ? `Publish ${params.phase}` : 'Publish',
        'publishing.controls.published': params?.phase ? `${params.phase} Published` : 'Published',
        'publishing.controls.publishing': 'Publishing',
        'publishing.controls.schedulePublish': 'Schedule Publish',
        'publishing.controls.previewNewsletter': 'Preview Newsletter',
        'publishing.controls.validationErrors': 'Validation Errors',
        'publishing.controls.scheduleDialog.title': 'Schedule Auto-Publish',
        'publishing.controls.scheduleDialog.confirm': 'Confirm Schedule',
        'publishing.controls.scheduleDialog.cancel': 'Cancel',
        'publishing.controls.scheduleDialog.selectDate': 'Select date and time',
        'publishing.controls.accessibility.publishingMode': 'Publishing Mode',
        'publishing.controls.accessibility.notifySubscribers': 'Notify subscribers when publishing',
        'publishing.controls.accessibility.publishingPhase': `Publishing ${params?.phase || 'phase'}`,
        'publishing.controls.phase.topic': 'Topic',
        'publishing.controls.phase.speakers': 'Speakers',
        'publishing.controls.phase.agenda': 'Agenda',
      };
      return translations[key] || key;
    },
    i18n: {
      language: 'en',
      changeLanguage: vi.fn(),
    },
  }),
}));

// Mock usePublishing hook
vi.mock('@/hooks/usePublishing/usePublishing');

const mockUsePublishing = {
  publishPhase: vi.fn(),
  unpublishPhase: vi.fn(),
  scheduleAutoPublish: vi.fn(),
  cancelAutoPublish: vi.fn(),
  isPublishing: false,
  isUnpublishing: false,
  isScheduling: false,
  publishError: null,
  validationErrors: [],
  preview: null,
  publishingStatus: {
    currentPhase: null,
    publishedPhases: [],
    topic: { isValid: true, errors: [] },
    speakers: { isValid: true, errors: [] },
    sessions: {
      isValid: true,
      errors: [],
      assignedCount: 0,
      totalCount: 0,
      unassignedSessions: [],
    },
  },
};

describe('PublishingControls', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(usePublishingHook.usePublishing).mockReturnValue(mockUsePublishing);
  });

  describe('Mode Selection', () => {
    it('should render mode selection radio buttons', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      expect(screen.getByRole('radio', { name: /draft/i })).toBeInTheDocument();
      expect(screen.getByRole('radio', { name: /progressive/i })).toBeInTheDocument();
      expect(screen.getByRole('radio', { name: /complete/i })).toBeInTheDocument();
    });

    it('should select progressive mode by default', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      const progressiveRadio = screen.getByRole('radio', { name: /progressive/i });
      expect(progressiveRadio).toBeChecked();
    });

    it('should update mode selection when radio button clicked', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      const draftRadio = screen.getByRole('radio', { name: /draft/i });
      fireEvent.click(draftRadio);

      await waitFor(() => {
        expect(draftRadio).toBeChecked();
      });
    });
  });

  describe('Publish Button', () => {
    it('should render publish button for topic phase', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      expect(screen.getByTestId('publish-topic-button')).toBeInTheDocument();
    });

    it('should render publish button for speakers phase', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      expect(screen.getByTestId('publish-speakers-button')).toBeInTheDocument();
    });

    it('should render publish button for agenda phase', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="agenda" />);

      expect(screen.getByTestId('publish-agenda-button')).toBeInTheDocument();
    });

    it('should call publishPhase when publish button clicked', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      const publishButton = screen.getByTestId('publish-topic-button');
      fireEvent.click(publishButton);

      await waitFor(() => {
        expect(mockUsePublishing.publishPhase).toHaveBeenCalledWith('topic', {
          mode: 'progressive',
          notifySubscribers: true,
        });
      });
    });

    it('should disable publish button when publishing in progress', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        isPublishing: true,
      });

      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      const publishButton = screen.getByTestId('publish-topic-button');
      expect(publishButton).toBeDisabled();
    });

    it('should disable publish button when validation errors exist', () => {
      const validationErrors = [
        {
          field: 'sessions.timing',
          message: 'Not all sessions have timing assigned',
          requirement: 'All sessions must have startTime and endTime for agenda phase',
        },
      ];

      render(
        <PublishingControls
          eventCode="BATbern142"
          currentPhase="agenda"
          validationErrors={validationErrors}
        />
      );

      const publishButton = screen.getByTestId('publish-agenda-button');
      expect(publishButton).toBeDisabled();
    });

    it('should show loading state when publishing', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        isPublishing: true,
      });

      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      // Check that the publish button has a loading spinner
      const publishButton = screen.getByTestId('publish-topic-button');
      expect(publishButton).toBeInTheDocument();
    });
  });

  describe('Newsletter Notification Toggle', () => {
    it('should render newsletter notification checkbox', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      expect(
        screen.getByRole('checkbox', { name: /notify subscribers when publishing/i })
      ).toBeInTheDocument();
    });

    it('should check newsletter notification by default', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      const checkbox = screen.getByRole('checkbox', {
        name: /notify subscribers when publishing/i,
      });
      expect(checkbox).toBeChecked();
    });

    it('should toggle newsletter notification when checkbox clicked', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      const checkbox = screen.getByRole('checkbox', {
        name: /notify subscribers when publishing/i,
      });
      fireEvent.click(checkbox);

      await waitFor(() => {
        expect(checkbox).not.toBeChecked();
      });
    });

    it('should pass notifySubscribers=false when unchecked', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      const checkbox = screen.getByRole('checkbox', {
        name: /notify subscribers when publishing/i,
      });
      fireEvent.click(checkbox); // Uncheck

      const publishButton = screen.getByTestId('publish-speakers-button');
      fireEvent.click(publishButton);

      await waitFor(() => {
        expect(mockUsePublishing.publishPhase).toHaveBeenCalledWith('speakers', {
          mode: 'progressive',
          notifySubscribers: false,
        });
      });
    });
  });

  describe('Schedule Publish', () => {
    it('should render schedule publish button', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      expect(screen.getByRole('button', { name: /schedule publish/i })).toBeInTheDocument();
    });

    it('should open date-time picker when schedule button clicked', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      const scheduleButton = screen.getByRole('button', { name: /schedule publish/i });
      fireEvent.click(scheduleButton);

      await waitFor(() => {
        expect(screen.getByTestId('schedule-datetime-picker')).toBeInTheDocument();
      });
    });

    it('should call scheduleAutoPublish with selected date', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      const scheduleButton = screen.getByRole('button', { name: /schedule publish/i });
      fireEvent.click(scheduleButton);

      await waitFor(() => {
        const datePicker = screen.getByTestId('schedule-datetime-picker');
        fireEvent.change(datePicker, { target: { value: '2025-04-15T08:00' } });
      });

      const confirmButton = screen.getByRole('button', { name: /confirm schedule/i });
      fireEvent.click(confirmButton);

      await waitFor(() => {
        expect(mockUsePublishing.scheduleAutoPublish).toHaveBeenCalledWith('speakers', {
          scheduledDate: expect.stringContaining('2025-04-15'),
          notifySubscribers: true,
        });
      });
    });

    it('should disable schedule button when scheduling in progress', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        isScheduling: true,
      });

      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      const scheduleButton = screen.getByRole('button', { name: /schedule publish/i });
      expect(scheduleButton).toBeDisabled();
    });
  });

  describe('Preview Newsletter Button', () => {
    it('should render preview newsletter button', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      expect(screen.getByRole('button', { name: /preview newsletter/i })).toBeInTheDocument();
    });

    it('should open newsletter preview modal when button clicked', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="speakers" />);

      const previewButton = screen.getByRole('button', { name: /preview newsletter/i });
      fireEvent.click(previewButton);

      await waitFor(() => {
        expect(screen.getByTestId('newsletter-preview-modal')).toBeInTheDocument();
      });
    });
  });

  describe('Validation Error Display', () => {
    it('should display validation errors when provided', () => {
      const validationErrors = [
        {
          field: 'sessions.timing',
          message: 'Not all sessions have timing assigned',
          requirement: 'All sessions must have startTime and endTime for agenda phase',
        },
        {
          field: 'speakers.count',
          message: 'No speakers accepted',
          requirement: 'At least one speaker must be accepted for speakers phase',
        },
      ];

      render(
        <PublishingControls
          eventCode="BATbern142"
          currentPhase="agenda"
          validationErrors={validationErrors}
        />
      );

      expect(screen.getByText(/not all sessions have timing assigned/i)).toBeInTheDocument();
      expect(screen.getByText(/no speakers accepted/i)).toBeInTheDocument();
    });

    it('should show error icon when validation fails', () => {
      const validationErrors = [
        {
          field: 'sessions.timing',
          message: 'Not all sessions have timing assigned',
          requirement: 'All sessions must have startTime and endTime for agenda phase',
        },
      ];

      render(
        <PublishingControls
          eventCode="BATbern142"
          currentPhase="agenda"
          validationErrors={validationErrors}
        />
      );

      expect(screen.getByTestId('validation-error-icon')).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have accessible labels for all form controls', () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      expect(screen.getByLabelText(/publishing mode/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/notify subscribers/i)).toBeInTheDocument();
    });

    it('should announce publish success to screen readers', async () => {
      render(<PublishingControls eventCode="BATbern142" currentPhase="topic" />);

      const publishButton = screen.getByTestId('publish-topic-button');
      fireEvent.click(publishButton);

      await waitFor(() => {
        const announcement = screen.getByRole('status', { hidden: true });
        expect(announcement).toBeInTheDocument();
      });
    });
  });
});
