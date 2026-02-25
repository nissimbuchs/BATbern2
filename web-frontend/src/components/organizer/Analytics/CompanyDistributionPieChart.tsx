/**
 * CompanyDistributionPieChart
 * Story 10.5: Analytics Dashboard (AC5)
 *
 * PieChart: each slice = one company's share of attendees.
 * Per-event filter dropdown: uses companies/distribution endpoint when event selected,
 * otherwise uses the distribution array from the companies endpoint.
 * Partner's own company slice highlighted with accent color.
 *
 * Hover animation strategy:
 *   - `shape` prop renders all slices (not activeShape, which remounts the node)
 *   - activeIndexRef holds the current hover state WITHOUT changing renderSlice's reference
 *   - renderSlice has [] deps → stable reference → Recharts never remounts sector nodes
 *   - When hover state changes, only style.transform is patched on the existing <g> node
 *   - CSS `transition` fires smoothly on the persistent DOM element
 */

import { Cell, Pie, PieChart, ResponsiveContainer, Sector, Tooltip } from 'recharts';
import { FormControl, InputLabel, MenuItem, Select } from '@mui/material';
import { useCallback, useMemo, useRef, useState } from 'react';
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

  // Ref holds current hover index synchronously; state drives re-render.
  // renderSlice closes over the ref (not state) → stable [] deps → no remount.
  const activeIndexRef = useRef<number | undefined>(undefined);
  const [, triggerRender] = useState(0);

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

  const handleMouseEnter = useCallback((_: unknown, index: number) => {
    activeIndexRef.current = index;
    triggerRender((n) => n + 1);
  }, []);

  const handleMouseLeave = useCallback(() => {
    activeIndexRef.current = undefined;
    triggerRender((n) => n + 1);
  }, []);

  /**
   * Stable shape renderer — empty deps, reads activeIndexRef.current on each call.
   * Because the reference never changes, Recharts does not remount sector elements,
   * so the CSS transition on the <g> wrapper fires on every hover in/out.
   *
   * Scale from chart center via the translate-scale-translate trick:
   *   translate(cx, cy) scale(s) translate(-cx, -cy)
   * This avoids transform-origin SVG quirks across browsers.
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

      const isActive = index === activeIndexRef.current;
      const scale = isActive ? 1.07 : 1;
      // Equivalent to: transform-origin cx cy; scale(scale)
      const transform =
        scale !== 1
          ? `translate(${cx}px,${cy}px) scale(${scale}) translate(${-cx}px,${-cy}px)`
          : 'none';

      return (
        <g
          style={{
            transform,
            transition: 'transform 220ms ease',
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
    [] // stable — reads ref, not state
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
            onMouseEnter={handleMouseEnter}
            onMouseLeave={handleMouseLeave}
            isAnimationActive={false}
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
          <Tooltip content={<PieTooltip />} isAnimationActive={false} />
        </PieChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default CompanyDistributionPieChart;
