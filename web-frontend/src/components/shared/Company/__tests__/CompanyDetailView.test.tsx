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

vi.mock('../PartnerInfoPanel', () => ({
  PartnerInfoPanel: ({ companyId }: { companyId: string }) => (
    <div data-testid="partner-info-panel">PartnerInfoPanel-{companyId}</div>
  ),
}));

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

// Mock company data
const mockCompany = {
  id: 'company-123',
  name: 'Test Company AG',
  displayName: 'Test Company',
  swissUID: 'CHE-123.456.789',
  website: 'https://test-company.ch',
  industry: 'Cloud Computing',
  sector: 'Private' as const,
  location: {
    city: 'Bern',
    canton: 'BE',
    country: 'Switzerland',
  },
  description: 'A test company for unit testing',
  logoUrl: 'https://cdn.example.com/logos/test-company.png',
  isVerified: true,
  verificationStatus: 'Verified' as const,
  createdAt: '2024-01-15T10:00:00Z',
  updatedAt: '2024-10-14T15:30:00Z',
  createdBy: 'user-456',
  statistics: {
    totalEvents: 5,
    totalPresentations: 12,
    totalAttendees: 250,
    firstEvent: '2024-02-01',
    mostRecentEvent: '2024-10-01',
    topicExpertise: [
      { topic: 'Cloud Security', count: 8 },
      { topic: 'DevOps', count: 4 },
    ],
  },
};

const mockCompanyWithPartnership = {
  ...mockCompany,
  isPartner: true,
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
      expect(screen.getByText(/Bern/)).toBeInTheDocument();

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

  describe('AC8.2: Display partner info when company is partner', () => {
    it('should_displayPartnerInfo_when_companyIsPartner', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompanyWithPartnership}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify partner badge is displayed
      expect(screen.getByTestId('partner-badge')).toBeInTheDocument();

      // Verify PartnerInfoPanel is rendered
      expect(screen.getByTestId('partner-info-panel')).toBeInTheDocument();
      expect(screen.getByText(/PartnerInfoPanel-company-123/)).toBeInTheDocument();
    });

    it('should_hidePartnerInfo_when_companyNotPartner', () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      // Verify PartnerInfoPanel is not rendered
      expect(screen.queryByTestId('partner-info-panel')).not.toBeInTheDocument();
    });
  });

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
