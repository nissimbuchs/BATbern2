/**
 * ReturningVsNewChart
 * Story 10.5: Analytics Dashboard (AC3)
 *
 * Stacked BarChart: returning attendees (warm) + new attendees (cool) per event.
 */

import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTranslation } from 'react-i18next';
import type { AttendanceEventItem } from '@/services/analyticsService';
import { CHART_COLORS } from './CHART_COLORS';
import ChartCard from './ChartCard';
import DataTable from './DataTable';
import type { ColumnDef } from './DataTable';

interface Props {
  data: AttendanceEventItem[];
  isLoading?: boolean;
}

type Row = { eventCode: string; title: string; returning: number; new: number };

const ReturningVsNewChart = ({ data, isLoading }: Props) => {
  const { t } = useTranslation('organizer');

  const rows: Row[] = data.map((item) => ({
    eventCode: item.eventCode,
    title: item.title,
    returning: item.returningAttendees,
    new: item.newAttendees,
  }));

  const columns: ColumnDef<Row>[] = [
    { key: 'eventCode', label: 'Event' },
    { key: 'title', label: 'Title' },
    { key: 'returning', label: t('analytics.labels.returning'), align: 'right' },
    { key: 'new', label: t('analytics.labels.new'), align: 'right' },
  ];

  return (
    <ChartCard
      title={t('analytics.charts.returningVsNew')}
      isLoading={isLoading}
      isEmpty={!isLoading && rows.length === 0}
      dataTable={<DataTable columns={columns} rows={rows} rowKey="eventCode" />}
    >
      <ResponsiveContainer width="100%" height={380}>
        <BarChart data={rows} margin={{ top: 4, right: 16, left: 0, bottom: 60 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="title"
            angle={-45}
            textAnchor="end"
            interval={0}
            tick={{ fontSize: 10 }}
          />
          <YAxis allowDecimals={false} />
          <Tooltip />
          <Legend verticalAlign="top" wrapperStyle={{ paddingBottom: 8 }} />
          <Bar
            dataKey="returning"
            name={t('analytics.labels.returning')}
            stackId="a"
            fill={CHART_COLORS.partner}
          />
          <Bar
            dataKey="new"
            name={t('analytics.labels.new')}
            stackId="a"
            fill={CHART_COLORS.light}
          />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default ReturningVsNewChart;
