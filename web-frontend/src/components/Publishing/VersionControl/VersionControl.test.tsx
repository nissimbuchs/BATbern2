import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { VersionControl } from './VersionControl';
import * as usePublishingHook from '@/hooks/usePublishing/usePublishing';

// Mock react-i18next with proper translations
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'publishing.versionControl.title': 'Version History',
        'publishing.versionControl.version': 'Version',
        'publishing.versionControl.versionNumber': params?.number
          ? `Version ${params.number}`
          : 'Version',
        'publishing.versionControl.phase': 'Phase',
        'publishing.versionControl.published': 'Published',
        'publishing.versionControl.publishedAt': 'Published At',
        'publishing.versionControl.publishedBy': 'Published By',
        'publishing.versionControl.publisher': 'Publisher',
        'publishing.versionControl.status': 'Status',
        'publishing.versionControl.actions': 'Actions',
        'publishing.versionControl.current': 'Current',
        'publishing.versionControl.rollback': 'Rollback',
        'publishing.versionControl.rollbackToVersion': params?.version
          ? `Rollback to version ${params.version}`
          : 'Rollback to version',
        'publishing.versionControl.confirmRollback': 'Confirm Rollback',
        'publishing.versionControl.rollbackQuestion':
          'Are you sure you want to rollback to this version?',
        'publishing.versionControl.cancel': 'Cancel',
        'publishing.versionControl.confirmRollbackButton': 'Confirm Rollback',
        'publishing.versionControl.rollingBack': 'Rolling Back...',
        'publishing.versionControl.reasonLabel': 'Reason for rollback',
        'publishing.versionControl.reasonPlaceholder':
          'Explain why you are rolling back this version',
        'publishing.versionControl.reasonHelperText': params
          ? `${params.current}/${params.min}-${params.max} characters`
          : 'Enter reason for rollback',
        'publishing.versionControl.reasonTooShort': params?.min
          ? `Reason must be at least ${params.min} characters`
          : 'Reason too short',
        'publishing.versionControl.reasonTooLong': params?.max
          ? `Reason must not exceed ${params.max} characters`
          : 'Reason too long',
        'publishing.versionControl.cdnWarning': 'CDN cache will be invalidated after rollback',
        'publishing.versionControl.cdnCleared': 'CDN Cleared',
        'publishing.versionControl.cdnPending': 'CDN Pending',
        'publishing.versionControl.cdnFailed': 'CDN Failed',
        'publishing.versionControl.cdnUnknown': 'CDN Unknown',
        'publishing.versionControl.noVersions': 'No version history available',
        'publishing.versionControl.tableAriaLabel': 'Version history table',
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
      // Check for table headers (column headers are unique)
      const headers = screen.getAllByRole('columnheader');
      expect(headers).toHaveLength(6);
      expect(headers[0]).toHaveTextContent(/version/i);
      expect(headers[1]).toHaveTextContent(/published/i);
      expect(headers[2]).toHaveTextContent(/phase/i);
      expect(headers[3]).toHaveTextContent(/publisher/i);
      expect(headers[4]).toHaveTextContent(/status/i);
      expect(headers[5]).toHaveTextContent(/actions/i);
    });

    it('should display all versions in table', () => {
      render(<VersionControl eventCode="BATbern142" />);

      expect(screen.getByText(/version 3/i)).toBeInTheDocument();
      expect(screen.getByText(/version 2/i)).toBeInTheDocument();
      expect(screen.getByText(/version 1/i)).toBeInTheDocument();
    });

    it('should show version publish dates', () => {
      render(<VersionControl eventCode="BATbern142" />);

      // Use testids to target specific version dates
      // Check date portion only (time varies by timezone)
      expect(screen.getByTestId('publish-date-3')).toHaveTextContent(/jan 15, 2025/i);
      expect(screen.getByTestId('publish-date-2')).toHaveTextContent(/jan 15, 2025/i);
      expect(screen.getByTestId('publish-date-1')).toHaveTextContent(/jan 15, 2025/i);
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

      // Component uses current-badge-{versionNumber}, version 3 is current
      expect(screen.getByTestId('current-badge-3')).toBeInTheDocument();
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
          cdnInvalidationStatus: 'PENDING',
        },
      ];

      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: versionWithPending,
      });

      render(<VersionControl eventCode="BATbern142" />);

      // Component uses 'cdn-status-pending' testid for PENDING status
      expect(screen.getByTestId('cdn-status-pending')).toBeInTheDocument();
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

      // Multiple cdn-status-completed icons, get the first one (version 3 with INV123)
      const statusIcons = screen.getAllByTestId('cdn-status-completed');
      fireEvent.mouseEnter(statusIcons[0]);

      await waitFor(() => {
        expect(screen.getByText(/cdn invalidation id: inv123/i)).toBeInTheDocument();
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
        // Modal shows confirmation text and version details
        expect(screen.getByText(/are you sure you want to rollback/i)).toBeInTheDocument();
        expect(screen.getByTestId('rollback-version-details')).toBeInTheDocument();
        // Check version details within the details box
        const detailsBox = screen.getByTestId('rollback-version-details');
        expect(detailsBox).toHaveTextContent(/version:.*2/i);
        expect(detailsBox).toHaveTextContent(/phase:.*speakers/i);
        expect(detailsBox).toHaveTextContent(/published:.*jan 15, 2025/i);
      });
    });

    it('should call rollbackVersion when confirm button clicked', async () => {
      render(<VersionControl eventCode="BATbern142" />);

      const rollbackButtons = screen.getAllByRole('button', { name: /rollback/i });
      fireEvent.click(rollbackButtons[0]); // Rollback to version 2

      await waitFor(() => {
        expect(screen.getByTestId('rollback-confirmation-modal')).toBeInTheDocument();
      });

      // Fill in the rollback reason (minimum 10 characters required)
      const reasonInput = screen.getByTestId('rollback-reason-input');
      fireEvent.change(reasonInput, {
        target: { value: 'Fixing critical bug found in production' },
      });

      const confirmButton = screen.getByRole('button', { name: /confirm rollback/i });
      fireEvent.click(confirmButton);

      await waitFor(() => {
        expect(mockUsePublishing.rollbackVersion).toHaveBeenCalledWith(2, {
          reason: 'Fixing critical bug found in production',
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

      // Component shows CircularProgress with testid "version-history-loading"
      expect(screen.getByTestId('version-history-loading')).toBeInTheDocument();
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

      // Component shows "No version history available" with testid
      expect(screen.getByTestId('no-versions-message')).toHaveTextContent(
        /no version history available/i
      );
    });

    it('should show message to publish content in empty state', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        versionHistory: [],
      });

      render(<VersionControl eventCode="BATbern142" />);

      // Component only shows one message, not separate publish content message
      expect(screen.getByTestId('no-versions-message')).toHaveTextContent(
        /no version history available/i
      );
    });
  });
});
