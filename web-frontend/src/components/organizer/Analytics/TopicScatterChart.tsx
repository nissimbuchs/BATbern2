/**
 * TopicScatterChart
 * Story 10.5: Analytics Dashboard (AC4)
 *
 * ScatterChart: X = number of events on topic; Y = avg attendee count.
 * Each dot = one topic, tooltip reveals title, category, event count, avg attendees.
 * Reveals under/over-performing topics.
 */

import {
  CartesianGrid,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTranslation } from 'react-i18next';
import type { TopicScatterItem } from '@/services/analyticsService';
import { CHART_COLORS, getCategoryColor } from './CHART_COLORS';
import ChartCard from './ChartCard';
import DataTable from './DataTable';
import type { ColumnDef } from './DataTable';

interface Props {
  data: TopicScatterItem[];
  isLoading?: boolean;
}

const TopicScatterChart = ({ data, isLoading }: Props) => {
  const { t } = useTranslation('organizer');

  const scatterData = data.map((item) => ({
    x: item.eventCount,
    y: item.avgAttendees,
    topicTitle: item.topicTitle,
    category: item.category,
    fill: getCategoryColor(item.category),
  }));

  type ScatterRow = TopicScatterItem;

  const columns: ColumnDef<ScatterRow>[] = [
    { key: 'topicTitle', label: 'Topic' },
    { key: 'category', label: 'Category' },
    { key: 'eventCount', label: 'Events', align: 'right' },
    {
      key: 'avgAttendees',
      label: 'Avg Attendees',
      align: 'right',
      format: (v) => Number(v).toFixed(1),
    },
  ];

  const CustomTooltip = ({
    active,
    payload,
  }: {
    active?: boolean;
    payload?: Array<{ payload: (typeof scatterData)[number] }>;
  }) => {
    if (!active || !payload?.length) {
      return null;
    }
    const d = payload[0].payload;
    return (
      <div
        style={{
          background: '#fff',
          border: '1px solid #ccc',
          padding: '8px 12px',
          borderRadius: 4,
        }}
      >
        <strong>{d.topicTitle}</strong>
        <br />
        Category: {d.category}
        <br />
        Events: {d.x}
        <br />
        Avg attendees: {Number(d.y).toFixed(1)}
      </div>
    );
  };

  return (
    <ChartCard
      title={t('analytics.charts.topicPopularity')}
      isLoading={isLoading}
      isEmpty={!isLoading && data.length === 0}
      dataTable={<DataTable columns={columns} rows={data} rowKey="topicCode" />}
    >
      <ResponsiveContainer width="100%" height={350}>
        <ScatterChart margin={{ top: 8, right: 32, left: 0, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis
            dataKey="x"
            type="number"
            name="Events"
            label={{ value: 'Number of Events', position: 'insideBottom', offset: -4 }}
          />
          <YAxis
            dataKey="y"
            type="number"
            name="Avg Attendees"
            label={{ value: 'Avg Attendees', angle: -90, position: 'insideLeft' }}
          />
          <Tooltip content={<CustomTooltip />} />
          <Scatter
            data={scatterData}
            fill={CHART_COLORS.primary}
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            shape={(props: any) => {
              const { cx, cy, fill } = props as { cx: number; cy: number; fill: string };
              return <circle cx={cx} cy={cy} r={6} fill={fill} opacity={0.8} />;
            }}
          />
        </ScatterChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default TopicScatterChart;
