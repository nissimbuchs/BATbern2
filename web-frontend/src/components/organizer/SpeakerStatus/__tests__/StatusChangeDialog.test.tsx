/**
 * Status Change Dialog Tests (Story 5.4)
 *
 * Tests for StatusChangeDialog component
 * Coverage:
 * - Dialog open/close behavior
 * - Reason input validation (max 2000 characters)
 * - Confirm/cancel actions
 * - i18n translations
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StatusChangeDialog } from '../StatusChangeDialog';

// Mock i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: any) => {
      const translations: Record<string, string> = {
        'organizer:speakerStatus.changeStatus': 'Change Status',
        'organizer:speakerStatus.confirmChangeMessage': `Change status of ${params?.speaker} from ${params?.from} to ${params?.to}?`,
        'organizer:speakerStatus.changeReason': 'Reason for status change (optional)',
        'organizer:speakerStatus.confirmChange': 'Change Status',
        'organizer:speakerStatus.cancelChange': 'Cancel',
        'common:actions.cancel': 'Cancel',
        'organizer:speakerStatus.reasonHelperText': `Optional (max ${params?.max} characters)`,
        'organizer:speakerStatus.reasonPlaceholder': 'e.g., Speaker confirmed via email',
        'organizer:speakerStatus.reasonTooLong': `Reason is too long (${params?.current}/${params?.max} characters)`,
        'organizer:speakerStatus.open': 'Open',
        'organizer:speakerStatus.contacted': 'Contacted',
        'organizer:speakerStatus.ready': 'Ready',
        'organizer:speakerStatus.accepted': 'Accepted',
      };
      return translations[key] || key;
    },
  }),
}));

describe('StatusChangeDialog', () => {
  const mockOnConfirm = vi.fn();
  const mockOnCancel = vi.fn();

  const defaultProps = {
    open: true,
    speakerName: 'Dr. Jane Smith',
    currentStatus: 'ready' as const,
    newStatus: 'accepted' as const,
    onConfirm: mockOnConfirm,
    onCancel: mockOnCancel,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe('Dialog Display', () => {
    it('should render when open is true', () => {
      render(<StatusChangeDialog {...defaultProps} />);

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText(/Change status of Dr. Jane Smith/)).toBeInTheDocument();
    });

    it('should not render when open is false', () => {
      render(<StatusChangeDialog {...defaultProps} open={false} />);

      expect(screen.queryByText('Change Status')).not.toBeInTheDocument();
    });

    it('should display speaker name and status transition', () => {
      render(<StatusChangeDialog {...defaultProps} />);

      expect(screen.getByText(/Dr. Jane Smith/)).toBeInTheDocument();
      expect(screen.getByText(/Ready/)).toBeInTheDocument();
      expect(screen.getByText(/Accepted/)).toBeInTheDocument();
    });

    it('should display reason input field', () => {
      render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText('Reason for status change (optional)');
      expect(reasonInput).toBeInTheDocument();
    });

    it('should display confirm and cancel buttons', () => {
      render(<StatusChangeDialog {...defaultProps} />);

      expect(screen.getByRole('button', { name: /Change Status/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Cancel/i })).toBeInTheDocument();
    });
  });

  describe('Reason Input', () => {
    it('should allow entering reason text', async () => {
      const user = userEvent.setup();
      render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText('Reason for status change (optional)');
      await user.type(reasonInput, 'Speaker confirmed availability via email');

      expect(reasonInput).toHaveValue('Speaker confirmed availability via email');
    });

    it('should validate reason length (max 2000 characters)', async () => {
      render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText(
        'Reason for status change (optional)'
      ) as HTMLTextAreaElement;
      const longReason = 'a'.repeat(2001);

      fireEvent.change(reasonInput, { target: { value: longReason } });

      await waitFor(() => {
        expect(screen.getByText(/Reason is too long/)).toBeInTheDocument();
      });
    });

    it('should disable confirm button when reason exceeds max length', async () => {
      render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText(
        'Reason for status change (optional)'
      ) as HTMLTextAreaElement;
      const confirmButton = screen.getByRole('button', { name: /Change Status/i });

      expect(confirmButton).not.toBeDisabled();

      const longReason = 'a'.repeat(2001);
      fireEvent.change(reasonInput, { target: { value: longReason } });

      await waitFor(() => {
        expect(confirmButton).toBeDisabled();
      });
    });

    it('should clear error when reason is shortened', async () => {
      const user = userEvent.setup();
      render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText(
        'Reason for status change (optional)'
      ) as HTMLTextAreaElement;

      // Set long reason
      const longReason = 'a'.repeat(2001);
      fireEvent.change(reasonInput, { target: { value: longReason } });

      await waitFor(() => {
        expect(screen.getByText(/Reason is too long/)).toBeInTheDocument();
      });

      // Set shorter reason
      await user.clear(reasonInput);
      await user.type(reasonInput, 'Valid reason');

      await waitFor(() => {
        expect(screen.queryByText(/Reason is too long/)).not.toBeInTheDocument();
      });
    });
  });

  describe('Confirm Action', () => {
    it('should call onConfirm with reason when confirm button clicked', async () => {
      const user = userEvent.setup();
      render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText('Reason for status change (optional)');
      await user.type(reasonInput, 'Speaker confirmed via email');

      const confirmButton = screen.getByRole('button', { name: /Change Status/i });
      await user.click(confirmButton);

      expect(mockOnConfirm).toHaveBeenCalledWith('Speaker confirmed via email');
      expect(mockOnConfirm).toHaveBeenCalledTimes(1);
    });

    it('should call onConfirm with undefined when no reason provided', async () => {
      const user = userEvent.setup();
      render(<StatusChangeDialog {...defaultProps} />);

      const confirmButton = screen.getByRole('button', { name: /Change Status/i });
      await user.click(confirmButton);

      expect(mockOnConfirm).toHaveBeenCalledWith(undefined);
    });

    it('should trim whitespace from reason', async () => {
      const user = userEvent.setup();
      render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText('Reason for status change (optional)');
      await user.type(reasonInput, '  Speaker confirmed  ');

      const confirmButton = screen.getByRole('button', { name: /Change Status/i });
      await user.click(confirmButton);

      expect(mockOnConfirm).toHaveBeenCalledWith('Speaker confirmed');
    });

    it('should clear reason after confirm', async () => {
      const user = userEvent.setup();
      const { rerender } = render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText('Reason for status change (optional)');
      await user.type(reasonInput, 'Test reason');

      const confirmButton = screen.getByRole('button', { name: /Change Status/i });
      await user.click(confirmButton);

      // Reopen dialog
      rerender(<StatusChangeDialog {...defaultProps} open={false} />);
      rerender(<StatusChangeDialog {...defaultProps} open={true} />);

      const newReasonInput = screen.getByLabelText('Reason for status change (optional)');
      expect(newReasonInput).toHaveValue('');
    });
  });

  describe('Cancel Action', () => {
    it('should call onCancel when cancel button clicked', async () => {
      const user = userEvent.setup();
      render(<StatusChangeDialog {...defaultProps} />);

      const cancelButton = screen.getByRole('button', { name: /Cancel/i });
      await user.click(cancelButton);

      expect(mockOnCancel).toHaveBeenCalledTimes(1);
      expect(mockOnConfirm).not.toHaveBeenCalled();
    });

    it('should clear reason when cancelled', async () => {
      const user = userEvent.setup();
      const { rerender } = render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText('Reason for status change (optional)');
      await user.type(reasonInput, 'Test reason');

      const cancelButton = screen.getByRole('button', { name: /Cancel/i });
      await user.click(cancelButton);

      // Reopen dialog
      rerender(<StatusChangeDialog {...defaultProps} open={false} />);
      rerender(<StatusChangeDialog {...defaultProps} open={true} />);

      const newReasonInput = screen.getByLabelText('Reason for status change (optional)');
      expect(newReasonInput).toHaveValue('');
    });

    it('should clear error when cancelled', async () => {
      const user = userEvent.setup();
      const { rerender } = render(<StatusChangeDialog {...defaultProps} />);

      const reasonInput = screen.getByLabelText(
        'Reason for status change (optional)'
      ) as HTMLTextAreaElement;
      const longReason = 'a'.repeat(2001);
      fireEvent.change(reasonInput, { target: { value: longReason } });

      await waitFor(() => {
        expect(screen.getByText(/Reason is too long/)).toBeInTheDocument();
      });

      const cancelButton = screen.getByRole('button', { name: /Cancel/i });
      await user.click(cancelButton);

      // Reopen dialog
      rerender(<StatusChangeDialog {...defaultProps} open={false} />);
      rerender(<StatusChangeDialog {...defaultProps} open={true} />);

      expect(screen.queryByText(/Reason is too long/)).not.toBeInTheDocument();
    });
  });

  describe('Status Transitions', () => {
    it('should display different status transitions', () => {
      const { rerender } = render(<StatusChangeDialog {...defaultProps} />);

      expect(screen.getByText(/Ready/)).toBeInTheDocument();
      expect(screen.getByText(/Accepted/)).toBeInTheDocument();

      rerender(<StatusChangeDialog {...defaultProps} currentStatus="open" newStatus="contacted" />);

      expect(screen.getByText(/Open/)).toBeInTheDocument();
      expect(screen.getByText(/Contacted/)).toBeInTheDocument();
    });
  });
});
