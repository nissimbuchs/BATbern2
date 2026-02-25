/**
 * EventsPerCategoryChart
 * Story 10.5: Analytics Dashboard (AC4)
 *
 * Horizontal BarChart: one bar per topic category, sorted descending by event count.
 * Category colors from BATbern palette.
 */

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTranslation } from 'react-i18next';
import type { CategoryEventCount } from '@/services/analyticsService';
import { getCategoryColor } from './CHART_COLORS';
import ChartCard from './ChartCard';
import DataTable from './DataTable';
import type { ColumnDef } from './DataTable';

interface Props {
  data: CategoryEventCount[];
  isLoading?: boolean;
}

const EventsPerCategoryChart = ({ data, isLoading }: Props) => {
  const { t } = useTranslation('organizer');

  const columns: ColumnDef<CategoryEventCount>[] = [
    { key: 'category', label: 'Category' },
    { key: 'eventCount', label: 'Events', align: 'right' },
  ];

  return (
    <ChartCard
      title={t('analytics.charts.eventsPerCategory')}
      isLoading={isLoading}
      isEmpty={!isLoading && data.length === 0}
      dataTable={<DataTable columns={columns} rows={data} rowKey="category" />}
    >
      <ResponsiveContainer width="100%" height={350}>
        <BarChart layout="vertical" data={data} margin={{ top: 4, right: 32, left: 80, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" allowDecimals={false} />
          <YAxis type="category" dataKey="category" width={80} tick={{ fontSize: 12 }} />
          <Tooltip />
          <Bar dataKey="eventCount" name="Events">
            {data.map((entry) => (
              <Cell key={entry.category} fill={getCategoryColor(entry.category)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
};

export default EventsPerCategoryChart;
