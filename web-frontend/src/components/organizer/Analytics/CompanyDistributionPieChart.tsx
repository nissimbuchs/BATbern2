/**
 * CompanyDistributionPieChart
 * Story 10.5: Analytics Dashboard (AC5)
 *
 * PieChart: each slice = one company's share of attendees.
 * Per-event filter dropdown: uses companies/distribution endpoint when event selected,
 * otherwise uses the distribution array from the companies endpoint.
 * Partner's own company slice highlighted with accent color.
 */

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { FormControl, InputLabel, MenuItem, Select } from '@mui/material';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { CompanyAttendanceShare, EventTimelineItem } from '@/services/analyticsService';
import { useCompanyDistribution } from '@/hooks/useAnalytics';
import { CHART_COLORS } from './CHART_COLORS';
import ChartCard from './ChartCard';

const SLICE_COLORS = [
  CHART_COLORS.primary,
  CHART_COLORS.light,
  CHART_COLORS.success,
  CHART_COLORS.purple,
  CHART_COLORS.info,
  CHART_COLORS.grey,
  CHART_COLORS.error,
];

interface Props {
  allTimeDistribution: CompanyAttendanceShare[];
  events: EventTimelineItem[];
  partnerCompany?: string | null;
  topN?: number | null;
  isLoading?: boolean;
}

const CompanyDistributionPieChart = ({
  allTimeDistribution,
  events,
  partnerCompany,
  topN,
  isLoading: parentLoading,
}: Props) => {
  const { t } = useTranslation('organizer');
  const [selectedEvent, setSelectedEvent] = useState<string>('');

  const { data: eventDistData, isLoading: eventLoading } = useCompanyDistribution(selectedEvent);

  const rawDistribution: CompanyAttendanceShare[] = selectedEvent
    ? (eventDistData?.distribution ?? [])
    : allTimeDistribution;

  // Apply Top N filter (pin partner company even if outside top N)
  const distribution: CompanyAttendanceShare[] = useMemo(() => {
    if (topN == null) {
      return rawDistribution;
    }
    const slice = rawDistribution.slice(0, topN);
    if (partnerCompany && !slice.find((d) => d.companyName === partnerCompany)) {
      const own = rawDistribution.find((d) => d.companyName === partnerCompany);
      if (own) slice.push(own);
    }
    return slice;
  }, [rawDistribution, topN, partnerCompany]);

  const isLoading = parentLoading || (!!selectedEvent && eventLoading);

  const controls = (
    <FormControl size="small" sx={{ minWidth: 180 }}>
      <InputLabel>{t('analytics.labels.filterByEvent')}</InputLabel>
      <Select
        value={selectedEvent}
        label={t('analytics.labels.filterByEvent')}
        onChange={(e) => setSelectedEvent(e.target.value)}
      >
        <MenuItem value="">{t('analytics.labels.allEvents')}</MenuItem>
        {events.map((ev) => (
          <MenuItem key={ev.eventCode} value={ev.eventCode}>
            {ev.eventCode} — {ev.title}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );

  return (
    <ChartCard
      title={t('analytics.charts.attendeeDistribution')}
      isLoading={isLoading}
      isEmpty={!isLoading && distribution.length === 0}
      controls={controls}
    >
      <ResponsiveContainer width="100%" height={350}>
        <PieChart>
          <Pie
            data={distribution}
            dataKey="attendeeCount"
            nameKey="displayName"
            cx="50%"
            cy="50%"
            outerRadius={130}
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            label={(props: any) =>
              `${props.displayName ?? props.companyName} ${(Number(props.percent ?? 0) * 100).toFixed(0)}%`
            }
          >
            {distribution.map((entry, idx) => (
              <Cell
                key={entry.companyName}
                fill={
                  entry.companyName === partnerCompany
                    ? CHART_COLORS.partner
                    : SLICE_COLORS[idx % SLICE_COLORS.length]
                }
                stroke={entry.companyName === partnerCompany ? CHART_COLORS.dark : undefined}
                strokeWidth={entry.companyName === partnerCompany ? 2 : 0}
              />
            ))}
          </Pie>
          <Tooltip formatter={(v, name) => [`${v} attendees`, name]} />
        </PieChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default CompanyDistributionPieChart;
