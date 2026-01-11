/**
 * EventCard Component (Story 4.2 - Task 2b)
 *
 * Archive event card with session preview
 * Supports grid and list view modes
 */

import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { EventDetailUI, SessionUI, SpeakerUI } from '@/types/event.types';

interface EventCardProps {
  event: EventDetailUI;
  viewMode: 'grid' | 'list';
}

export function EventCard({ event, viewMode }: EventCardProps) {
  const { t } = useTranslation();

  // Get first 3 sessions for preview (cast to SessionUI for extended properties)
  const sessions = (event.sessions || []) as SessionUI[];
  const previewSessions = sessions.slice(0, 3);
  const remainingCount = sessions.length > 3 ? sessions.length - 3 : 0;

  // Format date
  const formattedDate = new Date(event.date).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  const cardClassName = viewMode === 'grid' ? 'grid-card' : 'list-card';

  return (
    <Link
      to={`/archive/${event.eventCode}`}
      className={`${cardClassName} block rounded-lg border border-gray-200 bg-white shadow-sm hover:shadow-md transition-shadow ${
        viewMode === 'list' ? 'flex gap-4' : ''
      }`}
    >
      {/* Event Image */}
      {event.themeImageUrl && (
        <div className={viewMode === 'list' ? 'w-48 flex-shrink-0' : 'w-full'}>
          <img
            src={event.themeImageUrl}
            alt={event.title}
            className={`${
              viewMode === 'grid' ? 'w-full h-48' : 'h-full'
            } object-cover rounded-t-lg ${viewMode === 'list' ? 'rounded-l-lg rounded-t-none' : ''}`}
          />
        </div>
      )}

      {/* Event Details */}
      <div className="p-4 flex-1">
        {/* Topic Badge */}
        {event.topic && (
          <span className="inline-block px-2 py-1 text-xs font-medium bg-blue-100 text-blue-800 rounded mb-2">
            {event.topic}
          </span>
        )}

        {/* Title */}
        <h3 className="text-lg font-semibold text-gray-900 mb-2">{event.title}</h3>

        {/* Date and Venue */}
        <div className="text-sm text-gray-600 mb-3">
          <div>{formattedDate}</div>
          {event.venueName && <div>{event.venueName}</div>}
        </div>

        {/* Session Preview */}
        {previewSessions.length > 0 && (
          <div className="mt-3 space-y-2">
            {previewSessions.map((session) => (
              <div key={session.sessionId} className="text-sm">
                <div className="font-medium text-gray-800">{session.title}</div>
                {session.speakers && session.speakers.length > 0 && (
                  <div className="text-gray-600">
                    {(session.speakers as SpeakerUI[]).map((speaker, idx) => (
                      <span key={speaker.speakerId}>
                        {speaker.fullName}
                        {speaker.companyName && ` (${speaker.companyName})`}
                        {idx < session.speakers!.length - 1 && ', '}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}

            {/* Remaining sessions indicator */}
            {remainingCount > 0 && (
              <div className="text-sm text-gray-500">
                {t('archive.card.andMore', { count: remainingCount })}
              </div>
            )}
          </div>
        )}

        {/* View Details Link */}
        <div className="mt-4">
          <span className="text-blue-600 hover:text-blue-800 text-sm font-medium">
            {t('archive.card.viewDetails')} →
          </span>
        </div>
      </div>
    </Link>
  );
}
