/**
 * InvitationDetails Component - Story 6.2
 *
 * Displays event information for a speaker invitation.
 */

import { Card } from '@/components/public/ui/card';
import { Calendar, Clock, MapPin, User } from 'lucide-react';
import type { InvitationResponse } from '@/types/speakerInvitation.types';
import { useTranslation } from 'react-i18next';

interface InvitationDetailsProps {
  invitation: InvitationResponse;
}

export const InvitationDetails = ({ invitation }: InvitationDetailsProps) => {
  const { t } = useTranslation('speakerInvitation');

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('de-CH', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  return (
    <Card className="p-8 mb-6">
      <div className="text-center mb-6">
        <h1 className="text-3xl font-light mb-2">
          {t('invitationDetails.title', 'Speaker Invitation')}
        </h1>
        <p className="text-xl text-zinc-400">
          {t('invitationDetails.subtitle', "You've been invited to speak")}
        </p>
      </div>

      <div className="space-y-4">
        <div className="flex items-start gap-3">
          <Calendar className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-medium">{t('invitationDetails.event', 'Event')}</p>
            <p className="text-zinc-300">{invitation.eventCode}</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <Clock className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-medium">{t('invitationDetails.deadline', 'Response Deadline')}</p>
            <p className="text-zinc-300">{formatDate(invitation.expiresAt)}</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <User className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-medium">{t('invitationDetails.invitedBy', 'Invited By')}</p>
            <p className="text-zinc-300">{invitation.createdBy}</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <MapPin className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-medium">{t('invitationDetails.location', 'Location')}</p>
            <p className="text-zinc-300">Bern, Switzerland</p>
          </div>
        </div>
      </div>
    </Card>
  );
};

export default InvitationDetails;
