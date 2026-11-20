/**
 * CompanyDetailView with its real panels and hooks. The network is the MSW harness, i18n the real
 * `en` (moved off own-code mocks with #1251 story 6, touch rule).
 */
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { CompanyDetailView } from '@/components/shared/Company/CompanyDetailView';
import { renderWithProviders } from '@/test/test-utils';
import { api } from '@/test/msw/api';

// Mock company data - aligned with backend CompanyResponse
const mockCompany = {
  id: 'company-123',
  name: 'TestCompanyAG',
  displayName: 'Test Company AG',
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
};

const renderWithRouter = (component: React.ReactElement) =>
  renderWithProviders(
    <Routes>
      <Route path="/organizer/companies" element={<div>Company list page</div>} />
      <Route path="*" element={component} />
    </Routes>,
    { initialEntries: ['/organizer/companies/TestCompanyAG'] }
  );

describe('CompanyDetailView Component', () => {
  const mockOnEdit = vi.fn();
  const mockOnBack = vi.fn();

  describe('AC8.1: Load company detail when company card clicked', () => {
    it('should_loadCompanyDetail_when_companyCardClicked', async () => {
      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
      );

      // Verify company name is displayed in heading
      expect(screen.getByRole('heading', { name: 'Test Company AG' })).toBeInTheDocument();

      // Verify company details are displayed
      expect(screen.getByText(/Cloud Computing/)).toBeInTheDocument();
      // Location removed - no longer supported by backend

      // Verify description is displayed
      expect(screen.getByText(/A test company for unit testing/)).toBeInTheDocument();
    });

    it('should_displayCompanyLogo_when_logoUrlProvided', () => {
      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
      );

      const logo = screen.getByAltText(/Test Company AG logo/i);
      expect(logo).toBeInTheDocument();
      expect(logo).toHaveAttribute('src', 'https://cdn.example.com/logos/test-company.png');
    });

    it('should_displayVerifiedBadge_when_companyVerified', () => {
      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
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

      expect(mockOnEdit).toHaveBeenCalledWith('TestCompanyAG');
    });
  });

  describe('Back navigation', () => {
    it('should_displayBackButton_when_detailViewLoaded', () => {
      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
      );

      // Component uses breadcrumbs for navigation instead of back button
      expect(screen.getByRole('navigation', { name: /breadcrumb/i })).toBeInTheDocument();
      expect(screen.getByText('Company Management')).toBeInTheDocument();
    });

    it('should_callOnBack_when_backButtonClicked', async () => {
      const user = userEvent.setup();

      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
      );

      // Component uses breadcrumbs - click the breadcrumb link to navigate back
      const breadcrumbLink = screen.getByText('Company Management');
      await user.click(breadcrumbLink);

      expect(await screen.findByText('Company list page')).toBeInTheDocument();
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
    it('should_renderDetailView_when_mobileViewport', () => {
      // MUI handles responsive layout via sx prop, not CSS class names
      global.innerWidth = 375;
      global.dispatchEvent(new Event('resize'));

      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
      );

      const detailView = screen.getByTestId('company-detail-view');
      expect(detailView).toBeInTheDocument();
    });
  });

  describe('People tabs are organizer-only', () => {
    it('should_hidePeopleTabs_when_callerIsNotOrganizer', async () => {
      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
      );

      expect(screen.getAllByRole('tab')).toHaveLength(1);
      // the sessions panel loads (positive control); the user directory is never asked
      await waitFor(() => expect(api.calls('searchSessionsByCompany').length).toBeGreaterThan(0));
      expect(api.calls('listUsers')).toHaveLength(0);
    });

    it('should_showUsersSpeakersAndSessionsTabs_when_callerIsOrganizer', async () => {
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          showPeople={true}
          onEdit={mockOnEdit}
          onBack={mockOnBack}
        />
      );

      expect(screen.getAllByRole('tab')).toHaveLength(3);
      await waitFor(() => expect(api.calls('listUsers').length).toBeGreaterThan(0));
    });
  });

  describe('Merge with another company (#1251)', () => {
    it('should_offerMerge_when_onMergeGiven', async () => {
      const onMerge = vi.fn();
      renderWithRouter(
        <CompanyDetailView
          company={mockCompany}
          onEdit={mockOnEdit}
          onMerge={onMerge}
          onBack={mockOnBack}
        />
      );

      await userEvent.click(screen.getByRole('button', { name: 'Merge with another company' }));

      expect(onMerge).toHaveBeenCalled();
    });

    it('should_notOfferMerge_when_noOnMerge', () => {
      renderWithRouter(
        <CompanyDetailView company={mockCompany} onEdit={mockOnEdit} onBack={mockOnBack} />
      );

      expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
      expect(screen.queryByTestId('company-merge-button')).not.toBeInTheDocument();
    });
  });
});
