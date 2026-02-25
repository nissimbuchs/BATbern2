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
import { CHART_COLORS } from './CHART_COLORS';
import ChartCard from './ChartCard';
import DataTable from './DataTable';
import type { ColumnDef } from './DataTable';

type LabelMode = 'title' | 'category' | 'both';

interface Props {
  data: AttendanceEventItem[];
  isLoading?: boolean;
}

type Row = AttendanceEventItem & { label: string };

const AttendeesPerEventChart = ({ data, isLoading }: Props) => {
  const { t } = useTranslation('organizer');
  const [labelMode, setLabelMode] = useState<LabelMode>('title');

  const rows: Row[] = data.map((item) => ({
    ...item,
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
      <ToggleButton value="title">
        {t('analytics.labels.labelToggle.title')}
      </ToggleButton>
      <ToggleButton value="category">
        {t('analytics.labels.labelToggle.category')}
      </ToggleButton>
      <ToggleButton value="both">
        {t('analytics.labels.labelToggle.both')}
      </ToggleButton>
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
          <Bar dataKey="totalAttendees" fill={CHART_COLORS.primary} name="Attendees" />
          <Line
            type="monotone"
            dataKey="totalAttendees"
            stroke={CHART_COLORS.dark}
            dot={false}
            name="Trend"
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default AttendeesPerEventChart;
