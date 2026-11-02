/**
 * ParticipantMailList (Story 16.10): every mail we sent to one person (or the partner contacts of
 * one company) and every mail they sent us, across events, newest first. The same rows and inline
 * detail as the event page (`MailSentList`, `MailDetail`), full-screen dialog on mobile.
 *
 * The server resolves the person to all their addresses (primary and additional). Newsletters are
 * not listed (AC6), and mail before Stories 16.1a / 16.2a, or to an address the person no longer has,
 * is not there: the help line says so (AC8).
 */

import React from 'react';
import { Box, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { MailAudience } from '@/services/mailMessageService';
import { MailSentList } from '@/components/organizer/EventPage/communications/MailSentList';

type Participant = { participantUsername: string } | { participantCompany: string };

/** Newsletters are never in this view (AC6), so no Newsletter chip. */
const PARTICIPANT_FILTERS: Array<MailAudience | 'ALL'> = [
  'ALL',
  'REGISTRANTS',
  'SPEAKERS',
  'PARTNERS',
  'PUBLIC',
];

export const ParticipantMailList: React.FC<{ participant: Participant }> = ({ participant }) => {
  const { t } = useTranslation('events');
  return (
    <Box data-testid="participant-mail">
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('mailCenter.participant.limits')}
      </Typography>
      <MailSentList
        listParams={{ ...participant, direction: 'ANY', grouped: false }}
        filters={PARTICIPANT_FILTERS}
        emptyText={t('mailCenter.participant.empty')}
      />
    </Box>
  );
};

export default ParticipantMailList;
