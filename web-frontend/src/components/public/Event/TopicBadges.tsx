/**
 * TopicBadges Component (Story 4.1.3)
 * Displays event topics as badges
 */

import { Badge } from '@/components/public/ui/badge';

interface Topic {
  id: string;
  name: string;
  color?: string;
}

interface TopicBadgesProps {
  topics: Topic[] | string[];
}

export const TopicBadges = ({ topics }: TopicBadgesProps) => {
  if (!topics || topics.length === 0) {
    return null;
  }

  // Handle both Topic objects and plain strings
  const topicItems = topics.map((topic, index) => {
    if (typeof topic === 'string') {
      return { id: `topic-${index}`, name: topic };
    }
    return topic;
  });

  return (
    <div className="flex flex-wrap gap-2">
      {topicItems.map((topic) => (
        <Badge
          key={topic.id}
          className="bg-zinc-800 text-zinc-100 hover:bg-zinc-700 border border-zinc-700 px-3 py-1 text-sm"
        >
          {topic.name}
        </Badge>
      ))}
    </div>
  );
};
