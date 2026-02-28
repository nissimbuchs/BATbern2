/**
 * SpeakerGrid Component (Story 4.1.4)
 * Displays speakers in a responsive grid with session information
 * Hover to show speaker bio
 */

import { Card, CardContent, CardHeader } from '@/components/public/ui/card';
import type { Session } from '@/types/event.types';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { SpeakerDisplay } from './SpeakerDisplay';

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

  const STRUCTURAL_TYPES = new Set(['moderation', 'break', 'lunch']);

  // Aggregate speakers from non-structural sessions only
  const speakersWithSessions = useMemo(() => {
    const speakerMap = new Map<string, SpeakerWithSession>();

    sessions.forEach((session) => {
      if (STRUCTURAL_TYPES.has(session.sessionType ?? '')) return;
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
      <h2 className="text-3xl font-light mb-8 text-zinc-100">{t('common:navigation.speakers')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {speakersWithSessions.map((speaker) => (
          <Card
            key={speaker.username}
            className="group hover:border-blue-400 transition-colors bg-zinc-900/50 border-zinc-800"
          >
            <CardHeader className="pb-4">
              <SpeakerDisplay
                speaker={{
                  username: speaker.username,
                  firstName: speaker.firstName,
                  lastName: speaker.lastName,
                  company: speaker.company,
                  profilePictureUrl: speaker.profilePictureUrl,
                  bio: speaker.bio,
                  speakerRole:
                    (speaker.speakerRole as
                      | 'PRIMARY_SPEAKER'
                      | 'CO_SPEAKER'
                      | 'MODERATOR'
                      | 'PANELIST') || 'PRIMARY_SPEAKER',
                  presentationTitle: undefined,
                  isConfirmed: true,
                }}
                size="medium"
                showProfilePicture={true}
              />
            </CardHeader>
            <CardContent className="pt-0">
              <div className="border-t border-zinc-800 pt-4">
                <p className="text-sm font-medium text-blue-400 mb-2">{speaker.sessionTitle}</p>
                {speaker.bio && <p className="text-sm text-zinc-400 mt-2">{speaker.bio}</p>}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};
