/**
 * EventCadenceTimeline
 * Story 10.5: Analytics Dashboard (AC2)
 *
 * Horizontal scrollable Recharts BarChart showing all events as colored bars on a time axis.
 * Each bar is colored by topic category. Not filtered by the global time range — always all-time.
 */

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useTranslation } from 'react-i18next';
import type { EventTimelineItem } from '@/services/analyticsService';
import { getCategoryColor } from './CHART_COLORS';
import DataTable from './DataTable';
import ChartCard from './ChartCard';
import type { ColumnDef } from './DataTable';

interface Props {
  data: EventTimelineItem[];
  isLoading?: boolean;
}

type Row = {
  eventCode: string;
  title: string;
  eventDate: string;
  category: string;
  attendeeCount: number;
};

const EventCadenceTimeline = ({ data, isLoading }: Props) => {
  const { t } = useTranslation('organizer');

  const rows: Row[] = data.map((item) => ({
    eventCode: item.eventCode,
    title: item.title,
    eventDate: item.eventDate.slice(0, 10),
    category: item.category ?? 'OTHER',
    attendeeCount: item.attendeeCount,
  }));

  const columns: ColumnDef<Row>[] = [
    { key: 'eventCode', label: t('analytics.kpi.totalEvents') },
    { key: 'title', label: 'Title' },
    { key: 'eventDate', label: 'Date' },
    { key: 'category', label: 'Category' },
    { key: 'attendeeCount', label: t('analytics.kpi.totalAttendees'), align: 'right' },
  ];

  return (
    <ChartCard
      title={t('analytics.charts.eventCadenceTimeline')}
      isLoading={isLoading}
      isEmpty={!isLoading && rows.length === 0}
      dataTable={<DataTable columns={columns} rows={rows} rowKey="eventCode" />}
    >
      <ResponsiveContainer width="100%" height={350}>
        <BarChart data={rows} margin={{ top: 4, right: 16, left: 0, bottom: 48 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="eventCode"
            angle={-45}
            textAnchor="end"
            interval={0}
            tick={{ fontSize: 10 }}
          />
          <YAxis allowDecimals={false} />
          <Tooltip
            formatter={(value, _name, entry) => [`${value} attendees`, entry.payload.title]}
            labelFormatter={(label) => `Event: ${label}`}
          />
          <Bar
            dataKey="attendeeCount"
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
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default EventCadenceTimeline;
