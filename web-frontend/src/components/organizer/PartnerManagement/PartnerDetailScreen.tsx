/* eslint-disable @typescript-eslint/no-explicit-any */
import React, { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Box,
  Container,
  Alert,
  AlertTitle,
  Skeleton,
  Stack,
  Paper,
  BottomNavigation,
  BottomNavigationAction,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import {
  Dashboard as OverviewIcon,
  Contacts as ContactsIcon,
  Event as MeetingsIcon,
  BarChart as AnalyticsIcon,
  Note as NotesIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { usePartnerDetail } from '@/hooks/usePartnerDetail';
import { usePartnerDetailStore } from '@/stores/partnerDetailStore';
import { useAuth } from '@/hooks/useAuth';
import { PartnerDetailHeader } from './PartnerDetailHeader';
import { PartnerTabNavigation } from './PartnerTabNavigation';
import { PartnerOverviewTab } from './PartnerOverviewTab';
import { PartnerMeetingsTab } from './PartnerMeetingsTab';
import PartnerNotesTab from './PartnerNotesTab';
import { PartnerSettingsTab } from './PartnerSettingsTab';
import { PartnerAttendanceDashboard } from '@/components/partner/PartnerAttendanceDashboard';
import { PartnerCreateEditModal } from './PartnerCreateEditModal';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useUserList } from '@/hooks/useUserManagement';
import { useNavigate, useLocation } from 'react-router-dom';
import UserCard from '@/components/organizer/UserManagement/UserCard';
import UserTable from '@/components/organizer/UserManagement/UserTable';
import UserPagination from '@/components/organizer/UserManagement/UserPagination';
import type { User } from '@/types/user.types';

// ─── Partner Contacts Panel ────────────────────────────────────────────────────

const PartnerContactsPanel: React.FC<{ companyName: string; isMobile: boolean }> = ({
  companyName,
  isMobile,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [page, setPage] = useState(1);
  const [limit, setLimit] = useState(20);

  const { data, isLoading, isError } = useUserList({
    filters: { company: companyName, role: ['PARTNER'] },
    pagination: { page, limit },
  });

  if (isLoading) return <Skeleton variant="rectangular" height={300} />;
  if (isError) return <Alert severity="error">Failed to load contacts.</Alert>;

  const users = data?.data ?? [];
  const paginationData = data?.pagination;

  if (users.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        No partner users are assigned to this company yet.
      </Alert>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      {isMobile ? (
        <Stack spacing={2}>
          {users.map((user) => (
            <UserCard
              key={user.id}
              user={user}
              onClick={(u: User) =>
                navigate(`/organizer/users/${u.id}`, {
                  state: { from: location.pathname, fromLabel: companyName },
                })
              }
            />
          ))}
        </Stack>
      ) : (
        <UserTable
          users={users}
          onRowClick={(user: User) =>
            navigate(`/organizer/users/${user.id}`, {
              state: { from: location.pathname, fromLabel: companyName },
            })
          }
          onAction={(action, user: User) => {
            if (action === 'view')
              navigate(`/organizer/users/${user.id}`, {
                state: { from: location.pathname, fromLabel: companyName },
              });
          }}
        />
      )}
      {paginationData && (
        <UserPagination
          page={paginationData.page}
          totalPages={paginationData.totalPages}
          limit={paginationData.limit}
          onPageChange={setPage}
          onLimitChange={setLimit}
        />
      )}
    </Box>
  );
};

// ──────────────────────────────────────────────────────────────────────────────

interface PartnerDetailScreenProps {
  companyName?: string; // Story 8.0: provided by partner portal; organizer falls back to useParams()
}

/**
 * PartnerDetailScreen Component
 * Main screen integrating all partner detail components with tabbed interface
 */
export const PartnerDetailScreen: React.FC<PartnerDetailScreenProps> = (props) => {
  const { companyName: urlCompanyName } = useParams<{ companyName: string }>();
  const resolvedCompanyName = props.companyName ?? urlCompanyName ?? '';

  const { t } = useTranslation('organizer');
  const { t: tPartners } = useTranslation('partners');
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const activeTab = usePartnerDetailStore((state) => state.activeTab);
  const setActiveTab = usePartnerDetailStore((state) => state.setActiveTab);

  // Story 8.0: Get role from auth context (replaces hardcoded mock)
  const { user } = useAuth();
  const currentUser = {
    username: user?.username ?? '',
    role: (user?.role?.toUpperCase() ?? 'ORGANIZER') as
      | 'ORGANIZER'
      | 'PARTNER'
      | 'SPEAKER'
      | 'ATTENDEE',
  };

  // Story 8.0 H2: Clamp activeTab so stale ORGANIZER state cannot expose hidden tabs to PARTNER.
  // Story 8.4: Notes (4) and Settings (5) are not visible for PARTNER
  const PARTNER_MAX_TAB = 3; // Analytics (3) is the last visible tab for PARTNER
  const effectiveTab =
    currentUser.role === 'PARTNER' && activeTab > PARTNER_MAX_TAB ? 0 : activeTab;

  // Fetch partner detail with enriched data
  const {
    data: partner,
    isLoading,
    isError,
    error,
  } = usePartnerDetail(resolvedCompanyName, 'company,contacts,votes,meetings,activity');

  // Build breadcrumb items (memoized to prevent re-renders)
  // Story 8.0 M1: breadcrumb path is role-aware — partners cannot access /organizer/partners
  const breadcrumbItems: BreadcrumbItem[] = useMemo(
    () => [
      {
        label: t('navigation.partners', 'Partners'),
        path: currentUser.role === 'PARTNER' ? '/partners' : '/organizer/partners',
      },
      { label: partner?.companyName || resolvedCompanyName || t('common.loading', 'Loading...') },
    ],
    [partner?.companyName, resolvedCompanyName, t, currentUser.role]
  );

  // Handle tab change
  const handleTabChange = (newTab: number) => {
    setActiveTab(newTab);
  };

  // Loading state
  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <BATbernLoader size={96} />
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
          The partner &quot;{resolvedCompanyName}&quot; could not be found.
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

  const isPartner = currentUser.role === 'PARTNER';

  // Tabs visible per role — mirrors PartnerTabNavigation logic
  const allTabs = [
    { key: 'overview', label: tPartners('detail.tabs.overview'), icon: <OverviewIcon /> },
    { key: 'contacts', label: tPartners('detail.tabs.contacts'), icon: <ContactsIcon /> },
    { key: 'meetings', label: tPartners('detail.tabs.meetings'), icon: <MeetingsIcon /> },
    { key: 'analytics', label: tPartners('detail.tabs.analytics'), icon: <AnalyticsIcon /> },
    { key: 'notes', label: tPartners('detail.tabs.notes'), icon: <NotesIcon /> },
    { key: 'settings', label: tPartners('detail.tabs.settings'), icon: <SettingsIcon /> },
  ];
  const visibleTabs = isPartner
    ? allTabs.filter((tab) => tab.key !== 'settings' && tab.key !== 'notes')
    : allTabs;

  return (
    <Box sx={{ pb: 8 }}>
      <Container maxWidth="xl" sx={{ py: 4 }} data-testid="partner-detail-container">
        {/* Breadcrumbs — hidden for PARTNER (they can only belong to one company) */}
        {currentUser.role !== 'PARTNER' && <Breadcrumbs items={breadcrumbItems} marginBottom={2} />}

        {/* Header */}
        <PartnerDetailHeader partner={partner} role={currentUser.role} isMobile={isMobile} />

        {/* Tab Navigation — desktop only (bottom nav always shown) */}
        {!isMobile && (
          <Box sx={{ mt: 3 }}>
            <PartnerTabNavigation
              activeTab={effectiveTab}
              onTabChange={handleTabChange}
              role={currentUser.role}
            />
          </Box>
        )}

        {/* Tab Panels */}
        <Box sx={{ mt: 3 }}>
          {/* Overview Tab */}
          {effectiveTab === 0 && <PartnerOverviewTab partner={partner} />}

          {/* Contacts Tab */}
          {effectiveTab === 1 && (
            <PartnerContactsPanel companyName={partner.companyName} isMobile={isMobile} />
          )}

          {/* Meetings Tab */}
          {effectiveTab === 2 && (
            <PartnerMeetingsTab companyName={partner.companyName} role={currentUser.role} />
          )}

          {/* Analytics Tab */}
          {effectiveTab === 3 && <PartnerAttendanceDashboard companyName={partner.companyName} />}

          {/* Notes Tab — Story 8.4: hidden for PARTNER (organizer-internal notes) */}
          {effectiveTab === 4 && currentUser.role !== 'PARTNER' && (
            <PartnerNotesTab companyName={partner.companyName} role={currentUser.role} />
          )}

          {/* Settings Tab — Story 8.0 H2: explicit role guard in addition to tab nav filtering */}
          {effectiveTab === 5 && currentUser.role !== 'PARTNER' && (
            <PartnerSettingsTab partner={partner} currentUser={currentUser} />
          )}
        </Box>

        {/* Edit Partner Modal */}
        <PartnerCreateEditModal />
      </Container>

      {/* Bottom Navigation — always visible */}
      <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1100 }} elevation={3}>
        <BottomNavigation value={effectiveTab} onChange={(_, v) => handleTabChange(v)}>
          {visibleTabs.map((tab, idx) => (
            <BottomNavigationAction
              key={tab.key}
              value={idx}
              icon={tab.icon}
              sx={{ minWidth: 0, flex: 1, px: 0 }}
            />
          ))}
        </BottomNavigation>
      </Paper>
    </Box>
  );
};
