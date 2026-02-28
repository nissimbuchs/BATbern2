/**
 * OrganizerAnalyticsPage
 * Story 10.5: Analytics Dashboard (AC1, AC10)
 *
 * 4-tab analytics page for ORGANIZER and PARTNER roles.
 * Global time range selector affects Attendance, Topics, and Companies tabs.
 * Overview tab always shows all-time KPI totals.
 */

import { Box, Tab, Tabs, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import AttendanceTab from '@/components/organizer/Analytics/AttendanceTab';
import CompaniesTab from '@/components/organizer/Analytics/CompaniesTab';
import OverviewTab from '@/components/organizer/Analytics/OverviewTab';
import TopicsTab from '@/components/organizer/Analytics/TopicsTab';

type TimeRange = 'ALL' | '5Y' | '2Y';
type TabIndex = 0 | 1 | 2 | 3;

const fromYearFromRange = (range: TimeRange): number | undefined => {
  const currentYear = new Date().getFullYear();
  if (range === '5Y') return currentYear - 5;
  if (range === '2Y') return currentYear - 2;
  return undefined;
};

const OrganizerAnalyticsPage: React.FC = () => {
  const { t } = useTranslation('organizer');
  const [activeTab, setActiveTab] = useState<TabIndex>(0);
  const [timeRange, setTimeRange] = useState<TimeRange>('ALL');

  const fromYear = fromYearFromRange(timeRange);

  return (
    <Box sx={{ p: 3 }}>
      {/* Page header */}
      <Box
        display="flex"
        alignItems="center"
        justifyContent="space-between"
        flexWrap="wrap"
        gap={2}
        mb={2}
      >
        <Typography variant="h5" component="h1">
          {t('common:navigation.analytics')}
        </Typography>

        {/* Global time range selector — only shown on time-sensitive tabs */}
        {activeTab !== 0 && (
          <ToggleButtonGroup
            value={timeRange}
            exclusive
            onChange={(_e, v) => {
              if (v) setTimeRange(v as TimeRange);
            }}
            size="small"
            aria-label={t('common:navigation.analytics')}
          >
            <ToggleButton value="ALL">{t('analytics.timeRange.all')}</ToggleButton>
            <ToggleButton value="5Y">{t('analytics.timeRange.5y')}</ToggleButton>
            <ToggleButton value="2Y">{t('analytics.timeRange.2y')}</ToggleButton>
          </ToggleButtonGroup>
        )}
      </Box>

      {/* Tab bar */}
      <Tabs
        value={activeTab}
        onChange={(_e, v) => setActiveTab(v as TabIndex)}
        sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}
      >
        <Tab label={t('common:labels.overview')} />
        <Tab label={t('analytics.tabs.attendance')} />
        <Tab label={t('common:labels.topics')} />
        <Tab label={t('analytics.tabs.companies')} />
      </Tabs>

      {/* Tab panels */}
      {activeTab === 0 && <OverviewTab />}
      {activeTab === 1 && <AttendanceTab fromYear={fromYear} />}
      {activeTab === 2 && <TopicsTab fromYear={fromYear} />}
      {activeTab === 3 && <CompaniesTab fromYear={fromYear} />}
    </Box>
  );
};

export default OrganizerAnalyticsPage;
