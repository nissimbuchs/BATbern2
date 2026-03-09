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

type RegistrationStatus = 'REGISTERED' | 'CONFIRMED' | 'WAITLIST' | 'CANCELLED';

interface EventCardProps {
  event: EventDetailUI;
  viewMode: 'grid' | 'list';
  linkPrefix?: string;
  /** Optional registration status for this event (Story 10.10, AC5) */
  myRegistrationStatus?: RegistrationStatus;
}

const STATUS_CHIP_STYLES: Record<RegistrationStatus, string> = {
  CONFIRMED: 'bg-green-400/20 text-green-400 border-green-400/30',
  REGISTERED: 'bg-amber-400/20 text-amber-400 border-amber-400/30',
  WAITLIST: 'bg-blue-400/20 text-blue-400 border-blue-400/30',
  CANCELLED: 'bg-zinc-600/20 text-zinc-400 border-zinc-600/30',
};

export function EventCard({
  event,
  viewMode,
  linkPrefix = '/archive/',
  myRegistrationStatus,
}: EventCardProps) {
  const { t } = useTranslation();
  const { t: tEvents } = useTranslation('events');

  const STRUCTURAL_TYPES = new Set(['moderation', 'break', 'lunch']);

  // Get non-structural sessions only (moderation/break/lunch don't belong on a card)
  const sessions = (event.sessions || []).filter(
    (s) => !STRUCTURAL_TYPES.has(s.sessionType ?? '')
  ) as SessionUI[];

  // Show all sessions (no limit)
  const displayedSessions = sessions;

  // Format date (use Swiss German locale for Swiss company)
  const formattedDate = new Date(event.date).toLocaleDateString('de-CH', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  return (
    <Link
      to={`${linkPrefix}${event.eventCode}`}
      className={`block ${viewMode === 'list' ? 'w-full' : ''}`}
    >
      <div
        className={viewMode === 'grid' ? 'grid-card' : 'list-card'}
        data-testid={`event-card-${event.eventCode}`}
        data-view-mode={viewMode}
      >
        <Card className="group bg-zinc-800/60 border-zinc-700 hover:border-zinc-600 transition-colors h-full">
          {/* Theme Image */}
          {event.themeImageUrl && (
            <div
              className={`overflow-hidden ${viewMode === 'list' ? 'flex-shrink-0 w-48' : 'w-full h-48'}`}
            >
              <img
                src={event.themeImageUrl}
                alt={`${event.title} theme image`}
                className="w-full h-full object-cover"
              />
            </div>
          )}

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

              {/* Registration Status Chip (Story 10.10, AC5) */}
              {myRegistrationStatus && (
                <Badge
                  variant="outline"
                  className={STATUS_CHIP_STYLES[myRegistrationStatus]}
                  data-testid="event-card-status-chip"
                >
                  {tEvents(`eventCard.statusChip.${myRegistrationStatus.toLowerCase()}`)}
                </Badge>
              )}
            </div>

            {/* Event Title */}
            <CardTitle className="font-light text-xl text-zinc-100">{event.title}</CardTitle>

            {/* Date and Venue */}
            <div className="space-y-1 mt-2">
              <p className="text-sm text-zinc-400">{formattedDate}</p>
              {event.venueName && <p className="text-sm text-zinc-400">{event.venueName}</p>}
            </div>
          </CardHeader>

          <CardContent className="space-y-4">
            {/* Session Preview - Show all sessions */}
            {displayedSessions.length > 0 && (
              <div className="space-y-3">
                <div
                  className={`grid gap-4 ${viewMode === 'list' ? 'grid-cols-2' : 'grid-cols-1'}`}
                >
                  {displayedSessions.map((session, index) => (
                    <div
                      key={session.sessionId || session.id || `session-${index}`}
                      className="border-t border-zinc-800 pt-3 first:border-t-0 first:pt-0"
                    >
                      {/* Session Title */}
                      <p className="text-sm font-medium text-zinc-200 mb-2">{session.title}</p>

                      {/* Speakers with companies */}
                      {session.speakers && session.speakers.length > 0 ? (
                        <div
                          className={`grid gap-6 ${viewMode === 'list' ? 'grid-cols-2' : 'grid-cols-1'}`}
                        >
                          {session.speakers.map((speaker, speakerIndex) => (
                            <SpeakerDisplay
                              key={speaker.username || `speaker-${index}-${speakerIndex}`}
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
      </div>
    </Link>
  );
}
