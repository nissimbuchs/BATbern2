/**
 * TopicsTab
 * Story 10.5: Analytics Dashboard (AC4)
 */

import { useAnalyticsTopics } from '@/hooks/useAnalytics';
import EventsPerCategoryChart from './EventsPerCategoryChart';
import TopicScatterChart from './TopicScatterChart';

interface Props {
  fromYear?: number;
}

const TopicsTab = ({ fromYear }: Props) => {
  const { data, isLoading } = useAnalyticsTopics(fromYear);

  return (
    <>
      <EventsPerCategoryChart
        data={data?.eventsPerCategory ?? []}
        isLoading={isLoading}
      />
      <TopicScatterChart
        data={data?.topicScatter ?? []}
        isLoading={isLoading}
      />
    </>
  );
};

export default TopicsTab;
