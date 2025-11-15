/**
 * SpeakerGrid Component (Story 4.1.4)
 * Displays speakers in a responsive grid with session information
 * Hover to show speaker bio
 */

import { Card, CardContent, CardHeader } from '@/components/public/ui/card';
import type { Session } from '@/types/event.types';
import { Building2 } from 'lucide-react';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

interface SpeakerWithSession {
  username: string;
  firstName: string;
  lastName: string;
  company?: string;
  profilePictureUrl?: string;
  sessionTitle: string;
  sessionDescription?: string;
  bio?: string;
  speakerRole: string;
}

interface SpeakerGridProps {
  sessions: Session[];
}

export const SpeakerGrid = ({ sessions }: SpeakerGridProps) => {
  const { t } = useTranslation('events');

  // Aggregate speakers from all sessions with their session information
  const speakersWithSessions = useMemo(() => {
    const speakerMap = new Map<string, SpeakerWithSession>();

    sessions.forEach((session) => {
      if (session.speakers && session.speakers.length > 0) {
        session.speakers.forEach((speaker) => {
          // Use primary speaker only (or first speaker if no primary)
          if (!speakerMap.has(speaker.username)) {
            speakerMap.set(speaker.username, {
              username: speaker.username,
              firstName: speaker.firstName,
              lastName: speaker.lastName,
              company: speaker.company,
              profilePictureUrl: speaker.profilePictureUrl,
              sessionTitle: speaker.presentationTitle || session.title,
              sessionDescription: session.description,
              bio: speaker.bio,
              speakerRole: speaker.speakerRole,
            });
          }
        });
      }
    });

    return Array.from(speakerMap.values());
  }, [sessions]);

  if (speakersWithSessions.length === 0) {
    return null;
  }

  return (
    <div className="py-12">
      <h2 className="text-3xl font-light mb-8 text-zinc-100">{t('public.speakers.title')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {speakersWithSessions.map((speaker) => (
          <Card
            key={speaker.username}
            className="group hover:border-blue-400 transition-colors bg-zinc-900/50 border-zinc-800"
          >
            <CardHeader className="flex flex-row items-center gap-4 pb-4">
              <div className="h-16 w-16 rounded-full bg-zinc-800 flex items-center justify-center overflow-hidden flex-shrink-0">
                {speaker.profilePictureUrl ? (
                  <img
                    src={speaker.profilePictureUrl}
                    alt={`${speaker.firstName} ${speaker.lastName}`}
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <span className="text-2xl font-light text-zinc-300">
                    {speaker.firstName[0]}
                    {speaker.lastName[0]}
                  </span>
                )}
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="font-light text-lg text-zinc-100 truncate">
                  {speaker.firstName} {speaker.lastName}
                </h3>
                {speaker.company && (
                  <p className="text-sm text-zinc-400 flex items-center gap-1 truncate">
                    <Building2 className="h-3 w-3 flex-shrink-0" />
                    {speaker.company}
                  </p>
                )}
              </div>
            </CardHeader>
            <CardContent className="pt-0">
              <div className="border-t border-zinc-800 pt-4">
                <p className="text-sm font-medium text-blue-400 mb-2">{speaker.sessionTitle}</p>
                {speaker.bio && (
                  <p className="text-sm text-zinc-400 line-clamp-3 group-hover:line-clamp-none transition-all mt-2">
                    {speaker.bio}
                  </p>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};
