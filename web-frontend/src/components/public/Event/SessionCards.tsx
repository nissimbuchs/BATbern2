/**
 * SessionCards Component (Story 4.1.4)
 * Displays sessions in grid/list view with filtering capabilities
 * Hover to expand session description
 */

import { useState, useMemo } from 'react';
import { Button } from '@/components/public/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Badge } from '@/components/public/ui/badge';
import { LayoutGrid, List, Clock, MapPin, Users } from 'lucide-react';
import type { Session } from '@/types/event.types';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';

interface Topic {
  id: string;
  name: string;
  color?: string;
}

interface SessionCardsProps {
  sessions: Session[];
  topics?: Topic[];
}

export const SessionCards = ({ sessions, topics = [] }: SessionCardsProps) => {
  const { t } = useTranslation('events');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedTopics, setSelectedTopics] = useState<string[]>([]);

  // Filter sessions by selected topics
  const filteredSessions = useMemo(() => {
    if (selectedTopics.length === 0) {
      return sessions;
    }

    // Note: Session type doesn't have topicIds in current schema,
    // so filtering is disabled until topics are fully integrated
    return sessions;
  }, [sessions, selectedTopics]);

  const toggleTopic = (topicId: string) => {
    setSelectedTopics((prev) =>
      prev.includes(topicId) ? prev.filter((id) => id !== topicId) : [...prev, topicId]
    );
  };

  const formatSessionTime = (
    startTime: string | null | undefined,
    endTime: string | null | undefined
  ) => {
    if (!startTime || !endTime) {
      return ''; // Session not yet assigned to time slot
    }

    try {
      const start = new Date(startTime);
      const end = new Date(endTime);
      return `${format(start, 'HH:mm')} - ${format(end, 'HH:mm')}`;
    } catch {
      return '';
    }
  };

  const getSpeakerNames = (session: Session): string => {
    if (!session.speakers || session.speakers.length === 0) {
      return t('public.speakers.speakerTBA');
    }
    return session.speakers.map((speaker) => `${speaker.firstName} ${speaker.lastName}`).join(', ');
  };

  if (sessions.length === 0) {
    return null;
  }

  return (
    <div className="py-12">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-3xl font-light text-zinc-100">{t('public.sessions.title')}</h2>
        <div className="flex gap-2">
          <Button
            variant={viewMode === 'grid' ? 'default' : 'outline'}
            size="icon"
            onClick={() => setViewMode('grid')}
            aria-label={t('public.sessions.gridView')}
            className="border-zinc-800"
          >
            <LayoutGrid className="h-4 w-4" />
          </Button>
          <Button
            variant={viewMode === 'list' ? 'default' : 'outline'}
            size="icon"
            onClick={() => setViewMode('list')}
            aria-label={t('public.sessions.listView')}
            className="border-zinc-800"
          >
            <List className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Topic filters */}
      {topics.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-6">
          {topics.map((topic) => (
            <Badge
              key={topic.id}
              className={`cursor-pointer transition-colors ${
                selectedTopics.includes(topic.id)
                  ? 'bg-blue-400 text-zinc-950 hover:bg-blue-500'
                  : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
              }`}
              onClick={() => toggleTopic(topic.id)}
            >
              {topic.name}
            </Badge>
          ))}
        </div>
      )}

      {/* Sessions grid/list */}
      <div className={viewMode === 'grid' ? 'grid grid-cols-1 md:grid-cols-2 gap-6' : 'space-y-4'}>
        {filteredSessions.map((session) => (
          <Card
            key={session.sessionSlug}
            className="group bg-zinc-900/50 border-zinc-800 hover:border-zinc-700 transition-colors"
          >
            <CardHeader>
              <div className="flex items-start justify-between gap-4">
                <CardTitle className="font-light text-xl text-zinc-100">{session.title}</CardTitle>
                <Badge
                  variant="outline"
                  className="bg-blue-400/10 text-blue-400 border-blue-400/20"
                >
                  {session.sessionType}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {session.description && (
                <p className="text-sm text-zinc-400 line-clamp-3 group-hover:line-clamp-none transition-all">
                  {session.description}
                </p>
              )}

              <div className="flex flex-wrap gap-4 text-sm text-zinc-400">
                {session.startTime && session.endTime && (
                  <span className="flex items-center gap-1.5">
                    <Clock className="h-4 w-4" />
                    {formatSessionTime(session.startTime, session.endTime)}
                  </span>
                )}
                {session.room && (
                  <span className="flex items-center gap-1.5">
                    <MapPin className="h-4 w-4" />
                    {session.room}
                  </span>
                )}
                {session.capacity && (
                  <span className="flex items-center gap-1.5">
                    <Users className="h-4 w-4" />
                    {t('public.sessions.seats', { count: session.capacity })}
                  </span>
                )}
              </div>

              <div className="pt-2 border-t border-zinc-800">
                <p className="text-sm text-zinc-300">
                  <span className="text-zinc-500">{t('public.sessions.speaker')}: </span>
                  {getSpeakerNames(session)}
                </p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {filteredSessions.length === 0 && selectedTopics.length > 0 && (
        <div className="text-center py-12">
          <p className="text-zinc-400">{t('public.sessions.noSessions')}</p>
          <Button
            variant="outline"
            onClick={() => setSelectedTopics([])}
            className="mt-4 border-zinc-800"
          >
            {t('public.sessions.clearFilters')}
          </Button>
        </div>
      )}
    </div>
  );
};
