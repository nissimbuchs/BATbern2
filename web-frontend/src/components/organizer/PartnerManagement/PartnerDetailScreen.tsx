/* eslint-disable @typescript-eslint/no-explicit-any */
import React from 'react';
import { useParams } from 'react-router-dom';
import {
  Box,
  Container,
  CircularProgress,
  Alert,
  AlertTitle,
  Skeleton,
  Stack,
} from '@mui/material';
import { usePartnerDetail } from '@/hooks/usePartnerDetail';
import { usePartnerDetailStore } from '@/stores/partnerDetailStore';
import { PartnerDetailHeader } from './PartnerDetailHeader';
import { PartnerQuickStats } from './PartnerQuickStats';
import { PartnerTabNavigation } from './PartnerTabNavigation';
import { PartnerOverviewTab } from './PartnerOverviewTab';
import { PartnerMeetingsTab } from './PartnerMeetingsTab';
import PartnerActivityTab from './PartnerActivityTab';
import PartnerNotesTab from './PartnerNotesTab';
import { PartnerSettingsTab } from './PartnerSettingsTab';
import { PartnerCreateEditModal } from './PartnerCreateEditModal';

/**
 * PartnerDetailScreen Component
 * Main screen integrating all partner detail components with tabbed interface
 */
export const PartnerDetailScreen: React.FC = () => {
  const { companyName } = useParams<{ companyName: string }>();
  // const navigate = useNavigate(); // Removed: handleBack was removed
  const activeTab = usePartnerDetailStore((state) => state.activeTab);
  const setActiveTab = usePartnerDetailStore((state) => state.setActiveTab);

  // Fetch partner detail with enriched data
  const {
    data: partner,
    isLoading,
    isError,
    error,
  } = usePartnerDetail(companyName || '', 'company,contacts,votes,meetings,activity');

  // Handle tab change
  const handleTabChange = (newTab: number) => {
    setActiveTab(newTab);
  };

  // Handle status and auto-renewal updates (callbacks for Settings tab)
  const handleUpdateStatus = (isActive: boolean) => {
    // TODO: Implement status update mutation (Epic 8)
    console.log('Update status:', isActive);
  };

  const handleUpdateAutoRenewal = (autoRenewal: boolean) => {
    // TODO: Implement auto-renewal update mutation (Epic 8)
    console.log('Update auto-renewal:', autoRenewal);
  };

  // Mock current user (TODO: Get from auth context)
  const currentUser = {
    username: 'organizer1',
    role: 'ORGANIZER' as const,
  };

  // Loading state
  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress role="progressbar" />
        </Box>
        <Stack spacing={2} sx={{ mt: 4 }}>
          <Skeleton variant="rectangular" height={200} />
          <Skeleton variant="rectangular" height={100} />
          <Skeleton variant="rectangular" height={400} />
        </Stack>
      </Container>
    );
  }

  // Error state - 404
  if (isError && (error as any)?.response?.status === 404) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error">
          <AlertTitle>Partner Not Found</AlertTitle>
          The partner "{companyName}" could not be found.
        </Alert>
      </Container>
    );
  }

  // Error state - Other errors
  if (isError) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error">
          <AlertTitle>Error Loading Partner</AlertTitle>
          An error occurred while loading the partner details. Please try again later.
        </Alert>
      </Container>
    );
  }

  // No partner data
  if (!partner) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="warning">
          <AlertTitle>No Data</AlertTitle>
          No partner data available.
        </Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }} data-testid="partner-detail-container">
      {/* Header */}
      <PartnerDetailHeader partner={partner} />

      {/* Quick Stats */}
      <Box sx={{ mt: 3 }}>
        <PartnerQuickStats partner={partner} />
      </Box>

      {/* Tab Navigation */}
      <Box sx={{ mt: 3 }}>
        <PartnerTabNavigation activeTab={activeTab} onTabChange={handleTabChange} />
      </Box>

      {/* Tab Panels */}
      <Box sx={{ mt: 3 }}>
        {/* Overview Tab */}
        {activeTab === 0 && <PartnerOverviewTab partner={partner} />}

        {/* Contacts Tab - TODO: Story 2.8.4 */}
        {activeTab === 1 && (
          <Alert severity="info" sx={{ my: 2 }}>
            <AlertTitle>Contacts Management</AlertTitle>
            Contact management will be implemented in Story 2.8.4
          </Alert>
        )}

        {/* Meetings Tab */}
        {activeTab === 2 && <PartnerMeetingsTab companyName={partner.companyName} />}

        {/* Activity Tab */}
        {activeTab === 3 && <PartnerActivityTab companyName={partner.companyName} />}

        {/* Notes Tab */}
        {activeTab === 4 && <PartnerNotesTab companyName={partner.companyName} />}

        {/* Settings Tab */}
        {activeTab === 5 && (
          <PartnerSettingsTab
            partner={partner}
            currentUser={currentUser}
            onUpdateStatus={handleUpdateStatus}
            onUpdateAutoRenewal={handleUpdateAutoRenewal}
          />
        )}
      </Box>

      {/* Edit Partner Modal */}
      <PartnerCreateEditModal />
    </Container>
  );
};
