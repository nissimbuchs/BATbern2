/**
 * SpeakerGrid Component (Story 4.1.4)
 * Displays speakers in a responsive grid with their talk title and bio.
 *
 * Renders exactly the `speakers` of the Public Events read model (2026-10-07). The server decides
 * which speakers are public and whether talk titles are included (not in the SPEAKERS phase), so
 * this component holds no visibility rules of its own.
 */

import { Card, CardContent, CardHeader } from '@/components/public/ui/card';
import type { PublicSpeaker } from '@/types/event.types';
import { useTranslation } from 'react-i18next';
import { SpeakerDisplay } from './SpeakerDisplay';

interface SpeakerGridProps {
  speakers: PublicSpeaker[];
}

type SpeakerRole = 'PRIMARY_SPEAKER' | 'CO_SPEAKER' | 'MODERATOR' | 'PANELIST';

export const SpeakerGrid = ({ speakers }: SpeakerGridProps) => {
  const { t } = useTranslation('events');

  if (speakers.length === 0) {
    return null;
  }

  return (
    <div className="py-12" data-testid="speaker-grid">
      <h2 className="text-3xl font-light mb-8 text-zinc-100">{t('common:navigation.speakers')}</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {speakers.map((speaker) => (
          <Card
            key={speaker.username}
            data-testid="speaker-card"
            className="group hover:border-blue-400 transition-colors bg-zinc-700 border-zinc-600"
          >
            <CardHeader className="pb-4">
              <SpeakerDisplay
                speaker={{
                  username: speaker.username,
                  firstName: speaker.firstName ?? '',
                  lastName: speaker.lastName ?? '',
                  company: speaker.company ?? undefined,
                  companyDisplayName: speaker.companyDisplayName ?? undefined,
                  companyLogoUrl: speaker.companyLogoUrl ?? undefined,
                  profilePictureUrl: speaker.profilePictureUrl ?? undefined,
                  bio: speaker.bio ?? undefined,
                  speakerRole: (speaker.speakerRole as SpeakerRole) || 'PRIMARY_SPEAKER',
                  presentationTitle: undefined,
                  isConfirmed: true,
                }}
                size="medium"
                showProfilePicture={true}
              />
            </CardHeader>
            <CardContent className="pt-0">
              <div className="border-t border-zinc-800 pt-4">
                {speaker.talkTitle && (
                  <p
                    className="text-sm font-medium text-blue-400 mb-2"
                    data-testid="speaker-talk-title"
                  >
                    {speaker.talkTitle}
                  </p>
                )}
                {speaker.bio && <p className="text-sm text-zinc-400 mt-2">{speaker.bio}</p>}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};
