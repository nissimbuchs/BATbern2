/**
 * EventCard Component (Story 4.2 - Task 2b)
 *
 * Archive event card with session preview
 * Supports grid and list view modes
 * Styled to match session cards on homepage (dark theme)
 */

import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Badge } from '@/components/public/ui/badge';
import { SpeakerDisplay } from './Event/SpeakerDisplay';
import type { EventDetailUI, SessionUI } from '@/types/event.types';

interface EventCardProps {
  event: EventDetailUI;
  viewMode: 'grid' | 'list';
}

export function EventCard({ event, viewMode }: EventCardProps) {
  const { t } = useTranslation();

  // Get all sessions with speakers
  const sessions = (event.sessions || []) as SessionUI[];

  // Format date
  const formattedDate = new Date(event.date).toLocaleDateString('de-CH', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  return (
    <Link
      to={`/archive/${event.eventCode}`}
      className={`block ${viewMode === 'list' ? 'w-full' : ''}`}
    >
      <Card className="group bg-zinc-800/60 border-zinc-700 hover:border-zinc-600 transition-colors h-full">
        <CardHeader>
          <div className="flex items-start justify-between gap-4 mb-2">
            {/* Event Code (e.g., BATbern57) */}
            <Badge
              variant="outline"
              className="bg-blue-400/10 text-blue-400 border-blue-400/20 font-mono"
            >
              {event.eventCode}
            </Badge>

            {/* Topic Badge */}
            {event.topic && typeof event.topic === 'object' && (
              <Badge className="bg-zinc-800 text-zinc-300">{event.topic.name}</Badge>
            )}
          </div>

          {/* Event Title */}
          <CardTitle className="font-light text-xl text-zinc-100">{event.title}</CardTitle>

          {/* Date */}
          <p className="text-sm text-zinc-400 mt-2">{formattedDate}</p>
        </CardHeader>

        <CardContent className="space-y-4">
          {/* Session Preview - Show all sessions with speakers */}
          {sessions.length > 0 && (
            <div className="space-y-3">
              <div className={`grid gap-4 ${viewMode === 'list' ? 'grid-cols-2' : 'grid-cols-1'}`}>
                {sessions.map((session) => (
                  <div
                    key={session.sessionId}
                    className="border-t border-zinc-800 pt-3 first:border-t-0 first:pt-0"
                  >
                    {/* Session Title */}
                    <p className="text-sm font-medium text-zinc-200 mb-2">{session.title}</p>

                    {/* Speakers with companies */}
                    {session.speakers && session.speakers.length > 0 ? (
                      <div
                        className={`grid gap-6 ${viewMode === 'list' ? 'grid-cols-2' : 'grid-cols-1'}`}
                      >
                        {session.speakers.map((speaker) => (
                          <SpeakerDisplay
                            key={speaker.username}
                            speaker={speaker}
                            size="small"
                            showProfilePicture={true}
                          />
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-zinc-500">Keine Referenten</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* View Details Link */}
          <div className="pt-2 border-t border-zinc-800">
            <span className="text-blue-400 hover:text-blue-300 text-sm font-medium transition-colors">
              {t('archive.card.viewDetails')} →
            </span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
