import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { VersionControl } from './VersionControl';
import * as usePublishingHook from '@/hooks/usePublishing/usePublishing';

// Mock usePublishing hook
vi.mock('@/hooks/usePublishing/usePublishing');

const mockVersionHistory = [
  {
    id: '123e4567-e89b-12d3-a456-426614174003',
    eventCode: 'BATbern142',
    versionNumber: 3,
    publishedPhase: 'AGENDA',
    publishedAt: '2025-01-15T14:00:00Z',
    publishedBy: 'john.doe',
    cdnInvalidationId: 'INV123',
    cdnInvalidationStatus: 'COMPLETED',
    isCurrent: true,
  },
  {
    id: '123e4567-e89b-12d3-a456-426614174002',
    eventCode: 'BATbern142',
    versionNumber: 2,
    publishedPhase: 'SPEAKERS',
    publishedAt: '2025-01-15T12:00:00Z',
    publishedBy: 'john.doe',
    cdnInvalidationId: 'INV122',
    cdnInvalidationStatus: 'COMPLETED',
    isCurrent: false,
  },
  {
    id: '123e4567-e89b-12d3-a456-426614174001',
    eventCode: 'BATbern142',
    versionNumber: 1,
    publishedPhase: 'TOPIC',
    publishedAt: '2025-01-15T10:00:00Z',
    publishedBy: 'admin.user',
    cdnInvalidationId: 'INV121',
    cdnInvalidationStatus: 'COMPLETED',
    isCurrent: false,
  },
];

const mockUsePublishing = {
  versionHistory: mockVersionHistory,
  rollbackVersion: vi.fn(),
  isLoadingVersions: false,
  isRollingBack: false,
  rollbackError: null,
};

describe('VersionControl', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(usePublishingHook.usePublishing).mockReturnValue(mockUsePublishing);
  });

  describe('Version History Table', () => {
    it('should render version history table', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByRole('table')).toBeInTheDocument();
      expect(screen.getByText(/version/i)).toBeInTheDocument();
      expect(screen.getByText(/published/i)).toBeInTheDocument();
      expect(screen.getByText(/phase/i)).toBeInTheDocument();
      expect(screen.getByText(/status/i)).toBeInTheDocument();
      expect(screen.getByText(/actions/i)).toBeInTheDocument();
    });

    it('should display all versions in table', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByText(/version 3/i)).toBeInTheDocument();
      expect(screen.getByText(/version 2/i)).toBeInTheDocument();
      expect(screen.getByText(/version 1/i)).toBeInTheDocument();
    });

    it('should show version publish dates', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByText(/jan 15, 2025.*2:00 pm/i)).toBeInTheDocument();
      expect(screen.getByText(/jan 15, 2025.*12:00 pm/i)).toBeInTheDocument();
      expect(screen.getByText(/jan 15, 2025.*10:00 am/i)).toBeInTheDocument();
    });

    it('should show published phase for each version', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByText(/agenda/i)).toBeInTheDocument();
      expect(screen.getByText(/speakers/i)).toBeInTheDocument();
      expect(screen.getByText(/topic/i)).toBeInTheDocument();
    });

    it('should show publisher username for each version', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getAllByText(/john\.doe/i)).toHaveLength(2);
      expect(screen.getByText(/admin\.user/i)).toBeInTheDocument();
    });

    it('should mark current version with badge', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByTestId('current-version-badge')).toBeInTheDocument();
    });

    it('should sort versions by version number descending (newest first)', () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rows = screen.getAllByRole('row');
      // First row is header, second should be version 3 (newest)
      expect(rows[1]).toHaveTextContent(/version 3/i);
      expect(rows[2]).toHaveTextContent(/version 2/i);
      expect(rows[3]).toHaveTextContent(/version 1/i);
    });
  });

  describe('CDN Invalidation Status', () => {
    it('should show CDN cleared status for completed invalidations', () => {
      render(<VersionControl eventCode="BATbern142" />);

      const statusIcons = screen.getAllByTestId('cdn-status-completed');
      expect(statusIcons).toHaveLength(3);
    });

    it('should show CDN pending status for in-progress invalidations', () => {
      const versionWithPending = [
        {
          ...mockVersionHistory[0],
          cdnInvalidationStatus: 'IN_PROGRESS',
        },
      ];

      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: versionWithPending,
      });

      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByTestId('cdn-status-in-progress')).toBeInTheDocument();
    });

    it('should show CDN failed status for failed invalidations', () => {
      const versionWithFailed = [
        {
          ...mockVersionHistory[0],
          cdnInvalidationStatus: 'FAILED',
        },
      ];

      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: versionWithFailed,
      });

      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByTestId('cdn-status-failed')).toBeInTheDocument();
    });

    it('should show tooltip with CDN invalidation ID on hover', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const statusIcon = screen.getByTestId('cdn-status-completed');
      fireEvent.mouseEnter(statusIcon);

      await waitFor(() => {
        expect(screen.getByText(/invalidation id: inv123/i)).toBeInTheDocument();
      });
    });
  });

  describe('Rollback Button', () => {
    it('should render rollback button for non-current versions', () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      expect(rollbackButtons).toHaveLength(2); // Version 2 and 1, not current version 3
    });

    it('should not render rollback button for current version', () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rows = screen.getAllByRole('row');
      // Current version (row 1) should not have rollback button
      expect(rows[1]).not.toHaveTextContent(/rollback/i);
    });

    it('should open confirmation modal when rollback button clicked', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]); // Click rollback for version 2

      await waitFor(() => {
        expect(screen.getByTestId('rollback-confirmation-modal')).toBeInTheDocument();
      });
    });

    it('should disable rollback button when rolling back', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        isRollingBack: true,
      });

      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      rollbackButtons.forEach((button) => {
        expect(button).toBeDisabled();
      });
    });
  });

  describe('Rollback Confirmation Modal', () => {
    it('should show version details in confirmation modal', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]); // Rollback to version 2

      await waitFor(() => {
        expect(screen.getByText(/rollback to version 2/i)).toBeInTheDocument();
        expect(screen.getByText(/speakers phase/i)).toBeInTheDocument();
        expect(screen.getByText(/jan 15, 2025/i)).toBeInTheDocument();
      });
    });

    it('should require reason field in modal', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]);

      await waitFor(() => {
        expect(screen.getByLabelText(/reason for rollback/i)).toBeInTheDocument();
      });
    });

    it('should validate reason field has minimum 10 characters', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]);

      await waitFor(() => {
        const reasonField = screen.getByLabelText(/reason for rollback/i);
        fireEvent.change(reasonField, { target: { value: 'Short' } });
      });

      const confirmButton = screen.getByRole('button', { name: /confirm rollback/i });
      expect(confirmButton).toBeDisabled();
    });

    it('should validate reason field has maximum 500 characters', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]);

      await waitFor(() => {
        const reasonField = screen.getByLabelText(/reason for rollback/i);
        fireEvent.change(reasonField, { target: { value: 'a'.repeat(501) } });
      });

      const confirmButton = screen.getByRole('button', { name: /confirm rollback/i });
      expect(confirmButton).toBeDisabled();
    });

    it('should call rollbackVersion when confirm button clicked', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]); // Rollback to version 2

      await waitFor(() => {
        const reasonField = screen.getByLabelText(/reason for rollback/i);
        fireEvent.change(reasonField, {
          target: { value: 'Incorrect speaker information published' },
        });
      });

      const confirmButton = screen.getByRole('button', { name: /confirm rollback/i });
      fireEvent.click(confirmButton);

      await waitFor(() => {
        expect(mockUsePublishing.rollbackVersion).toHaveBeenCalledWith(2, {
          reason: 'Incorrect speaker information published',
        });
      });
    });

    it('should close modal when cancel button clicked', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]);

      await waitFor(() => {
        expect(screen.getByTestId('rollback-confirmation-modal')).toBeInTheDocument();
      });

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      fireEvent.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByTestId('rollback-confirmation-modal')).not.toBeInTheDocument();
      });
    });
  });

  describe('Loading State', () => {
    it('should show loading skeleton when versions are loading', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: [],
        isLoadingVersions: true,
      });

      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByTestId('version-history-skeleton')).toBeInTheDocument();
    });

    it('should hide table when loading', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: [],
        isLoadingVersions: true,
      });

      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });
  });

  describe('Empty State', () => {
    it('should show empty state when no versions exist', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: [],
      });

      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByText(/no publishing versions yet/i)).toBeInTheDocument();
    });

    it('should show message to publish content in empty state', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: [],
      });

      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByText(/publish content to create versions/i)).toBeInTheDocument();
    });
  });

  describe('Rollback History', () => {
    it('should show rollback indicator for rolled back versions', () => {
      const versionWithRollback = [
        {
          ...mockVersionHistory[0],
          rolledBackAt: '2025-01-16T10:00:00Z',
          rolledBackBy: 'admin.user',
        },
      ];

      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: versionWithRollback,
      });

      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByTestId('rollback-indicator')).toBeInTheDocument();
    });

    it('should show rollback reason in tooltip', async () => {
      const versionWithRollback = [
        {
          ...mockVersionHistory[0],
          rolledBackAt: '2025-01-16T10:00:00Z',
          rolledBackBy: 'admin.user',
          rollbackReason: 'Incorrect speaker information',
        },
      ];

      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: versionWithRollback,
      });

      render(<VersionControl eventCode="BATbern142" />);

      const rollbackIndicator = screen.getByTestId('rollback-indicator');
      fireEvent.mouseEnter(rollbackIndicator);

      await waitFor(() => {
        expect(screen.getByText(/incorrect speaker information/i)).toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    it('should have accessible labels for version table', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByLabelText(/publishing version history/i)).toBeInTheDocument();
    });

    it('should announce rollback success to screen readers', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]);

      await waitFor(() => {
        const reasonField = screen.getByLabelText(/reason for rollback/i);
        fireEvent.change(reasonField, { target: { value: 'Test rollback reason' } });
      });

      const confirmButton = screen.getByRole('button', { name: /confirm rollback/i });
      fireEvent.click(confirmButton);

      await waitFor(() => {
        const announcement = screen.getByRole('status', { hidden: true });
        expect(announcement).toHaveTextContent(/rolling back to version/i);
      });
    });
  });
});
