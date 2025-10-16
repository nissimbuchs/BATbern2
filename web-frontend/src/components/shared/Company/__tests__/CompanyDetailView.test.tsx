import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import { CompanyDetailView } from '@/components/shared/Company/CompanyDetailView';

// Mock child components to focus on integration
vi.mock('../AssociatedUsersPanel', () => ({
  AssociatedUsersPanel: ({ companyId }: { companyId: string }) => (
    <div data-testid="associated-users-panel">AssociatedUsersPanel-{companyId}</div>
  ),
}));

// PartnerInfoPanel removed - isPartner field no longer supported by backend

vi.mock('../CompanyStatistics', () => ({
  CompanyStatistics: ({ statistics }: { statistics: any }) => (
    <div data-testid="company-statistics">Statistics: {statistics?.totalEvents}</div>
  ),
}));

vi.mock('../ActivityTimeline', () => ({
  ActivityTimeline: ({ companyId }: { companyId: string }) => (
    <div data-testid="activity-timeline">ActivityTimeline-{companyId}</div>
  ),
}));

// Mock company data - aligned with backend CompanyResponse
const mockCompany = {
  id: 'company-123',
  name: 'Test Company AG',
  displayName: 'Test Company',
  swissUID: 'CHE-123.456.789',
  website: 'https://test-company.ch',
  industry: 'Cloud Computing',
  description: 'A test company for unit testing',
  isVerified: true,
  createdAt: '2024-01-15T10:00:00Z',
  updatedAt: '2024-10-14T15:30:00Z',
  createdBy: 'auth0|user_456',
  logo: {
    url: 'https://cdn.example.com/logos/test-company.png',
    s3Key: 'logos/test-company.png',
    fileId: 'file-123',
  },
  statistics: {
    totalEvents: 5,
    totalSpeakers: 12,
    totalPartners: 3,
  },
};

const renderWithRouter = (component: React.ReactElement) => {
  return render(<BrowserRouter>{component}</BrowserRouter>);
};

describe('CompanyDetailView Component', () => {
  const mockOnEdit = vi.fn();
  const mockOnBack = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('AC8.1: Load company detail when company card clicked', () => {
    it('should_loadCompanyDetail_when_companyCardClicked', async () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify company name is displayed
      expect(screen.getByText('Test Company AG')).toBeInTheDocument();

      // Verify company details are displayed
      expect(screen.getByText(/CHE-123.456.789/)).toBeInTheDocument();
      expect(screen.getByText(/Cloud Computing/)).toBeInTheDocument();
      // Location removed - no longer supported by backend

      // Verify description is displayed
      expect(screen.getByText(/A test company for unit testing/)).toBeInTheDocument();
    });

    it('should_displayCompanyLogo_when_logoUrlProvided', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      const logo = screen.getByAltText(/Test Company AG logo/i);
      expect(logo).toBeInTheDocument();
      expect(logo).toHaveAttribute('src', 'https://cdn.example.com/logos/test-company.png');
    });

    it('should_displayVerifiedBadge_when_companyVerified', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify verified badge/icon is shown
      expect(screen.getByTestId('verified-badge')).toBeInTheDocument();
    });

    it('should_displayLoadingSkeleton_when_companyDataLoading', () => {
      renderWithRouter(
        <CompanyDetailView
          company={null}
          isLoading={true}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify skeleton loader is shown
      expect(screen.getByTestId('detail-view-skeleton')).toBeInTheDocument();
    });
  });

  // AC8.2 removed: isPartner field no longer supported by backend

  describe('AC8.3: Display associated users when users exist', () => {
    it('should_displayAssociatedUsers_when_usersExist', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify AssociatedUsersPanel is rendered
      expect(screen.getByTestId('associated-users-panel')).toBeInTheDocument();
      expect(screen.getByText(/AssociatedUsersPanel-company-123/)).toBeInTheDocument();
    });
  });

  describe('AC8.4: Display statistics when statistics loaded', () => {
    it('should_displayStatistics_when_statisticsLoaded', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify CompanyStatistics component is rendered
      expect(screen.getByTestId('company-statistics')).toBeInTheDocument();
      expect(screen.getByText(/Statistics: 5/)).toBeInTheDocument();
    });

    it('should_hideStatistics_when_noStatisticsAvailable', () => {
      const companyWithoutStats = { ...mockCompany, statistics: undefined };

      renderWithRouter(
        <CompanyDetailView
          company={companyWithoutStats}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Statistics component should still render but show empty state
      expect(screen.getByTestId('company-statistics')).toBeInTheDocument();
    });
  });

  describe('Tab navigation and layout', () => {
    it('should_displayTabNavigation_when_detailViewLoaded', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify tabs are displayed
      expect(screen.getByRole('tab', { name: /overview/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /users/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /statistics/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /activity/i })).toBeInTheDocument();
    });

    it('should_switchToUsersTab_when_usersTabClicked', async () => {
      const user = userEvent.setup();

      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      const usersTab = screen.getByRole('tab', { name: /users/i });
      await user.click(usersTab);

      // Verify users tab content is visible
      await waitFor(() => {
        expect(screen.getByTestId('associated-users-panel')).toBeVisible();
      });
    });

    it('should_switchToStatisticsTab_when_statisticsTabClicked', async () => {
      const user = userEvent.setup();

      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      const statsTab = screen.getByRole('tab', { name: /statistics/i });
      await user.click(statsTab);

      // Verify statistics tab content is visible
      await waitFor(() => {
        expect(screen.getByTestId('company-statistics')).toBeVisible();
      });
    });

    it('should_switchToActivityTab_when_activityTabClicked', async () => {
      const user = userEvent.setup();

      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      const activityTab = screen.getByRole('tab', { name: /activity/i });
      await user.click(activityTab);

      // Verify activity timeline is visible
      await waitFor(() => {
        expect(screen.getByTestId('activity-timeline')).toBeVisible();
      });
    });
  });

  describe('Edit functionality', () => {
    it('should_displayEditButton_when_userHasPermission', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
          canEdit={true}
        />
      );

      expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
    });

    it('should_hideEditButton_when_userLacksPermission', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
          canEdit={false}
        />
      );

      expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
    });

    it('should_callOnEdit_when_editButtonClicked', async () => {
      const user = userEvent.setup();

      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
          canEdit={true}
        />
      );

      const editButton = screen.getByRole('button', { name: /edit/i });
      await user.click(editButton);

      expect(mockOnEdit).toHaveBeenCalledWith('company-123');
    });
  });

  describe('Back navigation', () => {
    it('should_displayBackButton_when_detailViewLoaded', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
    });

    it('should_callOnBack_when_backButtonClicked', async () => {
      const user = userEvent.setup();

      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      const backButton = screen.getByRole('button', { name: /back/i });
      await user.click(backButton);

      expect(mockOnBack).toHaveBeenCalled();
    });
  });

  describe('Error handling', () => {
    it('should_displayErrorMessage_when_companyLoadFails', () => {
      renderWithRouter(
        <CompanyDetailView
          company={null}
          error="Failed to load company details"
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      expect(screen.getByText(/Failed to load company details/i)).toBeInTheDocument();
    });

    it('should_displayRetryButton_when_errorOccurs', async () => {
      const mockOnRetry = vi.fn();

      renderWithRouter(
        <CompanyDetailView
          company={null}
          error="Network error"
          onRetry={mockOnRetry}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      const retryButton = screen.getByRole('button', { name: /retry/i });
      expect(retryButton).toBeInTheDocument();

      const user = userEvent.setup();
      await user.click(retryButton);
      expect(mockOnRetry).toHaveBeenCalled();
    });
  });

  describe('Responsive behavior', () => {
    it('should_stackLayout_when_mobileViewport', () => {
      // Mock mobile viewport
      global.innerWidth = 375;
      global.dispatchEvent(new Event('resize'));

      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      const detailView = screen.getByTestId('company-detail-view');
      expect(detailView).toHaveClass('mobile-layout');
    });
  });
});
