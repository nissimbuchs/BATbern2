/**
 * EventManagementAdminPage (Story 10.1 - Task 1)
 *
 * Tabbed administration page at /organizer/admin with 8 tabs:
 *   0 - Event Types
 *   1 - Import Data
 *   2 - Task Templates
 *   3 - Email Templates
 *   4 - Presentation Settings
 *   5 - Export / Import (Story 10.20)
 *   6 - AI Prompts
 *   7 - Settings (Story 10.26)
 *
 * Tab index is persisted in URL as ?tab=N.
 * ORGANIZER role guard enforced.
 */

import React, { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Alert, Box, Container, Tab, Tabs, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { Breadcrumbs } from '@/components/shared/Breadcrumbs';
import type { BreadcrumbItem } from '@/components/shared/Breadcrumbs';
import { EventTypesTab } from '@/components/organizer/Admin/EventTypesTab';
import { ImportDataTab } from '@/components/organizer/Admin/ImportDataTab';
import { TaskTemplatesTab } from '@/components/organizer/Admin/TaskTemplatesTab';
import { EmailTemplatesTab } from '@/components/organizer/Admin/EmailTemplatesTab';
import { PresentationSettingsTab } from '@/components/organizer/Admin/PresentationSettingsTab';
import { ExportImportTab } from '@/components/organizer/Admin/ExportImportTab';
import { AiPromptsTab } from '@/components/organizer/Admin/AiPromptsTab';
import { AdminSettingsTab } from '@/components/organizer/Admin/AdminSettingsTab';

const EventManagementAdminPage: React.FC = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const tabIndex = Math.max(0, Math.min(7, Number(searchParams.get('tab') ?? 0)));

  const breadcrumbItems: BreadcrumbItem[] = useMemo(
    () => [{ label: t('menu.administration', 'Administration') }],
    [t]
  );

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    setSearchParams({ tab: String(newValue) });
  };

  if (user?.role !== 'organizer') {
    return (
      <Container maxWidth="md" sx={{ mt: 4 }}>
        <Alert severity="error">
          <Typography variant="h6">{t('admin.accessDenied', 'Access Denied')}</Typography>
          <Typography>{t('admin.organizerOnly', 'This page is for organizers only.')}</Typography>
        </Alert>
      </Container>
    );
  }

  const tabs = [
    { label: t('admin.tabs.eventTypes', 'Event Types'), component: <EventTypesTab /> },
    { label: t('admin.tabs.importData', 'Import Data'), component: <ImportDataTab /> },
    { label: t('admin.tabs.taskTemplates', 'Task Templates'), component: <TaskTemplatesTab /> },
    { label: t('admin.tabs.emailTemplates', 'Email Templates'), component: <EmailTemplatesTab /> },
    {
      label: t('admin.tabs.presentationSettings', 'Presentation'),
      component: <PresentationSettingsTab />,
    },
    { label: t('admin.tabs.exportImport', 'Export / Import'), component: <ExportImportTab /> },
    { label: t('admin.tabs.aiPrompts', 'AI Prompts'), component: <AiPromptsTab /> },
    { label: t('admin.tabs.settings', 'Settings'), component: <AdminSettingsTab /> },
  ];

  return (
    <Container maxWidth="xl" sx={{ mt: 4, mb: 4 }}>
      <Breadcrumbs items={breadcrumbItems} />

      <Typography variant="h4" component="h1" gutterBottom>
        {t('menu.administration', 'Administration')}
      </Typography>

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={tabIndex} onChange={handleTabChange} aria-label="Administration tabs">
          {tabs.map((tab, i) => (
            <Tab
              key={i}
              label={tab.label}
              id={`admin-tab-${i}`}
              aria-controls={`admin-tabpanel-${i}`}
            />
          ))}
        </Tabs>
      </Box>

      <Box
        role="tabpanel"
        id={`admin-tabpanel-${tabIndex}`}
        aria-labelledby={`admin-tab-${tabIndex}`}
      >
        {tabs[tabIndex]?.component}
      </Box>
    </Container>
  );
};

export default EventManagementAdminPage;
