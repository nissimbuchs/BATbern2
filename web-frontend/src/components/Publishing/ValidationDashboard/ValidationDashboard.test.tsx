import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ValidationDashboard } from './ValidationDashboard';
import { useNavigate } from 'react-router-dom';

// Mock useNavigate
vi.mock('react-router-dom', () => ({
  useNavigate: vi.fn(),
}));

describe('ValidationDashboard', () => {
  const mockNavigate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useNavigate).mockReturnValue(mockNavigate);
  });

  describe('Validation Items Display', () => {
    it('should render all validation items for topic phase', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: false, errors: [] },
        sessions: { isValid: false, errors: [] },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="topic" validation={validation} />);

      expect(screen.getByText(/event topic/i)).toBeInTheDocument();
      expect(screen.getByText(/speaker lineup/i)).toBeInTheDocument();
      expect(screen.getByText(/session timings/i)).toBeInTheDocument();
    });

    it('should show checkmark for valid items', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: false, errors: [] },
        sessions: { isValid: false, errors: [] },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="topic" validation={validation} />);

      expect(screen.getByTestId('validation-check-topic')).toBeInTheDocument();
    });

    it('should show warning icon for invalid items', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: false, errors: ['No speakers accepted'] },
        sessions: { isValid: false, errors: [] },
      };

      render(
        <ValidationDashboard eventCode="BATbern142" phase="speakers" validation={validation} />
      );

      expect(screen.getByTestId('validation-warning-speakers')).toBeInTheDocument();
    });
  });

  describe('Session Timings Validation', () => {
    it('should show session timing status as Ready when all assigned', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: true,
          errors: [],
          assignedCount: 8,
          totalCount: 8,
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByText(/ready \(8\/8 sessions assigned\)/i)).toBeInTheDocument();
    });

    it('should show session timing status as Incomplete when not all assigned', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByText(/incomplete \(5\/8 sessions assigned\)/i)).toBeInTheDocument();
    });

    it('should render [Assign Timings] button when sessions incomplete', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
          unassignedSessions: ['john-doe-acme-corp', 'jane-smith-tech-inc', 'bob-jones-consulting'],
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByRole('button', { name: /assign timings/i })).toBeInTheDocument();
    });

    it('should navigate to slot assignment page when [Assign Timings] clicked', async () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      const assignButton = screen.getByRole('button', { name: /assign timings/i });
      fireEvent.click(assignButton);

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/organizer/events/BATbern142/slot-assignment');
      });
    });

    it('should not render [Assign Timings] button when all sessions assigned', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: true,
          errors: [],
          assignedCount: 8,
          totalCount: 8,
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.queryByRole('button', { name: /assign timings/i })).not.toBeInTheDocument();
    });
  });

  describe('Expandable Sub-items', () => {
    it('should show expandable arrow for session timings with unassigned sessions', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
          unassignedSessions: [
            { sessionSlug: 'john-doe-acme-corp', title: 'John Doe - Acme Corp' },
            { sessionSlug: 'jane-smith-tech-inc', title: 'Jane Smith - Tech Inc' },
            { sessionSlug: 'bob-jones-consulting', title: 'Bob Jones - Consulting' },
          ],
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByTestId('expand-sessions-button')).toBeInTheDocument();
    });

    it('should expand to show unassigned session list when clicked', async () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
          unassignedSessions: [
            { sessionSlug: 'john-doe-acme-corp', title: 'John Doe - Acme Corp' },
            { sessionSlug: 'jane-smith-tech-inc', title: 'Jane Smith - Tech Inc' },
            { sessionSlug: 'bob-jones-consulting', title: 'Bob Jones - Consulting' },
          ],
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      const expandButton = screen.getByTestId('expand-sessions-button');
      fireEvent.click(expandButton);

      await waitFor(() => {
        expect(screen.getByText(/john doe - acme corp/i)).toBeInTheDocument();
        expect(screen.getByText(/jane smith - tech inc/i)).toBeInTheDocument();
        expect(screen.getByText(/bob jones - consulting/i)).toBeInTheDocument();
      });
    });

    it('should collapse unassigned session list when clicked again', async () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
          unassignedSessions: [
            { sessionSlug: 'john-doe-acme-corp', title: 'John Doe - Acme Corp' },
          ],
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      const expandButton = screen.getByTestId('expand-sessions-button');
      fireEvent.click(expandButton); // Expand

      await waitFor(() => {
        expect(screen.getByText(/john doe - acme corp/i)).toBeInTheDocument();
      });

      fireEvent.click(expandButton); // Collapse

      await waitFor(() => {
        expect(screen.queryByText(/john doe - acme corp/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Phase-specific Requirements', () => {
    it('should show only topic validation for Phase 1', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: false, errors: [] },
        sessions: { isValid: false, errors: [] },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="topic" validation={validation} />);

      expect(screen.getByText(/event topic/i)).toBeInTheDocument();
      // Speakers and sessions should be visible but marked as not required for this phase
      expect(screen.getByTestId('validation-item-speakers')).toHaveAttribute(
        'data-required',
        'false'
      );
      expect(screen.getByTestId('validation-item-sessions')).toHaveAttribute(
        'data-required',
        'false'
      );
    });

    it('should require topic and speakers for Phase 2', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: { isValid: false, errors: [] },
      };

      render(
        <ValidationDashboard eventCode="BATbern142" phase="speakers" validation={validation} />
      );

      expect(screen.getByTestId('validation-item-topic')).toHaveAttribute('data-required', 'true');
      expect(screen.getByTestId('validation-item-speakers')).toHaveAttribute(
        'data-required',
        'true'
      );
      expect(screen.getByTestId('validation-item-sessions')).toHaveAttribute(
        'data-required',
        'false'
      );
    });

    it('should require all items for Phase 3 (Agenda)', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: { isValid: true, errors: [] },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByTestId('validation-item-topic')).toHaveAttribute('data-required', 'true');
      expect(screen.getByTestId('validation-item-speakers')).toHaveAttribute(
        'data-required',
        'true'
      );
      expect(screen.getByTestId('validation-item-sessions')).toHaveAttribute(
        'data-required',
        'true'
      );
    });
  });

  describe('Overall Validation Status', () => {
    it('should show overall status as Ready when all required items valid', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: { isValid: true, errors: [] },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByTestId('overall-validation-status')).toHaveTextContent(
        /ready to publish/i
      );
    });

    it('should show overall status as Not Ready when any required item invalid', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByTestId('overall-validation-status')).toHaveTextContent(/not ready/i);
    });
  });

  describe('Real-time Updates', () => {
    it('should update validation status when props change', async () => {
      const initialValidation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
        },
      };

      const { rerender } = render(
        <ValidationDashboard eventCode="BATbern142" phase="agenda" validation={initialValidation} />
      );

      expect(screen.getByText(/incomplete \(5\/8 sessions assigned\)/i)).toBeInTheDocument();

      const updatedValidation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: true,
          errors: [],
          assignedCount: 8,
          totalCount: 8,
        },
      };

      rerender(
        <ValidationDashboard eventCode="BATbern142" phase="agenda" validation={updatedValidation} />
      );

      await waitFor(() => {
        expect(screen.getByText(/ready \(8\/8 sessions assigned\)/i)).toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    it('should have accessible labels for validation items', () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: { isValid: false, errors: [] },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      expect(screen.getByLabelText(/event topic validation/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/speaker lineup validation/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/session timings validation/i)).toBeInTheDocument();
    });

    it('should announce validation status changes to screen readers', async () => {
      const validation = {
        topic: { isValid: true, errors: [] },
        speakers: { isValid: true, errors: [] },
        sessions: {
          isValid: false,
          errors: ['Not all sessions have timing assigned'],
          assignedCount: 5,
          totalCount: 8,
        },
      };

      render(<ValidationDashboard eventCode="BATbern142" phase="agenda" validation={validation} />);

      const announcement = screen.getByRole('status', { hidden: true });
      expect(announcement).toHaveTextContent(/not ready to publish/i);
    });
  });
});
