/**
 * EventProgram Component (Story 4.1.4)
 * Vertical timeline showing session schedule grouped by time slots
 */

import type { Session } from '@/types/event.types';
import { format } from 'date-fns';
import { Clock, MapPin } from 'lucide-react';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { SpeakerDisplay } from './SpeakerDisplay';

interface EventProgramProps {
  sessions: Session[];
}

export const EventProgram = ({ sessions }: EventProgramProps) => {
  const { t } = useTranslation('events');

  // Group sessions by start time
  const timeSlots = useMemo(() => {
    const slotMap = new Map<string, Session[]>();

    sessions.forEach((session) => {
      try {
        if (!session.startTime) {
          console.warn('Session missing start time:', session.sessionSlug);
          return;
        }
        const timeKey = format(new Date(session.startTime), 'HH:mm');
        const existing = slotMap.get(timeKey) || [];
        slotMap.set(timeKey, [...existing, session]);
      } catch (error) {
        console.warn('Invalid session time:', session.sessionSlug, error);
      }
    });

    // Convert map to sorted array
    return Array.from(slotMap.entries())
      .map(([time, sessions]) => ({ time, sessions }))
      .sort((a, b) => a.time.localeCompare(b.time));
  }, [sessions]);

  const formatSessionDuration = (
    startTime: string | null | undefined,
    endTime: string | null | undefined
  ): string => {
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

  if (sessions.length === 0) {
    return null;
  }

  return (
    <div className="py-12">
      <h2 className="text-3xl font-light mb-8 text-zinc-100">{t('public.program.title')}</h2>

      <div className="relative">
        {/* Vertical timeline line */}
        <div className="absolute left-[31px] top-0 bottom-0 w-px bg-zinc-800" />

        {/* Timeline items */}
        <div className="space-y-8">
          {timeSlots.map((slot, slotIndex) => (
            <div key={slot.time} className="relative">
              {/* Time indicator */}
              <div className="flex items-start gap-6">
                <div className="flex items-center justify-center w-16 h-16 rounded-full bg-zinc-900 border-2 border-blue-400 flex-shrink-0 relative z-10">
                  <span className="text-sm font-medium text-blue-400">{slot.time}</span>
                </div>

                {/* Sessions at this time */}
                <div className="flex-1 pt-2 space-y-4">
                  {slot.sessions.map((session) => (
                    <div
                      key={session.sessionSlug}
                      className="bg-zinc-900/50 border border-zinc-800 rounded-lg p-6 hover:border-zinc-700 transition-colors"
                    >
                      <div className="flex items-start justify-between gap-4 mb-3">
                        <h3 className="text-xl font-light text-zinc-100">{session.title}</h3>
                        <span className="text-xs px-2 py-1 rounded bg-blue-400/10 text-blue-400 border border-blue-400/20 whitespace-nowrap">
                          {session.sessionType}
                        </span>
                      </div>

                      {session.description && (
                        <p className="text-sm text-zinc-400 mb-4">{session.description}</p>
                      )}

                      <div className="flex flex-wrap gap-4 text-sm text-zinc-400">
                        <span className="flex items-center gap-1.5">
                          <Clock className="h-4 w-4" />
                          {formatSessionDuration(session.startTime, session.endTime)}
                        </span>
                        {session.room && (
                          <span className="flex items-center gap-1.5">
                            <MapPin className="h-4 w-4" />
                            {session.room}
                          </span>
                        )}
                      </div>

                      <div className="mt-4 pt-4 border-t border-zinc-800">
                        {session.speakers && session.speakers.length > 0 ? (
                          <div>
                            <p className="text-xs text-zinc-500 mb-2">
                              {t('public.program.speaker')}:
                            </p>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                              {session.speakers.map((speaker) => (
                                <SpeakerDisplay
                                  key={speaker.username}
                                  speaker={speaker}
                                  size="small"
                                  showProfilePicture={true}
                                />
                              ))}
                            </div>
                          </div>
                        ) : (
                          <p className="text-sm text-zinc-400">{t('public.speakers.speakerTBA')}</p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Connecting line between slots (except last) */}
              {slotIndex < timeSlots.length - 1 && (
                <div className="absolute left-[31px] top-16 w-px h-8 bg-zinc-800" />
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
