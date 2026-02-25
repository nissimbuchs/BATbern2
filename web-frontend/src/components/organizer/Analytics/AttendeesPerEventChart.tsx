/**
 * AttendeesPerEventChart
 * Story 10.5: Analytics Dashboard (AC3)
 *
 * Recharts ComposedChart: bars per event + Line trend overlay.
 * Label toggle: [Event Title] [Category] [Both]
 */

import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ToggleButton, ToggleButtonGroup } from '@mui/material';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AttendanceEventItem } from '@/services/analyticsService';
import { CHART_COLORS, getCategoryColor } from './CHART_COLORS';
import ChartCard from './ChartCard';
import DataTable from './DataTable';
import type { ColumnDef } from './DataTable';

const ROLLING_WINDOW = 8;

/** Centered rolling average with a window of ROLLING_WINDOW events. */
function rollingAverage(values: number[], window: number): (number | null)[] {
  const half = Math.floor(window / 2);
  return values.map((_, i) => {
    const start = Math.max(0, i - half);
    const end = Math.min(values.length - 1, i + half);
    const slice = values.slice(start, end + 1);
    return Math.round(slice.reduce((s, v) => s + v, 0) / slice.length);
  });
}

type LabelMode = 'title' | 'category' | 'both';

interface Props {
  data: AttendanceEventItem[];
  isLoading?: boolean;
}

type Row = AttendanceEventItem & { label: string; trend: number | null };

const AttendeesPerEventChart = ({ data, isLoading }: Props) => {
  const { t } = useTranslation('organizer');
  const [labelMode, setLabelMode] = useState<LabelMode>('title');

  const trendValues = rollingAverage(
    data.map((d) => d.totalAttendees),
    ROLLING_WINDOW
  );

  const rows: Row[] = data.map((item, i) => ({
    ...item,
    trend: trendValues[i],
    label:
      labelMode === 'title'
        ? item.title
        : labelMode === 'category'
          ? (item.category ?? 'OTHER')
          : `${item.title} (${item.category ?? 'OTHER'})`,
  }));

  const columns: ColumnDef<Row>[] = [
    { key: 'eventCode', label: 'Event' },
    { key: 'title', label: 'Title' },
    { key: 'totalAttendees', label: 'Total', align: 'right' },
    { key: 'returningAttendees', label: 'Returning', align: 'right' },
    { key: 'newAttendees', label: 'New', align: 'right' },
  ];

  const controls = (
    <ToggleButtonGroup
      value={labelMode}
      exclusive
      onChange={(_e, v) => {
        if (v) {
          setLabelMode(v as LabelMode);
        }
      }}
      size="small"
    >
      <ToggleButton value="title">{t('analytics.labels.labelToggle.title')}</ToggleButton>
      <ToggleButton value="category">{t('analytics.labels.labelToggle.category')}</ToggleButton>
      <ToggleButton value="both">{t('analytics.labels.labelToggle.both')}</ToggleButton>
    </ToggleButtonGroup>
  );

  return (
    <ChartCard
      title={t('analytics.charts.attendeesPerEvent')}
      isLoading={isLoading}
      isEmpty={!isLoading && rows.length === 0}
      controls={controls}
      dataTable={<DataTable columns={columns} rows={rows} rowKey="eventCode" />}
    >
      <ResponsiveContainer width="100%" height={350}>
        <ComposedChart data={rows} margin={{ top: 4, right: 16, left: 0, bottom: 64 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="label"
            angle={-45}
            textAnchor="end"
            interval={0}
            tick={{ fontSize: 10 }}
          />
          <YAxis allowDecimals={false} />
          <Tooltip />
          <Bar
            dataKey="totalAttendees"
            name="Attendees"
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            shape={(props: any) => {
              const { x, y, width, height, category } = props as {
                x: number;
                y: number;
                width: number;
                height: number;
                category: string;
              };
              return (
                <rect x={x} y={y} width={width} height={height} fill={getCategoryColor(category)} />
              );
            }}
          />
          <Line
            type="monotone"
            dataKey="trend"
            stroke={CHART_COLORS.dark}
            dot={false}
            name="Trend"
            strokeWidth={2}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default AttendeesPerEventChart;
