/**
 * CompanyDistributionPieChart
 * Story 10.5: Analytics Dashboard (AC5)
 *
 * PieChart: each slice = one company's share of attendees.
 * Per-event filter dropdown: uses companies/distribution endpoint when event selected,
 * otherwise uses the distribution array from the companies endpoint.
 * Partner's own company slice highlighted with accent color.
 *
 * Hover animation: uses `shape` prop (not activeShape) so the same DOM <g> element
 * persists on hover — only its CSS transform changes, enabling a smooth transition.
 */

import { Cell, Pie, PieChart, ResponsiveContainer, Sector, Tooltip } from 'recharts';
import { FormControl, InputLabel, MenuItem, Select } from '@mui/material';
import { useCallback, useMemo, useState } from 'react';
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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const PieTooltip = ({ active, payload }: any) => {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload as CompanyAttendanceShare;
  return (
    <div
      style={{
        background: '#fff',
        border: '1px solid #ccc',
        borderRadius: 4,
        padding: '8px 12px',
        fontSize: 12,
      }}
    >
      <div style={{ fontWeight: 600 }}>{d.displayName ?? d.companyName}</div>
      <div style={{ marginTop: 4 }}>{d.attendeeCount} attendees</div>
    </div>
  );
};

const CompanyDistributionPieChart = ({
  allTimeDistribution,
  events,
  partnerCompany,
  topN,
  isLoading: parentLoading,
}: Props) => {
  const { t } = useTranslation('organizer');
  const [selectedEvent, setSelectedEvent] = useState<string>('');
  const [activeIndex, setActiveIndex] = useState<number | undefined>(undefined);

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

  /**
   * Custom shape renderer — called for every slice on every render.
   * Using `shape` (not `activeShape`) keeps the same <g> DOM node alive across
   * hover state changes, so the CSS `transition` on transform fires smoothly.
   *
   * Translate the active slice outward along its midpoint angle.
   * Recharts Sector angles are in degrees: 0° = 3 o'clock, increasing counter-clockwise.
   */
  const renderSlice = useCallback(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (props: any) => {
      const { cx, cy, innerRadius, outerRadius, startAngle, endAngle, fill, index } = props as {
        cx: number;
        cy: number;
        innerRadius: number;
        outerRadius: number;
        startAngle: number;
        endAngle: number;
        fill: string;
        index: number;
      };

      const isActive = index === activeIndex;
      const midAngleRad = ((startAngle + endAngle) / 2) * (Math.PI / 180);
      const shift = isActive ? 10 : 0;
      // Translate along the midpoint direction (SVG y-axis is inverted → negate sin)
      const dx = Math.cos(midAngleRad) * shift;
      const dy = -Math.sin(midAngleRad) * shift;

      return (
        <g
          style={{
            transform: `translate(${dx}px, ${dy}px)`,
            transition: 'transform 220ms ease, filter 220ms ease',
            filter: isActive ? 'url(#pie-slice-glow)' : 'none',
          }}
        >
          <Sector
            cx={cx}
            cy={cy}
            innerRadius={innerRadius}
            outerRadius={outerRadius}
            startAngle={startAngle}
            endAngle={endAngle}
            fill={fill}
          />
        </g>
      );
    },
    [activeIndex]
  );

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
      <ResponsiveContainer width="100%" height={380}>
        <PieChart>
          <defs>
            <filter id="pie-slice-glow" x="-25%" y="-25%" width="150%" height="150%">
              <feDropShadow dx="0" dy="0" stdDeviation="5" floodOpacity="0.45" />
            </filter>
          </defs>
          <Pie
            data={distribution}
            dataKey="attendeeCount"
            nameKey="displayName"
            cx="50%"
            cy="50%"
            outerRadius={130}
            shape={renderSlice}
            onMouseEnter={(_, index) => setActiveIndex(index)}
            onMouseLeave={() => setActiveIndex(undefined)}
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
          <Tooltip content={<PieTooltip />} />
        </PieChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default CompanyDistributionPieChart;
