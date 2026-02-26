/**
 * CompaniesTab
 * Story 10.5: Analytics Dashboard (AC5)
 *
 * Top N toggle state, partner company highlight logic, all 3 charts.
 */

import { Box, ToggleButton, ToggleButtonGroup } from '@mui/material';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth';
import { useAnalyticsCompanies, useAnalyticsOverview } from '@/hooks/useAnalytics';
import CompanyAttendanceOverTimeChart from './CompanyAttendanceOverTimeChart';
import CompanyDistributionPieChart from './CompanyDistributionPieChart';
import SessionsPerCompanyChart from './SessionsPerCompanyChart';

type TopNOption = '5' | '10' | 'all';

interface Props {
  fromYear?: number;
}

const CompaniesTab = ({ fromYear }: Props) => {
  const { t } = useTranslation('organizer');
  const { user } = useAuth();
  const [topNOption, setTopNOption] = useState<TopNOption>('10');

  const partnerCompany = user?.role === 'partner' ? (user.companyName ?? null) : null;

  const topN: number | null = topNOption === 'all' ? null : Number(topNOption);

  const { data: companiesData, isLoading } = useAnalyticsCompanies(fromYear);
  const { data: overviewData } = useAnalyticsOverview();

  const pinnedSessions = useMemo(() => {
    const sessions = companiesData?.sessionsPerCompany ?? [];
    if (!partnerCompany) {
      return sessions;
    }
    return [
      ...sessions.filter((s) => s.companyName === partnerCompany),
      ...sessions.filter((s) => s.companyName !== partnerCompany),
    ];
  }, [companiesData, partnerCompany]);

  const topNControls = (
    <ToggleButtonGroup
      value={topNOption}
      exclusive
      onChange={(_e, v) => {
        if (v) {
          setTopNOption(v as TopNOption);
        }
      }}
      size="small"
      sx={{ mb: 2 }}
    >
      <ToggleButton value="5">{t('analytics.labels.topN.top5')}</ToggleButton>
      <ToggleButton value="10">{t('analytics.labels.topN.top10')}</ToggleButton>
      <ToggleButton value="all">{t('analytics.labels.topN.all')}</ToggleButton>
    </ToggleButtonGroup>
  );

  return (
    <>
      <Box>{topNControls}</Box>

      <CompanyAttendanceOverTimeChart
        data={companiesData?.attendanceOverTime ?? []}
        topN={topN}
        partnerCompany={partnerCompany}
        isLoading={isLoading}
      />

      <SessionsPerCompanyChart
        data={pinnedSessions}
        topN={topN}
        partnerCompany={partnerCompany}
        isLoading={isLoading}
      />

      <CompanyDistributionPieChart
        allTimeDistribution={companiesData?.distribution ?? []}
        events={overviewData?.timeline ?? []}
        partnerCompany={partnerCompany}
        topN={topN}
        isLoading={isLoading}
      />
    </>
  );
};

export default CompaniesTab;
