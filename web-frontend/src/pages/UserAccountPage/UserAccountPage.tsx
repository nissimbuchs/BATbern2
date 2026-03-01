/**
 * UserAccountPage Component
 * Story 2.6: User Account Management Frontend
 * Main page container with Profile and Settings tabs
 */

import React, { useState, useMemo } from 'react';
import { Box, Tabs, Tab, Alert } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@hooks/useAuth';
import { useUserProfile } from '@/hooks/useUserAccount/useUserAccount';
import UserProfileTab from '@components/user/UserProfileTab/UserProfileTab';
import UserSettingsTab from '@components/user/UserSettingsTab/UserSettingsTab';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`account-tabpanel-${index}`}
      aria-labelledby={`account-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
    </div>
  );
}

function a11yProps(index: number) {
  return {
    id: `account-tab-${index}`,
    'aria-controls': `account-tabpanel-${index}`,
  };
}

const UserAccountPage: React.FC = () => {
  console.log('[UserAccountPage] Component mounting');
  const { user } = useAuth();
  console.log('[UserAccountPage] User:', user);
  const { t } = useTranslation('common');
  const [activeTab, setActiveTab] = useState(0);

  const { data: profileData, isLoading, isError, error } = useUserProfile(user?.userId || '');
  console.log('[UserAccountPage] Profile data:', { profileData, isLoading, isError, error });
  console.log('[UserAccountPage] profileData structure:', JSON.stringify(profileData, null, 2));

  // Build breadcrumb items (memoized to prevent re-renders)
  const breadcrumbItems: BreadcrumbItem[] = useMemo(
    () => [{ label: t('navigation.myAccount', 'My Account') }],
    [t]
  );

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  if (isLoading) {
    console.log('[UserAccountPage] Rendering loading state');
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '50vh',
        }}
        data-testid="loading-state"
      >
        <BATbernLoader size={96} />
      </Box>
    );
  }

  if (isError) {
    return (
      <Box sx={{ p: 3 }} data-testid="error-state">
        <Alert severity="error">
          {t('userAccount.errors.loadFailed')}
          {error && (
            <Box component="span" sx={{ display: 'block', mt: 1, fontSize: '0.875rem' }}>
              {t('userAccount.errors.correlationId', {
                id: (error as { correlationId?: string })?.correlationId || 'N/A',
              })}
            </Box>
          )}
        </Alert>
      </Box>
    );
  }

  if (!profileData || !profileData.user) {
    console.log('[UserAccountPage] No profile data or user, returning null');
    return null;
  }

  console.log('[UserAccountPage] Rendering main content');
  return (
    <Box sx={{ width: '100%', p: { xs: 2, md: 3 } }}>
      {/* Breadcrumbs */}
      <Breadcrumbs items={breadcrumbItems} marginBottom={2} />

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          aria-label={t('userAccount.tabs.ariaLabel')}
          data-testid="account-tabs"
        >
          <Tab label={t('menu.profile')} {...a11yProps(0)} data-testid="profile-tab" />
          <Tab label={t('menu.settings')} {...a11yProps(1)} data-testid="settings-tab" />
        </Tabs>
      </Box>

      <TabPanel value={activeTab} index={0}>
        <UserProfileTab user={profileData.user} activity={profileData.activity || []} />
      </TabPanel>

      <TabPanel value={activeTab} index={1}>
        <UserSettingsTab
          email={profileData.user.email}
          preferences={profileData.preferences}
          settings={profileData.settings}
        />
      </TabPanel>
    </Box>
  );
};

export default UserAccountPage;
