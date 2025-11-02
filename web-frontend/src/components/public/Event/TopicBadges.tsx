/**
 * TopicBadges Component
 * Story 4.1.3: Event Landing Page Hero Section
 */

import { Badge } from '@/components/public/ui/badge';

interface Topic {
  id: string;
  name: string;
  color?: string;
}

interface TopicBadgesProps {
  topics: Topic[];
}

export const TopicBadges = ({ topics }: TopicBadgesProps) => {
  return (
    <div className="flex flex-wrap gap-2">
      {topics.map((topic) => (
        <Badge
          key={topic.id}
          className="bg-zinc-800 text-zinc-100 hover:bg-zinc-700"
        >
          {topic.name}
        </Badge>
      ))}
    </div>
  );
};
