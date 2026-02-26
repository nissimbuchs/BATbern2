/**
 * OverviewTab
 * Story 10.5: Analytics Dashboard (AC2)
 *
 * 4 KPI cards (all-time, unaffected by time range filter) + event cadence timeline.
 */

import BusinessIcon from '@mui/icons-material/Business';
import EventIcon from '@mui/icons-material/Event';
import MicIcon from '@mui/icons-material/Mic';
import PeopleIcon from '@mui/icons-material/People';
import PersonIcon from '@mui/icons-material/Person';
import { Box } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAnalyticsOverview } from '@/hooks/useAnalytics';
import { BATbernLoader } from '@/components/shared/BATbernLoader';
import EventCadenceTimeline from './EventCadenceTimeline';
import KpiCard from './KpiCard';

const OverviewTab = () => {
  const { t } = useTranslation('organizer');
  const { data, isLoading } = useAnalyticsOverview();

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" mt={4}>
        <BATbernLoader size={48} speed="normal" />
      </Box>
    );
  }

  return (
    <Box>
      <Box display="flex" gap={2} flexWrap="wrap" mb={3}>
        <KpiCard
          label={t('analytics.kpi.totalEvents')}
          value={data?.totalEvents ?? 0}
          icon={EventIcon}
          color="#2C5F7C"
        />
        <KpiCard
          label={t('analytics.kpi.totalAttendees')}
          value={data?.totalAttendees ?? 0}
          icon={PeopleIcon}
          color="#4A90B8"
        />
        <KpiCard
          label={t('analytics.kpi.companiesRepresented')}
          value={data?.companiesRepresented ?? 0}
          icon={BusinessIcon}
          color="#27AE60"
        />
        <KpiCard
          label={t('analytics.kpi.totalSessions')}
          value={data?.totalSessions ?? 0}
          icon={MicIcon}
          color="#9B59B6"
        />
        <KpiCard
          label={t('analytics.kpi.totalSpeakers')}
          value={data?.totalSpeakers ?? 0}
          icon={PersonIcon}
          color="#E67E22"
        />
      </Box>

      <EventCadenceTimeline data={data?.timeline ?? []} isLoading={isLoading} />
    </Box>
  );
};

export default OverviewTab;
