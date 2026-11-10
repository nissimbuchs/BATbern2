/**
 * MeetingRsvpSummary (Story 16.9 AC1): the RSVPs of the partner meeting an invite was about,
 * shown on its row in "What went out". Loaded per row through the partner meetings service, so
 * only rows on screen fetch (one page of the list at most); the same query key as
 * PartnerMeetingRsvpPanel, so the two share the cache.
 *
 * "No answer" is the invite's recipients minus the answers. The invite goes to partners and
 * organizers on one To: line (#1163), so organizers who do not answer count as "no answer" too.
 */

import React from 'react';
import { Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getRsvps } from '@/services/api/partnerMeetingsApi';

interface MeetingRsvpSummaryProps {
  meetingId: string;
  mailKey: string;
  recipientCount: number;
}

export const MeetingRsvpSummary: React.FC<MeetingRsvpSummaryProps> = ({
  meetingId,
  mailKey,
  recipientCount,
}) => {
  const { t } = useTranslation('events');
  const { data } = useQuery({
    queryKey: ['meetingRsvps', meetingId, 0],
    queryFn: () => getRsvps(meetingId),
    staleTime: 60 * 1000,
  });
  // A deleted meeting (404) or a failed load shows nothing: the mail itself is still listed.
  if (!data) return null;
  const { accepted, declined, tentative } = data.summary;
  const noAnswer = Math.max(0, recipientCount - accepted - declined - tentative);
  return (
    <Typography
      variant="caption"
      color="text.secondary"
      component="div"
      data-testid={`mail-rsvp-${mailKey}`}
    >
      {t('eventPage.mail.rsvpSummary', { accepted, declined, tentative, noAnswer })}
    </Typography>
  );
};
