/**
 * EventManagementAdminPage (Story 10.1 - Task 1)
 *
 * Tabbed administration page at /organizer/admin:
 *   0 - Event Types
 *   1 - Import Data
 *   2 - Task Templates
 *   3 - Email Templates
 *   4 - Presentation Settings
 *   5 - AI Prompts
 *   6 - Settings (Story 10.26)
 *   7 - Global Images
 *   8 - Venue & Catering Contacts
 *
 * Tab index is persisted in URL as ?tab=N.
 * ORGANIZER role guard enforced.
 */

import React, { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert,
  Box,
  BottomNavigation,
  BottomNavigationAction,
  Container,
  Paper,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import {
  Category as CategoryIcon,
  CloudUpload as CloudUploadIcon,
  TaskAlt as TaskAltIcon,
  Email as EmailIcon,
  Slideshow as SlideshowIcon,
  AutoAwesome as AutoAwesomeIcon,
  Settings as SettingsIcon,
  PhotoLibrary as PhotoLibraryIcon,
  Restaurant as RestaurantIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { useAuth } from '@/hooks/useAuth';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
import { EventTypesTab } from '@/components/organizer/Admin/EventTypesTab';
import { ImportDataTab } from '@/components/organizer/Admin/ImportDataTab';
import { TaskTemplatesTab } from '@/components/organizer/Admin/TaskTemplatesTab';
import { EmailTemplatesTab } from '@/components/organizer/Admin/EmailTemplatesTab';
import { PresentationSettingsTab } from '@/components/organizer/Admin/PresentationSettingsTab';
import { AiPromptsTab } from '@/components/organizer/Admin/AiPromptsTab';
import { AdminSettingsTab } from '@/components/organizer/Admin/AdminSettingsTab';
import { GlobalImagesTab } from '@/components/organizer/Admin/GlobalImagesTab';
import { VenueCateringContactsTab } from '@/components/organizer/Admin/VenueCateringContactsTab';

const EventManagementAdminPage: React.FC = () => {
  const { t } = useTranslation('admin');
  const { isMobile } = useBreakpoints();
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const tabIndex = Math.max(0, Math.min(8, Number(searchParams.get('tab') ?? 0)));

  const breadcrumbItems: BreadcrumbItem[] = useMemo(
    () => [{ label: t('common:menu.administration', 'Administration') }],
    [t]
  );

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    setSearchParams({ tab: String(newValue) });
  };

  if (user?.role !== 'organizer') {
    return (
      <Container maxWidth="md" sx={{ mt: 4 }}>
        <Alert severity="error">
          <Typography variant="h6">{t('accessDenied', 'Access Denied')}</Typography>
          <Typography>{t('organizerOnly', 'This page is for organizers only.')}</Typography>
        </Alert>
      </Container>
    );
  }

  const tabs = [
    {
      label: t('tabs.eventTypes', 'Event Types'),
      icon: <CategoryIcon />,
      component: <EventTypesTab />,
    },
    {
      label: t('tabs.importData', 'Import Data'),
      icon: <CloudUploadIcon />,
      component: <ImportDataTab />,
    },
    {
      label: t('tabs.taskTemplates', 'Task Templates'),
      icon: <TaskAltIcon />,
      component: <TaskTemplatesTab />,
    },
    {
      label: t('tabs.emailTemplates', 'Email Templates'),
      icon: <EmailIcon />,
      component: <EmailTemplatesTab />,
    },
    {
      label: t('tabs.presentationSettings', 'Presentation'),
      icon: <SlideshowIcon />,
      component: <PresentationSettingsTab />,
    },
    {
      label: t('tabs.aiPrompts', 'AI Prompts'),
      icon: <AutoAwesomeIcon />,
      component: <AiPromptsTab />,
    },
    {
      label: t('tabs.settings', 'Settings'),
      icon: <SettingsIcon />,
      component: <AdminSettingsTab />,
    },
    {
      label: t('tabs.globalImages', 'Global Images'),
      icon: <PhotoLibraryIcon />,
      component: <GlobalImagesTab />,
    },
    {
      label: t('tabs.venueCoordination', 'Venue & Catering'),
      icon: <RestaurantIcon />,
      component: <VenueCateringContactsTab />,
    },
  ];

  const handleMobileNavChange = (_: React.SyntheticEvent, newValue: number) => {
    setSearchParams({ tab: String(newValue) });
  };

  return (
    <Container maxWidth="xl" sx={{ mt: 4, mb: 4, pb: isMobile ? 8 : 0 }}>
      <Breadcrumbs items={breadcrumbItems} />

      <Typography variant="h4" component="h1" gutterBottom>
        {t('common:menu.administration', 'Administration')}
      </Typography>

      {/* Desktop scrollable tab strip */}
      {!isMobile && (
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
          <Tabs
            value={tabIndex}
            onChange={handleTabChange}
            aria-label="Administration tabs"
            data-testid="admin-tabs"
            variant="scrollable"
            scrollButtons="auto"
            allowScrollButtonsMobile
          >
            {tabs.map((tab, i) => (
              <Tab
                key={i}
                label={tab.label}
                icon={tab.icon}
                iconPosition="start"
                id={`admin-tab-${i}`}
                aria-controls={`admin-tabpanel-${i}`}
                data-testid={`admin-tab-${i}`}
              />
            ))}
          </Tabs>
        </Box>
      )}

      <Box
        role="tabpanel"
        id={`admin-tabpanel-${tabIndex}`}
        aria-labelledby={`admin-tab-${tabIndex}`}
      >
        {tabs[tabIndex]?.component}
      </Box>

      {/* Mobile fixed bottom navigation */}
      {isMobile && (
        <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1100 }} elevation={3}>
          <BottomNavigation
            value={tabIndex}
            onChange={handleMobileNavChange}
            showLabels={false}
            data-testid="admin-bottom-nav"
          >
            {tabs.map((tab, i) => (
              <BottomNavigationAction
                key={i}
                value={i}
                label={tab.label}
                aria-label={tab.label}
                icon={tab.icon}
                data-testid={`admin-tab-${i}`}
                sx={{ minWidth: 0, flex: 1, px: 0 }}
              />
            ))}
          </BottomNavigation>
        </Paper>
      )}
    </Container>
  );
};

export default EventManagementAdminPage;
