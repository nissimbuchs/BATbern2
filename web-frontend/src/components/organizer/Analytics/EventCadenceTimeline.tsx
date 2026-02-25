/**
 * EventCadenceTimeline
 * Story 10.5: Analytics Dashboard (AC2)
 *
 * Horizontal scrollable Recharts BarChart showing all events as colored bars on a time axis.
 * Each bar is colored by topic category. Not filtered by the global time range — always all-time.
 */

import {
  Bar,
  BarChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
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

  // Compute titles where the year changes — used for year-boundary reference lines
  const yearBoundaryTitles: string[] = [];
  let lastYear = '';
  for (const row of rows) {
    const year = row.eventDate.slice(0, 4);
    if (year !== lastYear && lastYear !== '') {
      yearBoundaryTitles.push(row.title);
    }
    lastYear = year;
  }

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
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          {yearBoundaryTitles.map((title) => (
            <ReferenceLine
              key={title}
              x={title}
              stroke="#aaa"
              strokeWidth={1.5}
              strokeDasharray="4 2"
            />
          ))}
          <XAxis
            dataKey="title"
            angle={-45}
            textAnchor="end"
            interval={0}
            tick={{ fontSize: 10 }}
          />
          <YAxis allowDecimals={false} />
          <Tooltip
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            content={(props: any) => {
              if (!props.active || !props.payload?.length) return null;
              const d = props.payload[0].payload as Row;
              return (
                <div
                  style={{
                    background: '#fff',
                    border: '1px solid #ccc',
                    borderRadius: 4,
                    padding: '6px 10px',
                    fontSize: 12,
                  }}
                >
                  <div style={{ fontWeight: 600 }}>{d.eventCode}</div>
                  <div style={{ color: '#555' }}>{d.eventDate}</div>
                  <div style={{ marginTop: 4 }}>{d.attendeeCount} attendees</div>
                </div>
              );
            }}
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
