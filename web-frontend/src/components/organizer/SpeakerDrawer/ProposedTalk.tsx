import React from 'react';
import { Box, Button, Paper, Typography } from '@mui/material';
import { Forum as ForumIcon } from '@mui/icons-material';
import { Link as RouterLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import { intlLocale } from '@/utils/date/intlLocale';

interface ProposedTalkProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
}

/**
 * What a self-nominated speaker proposed ("I could speak on that"): title, abstract, when, and the
 * Mail Center conversation where organizers answer. Read-only: the proposal stays as submitted;
 * after promote the talk is edited on the session it seeded.
 */
export const ProposedTalk: React.FC<ProposedTalkProps> = ({ speaker, eventCode }) => {
  const { t, i18n } = useTranslation('organizer');
  if (!speaker.proposedSessionTitle) return null;
  const proposedAt = speaker.proposedAt
    ? new Date(speaker.proposedAt).toLocaleDateString(intlLocale(i18n.language), {
        dateStyle: 'medium',
      })
    : null;
  return (
    <Paper variant="outlined" sx={{ p: 2, mb: 2 }} data-testid="details-proposal">
      <Typography variant="overline" sx={{ color: 'text.secondary' }}>
        {t('speakerDrawer.proposal.title')}
      </Typography>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
        {speaker.proposedSessionTitle}
      </Typography>
      {speaker.proposedAbstract && (
        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', mt: 1 }}>
          {speaker.proposedAbstract}
        </Typography>
      )}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 1.5, flexWrap: 'wrap' }}>
        {proposedAt && (
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {t('speakerDrawer.proposal.submittedOn', { date: proposedAt })}
          </Typography>
        )}
        {speaker.proposalConversationKey && (
          <Button
            component={RouterLink}
            to={`/organizer/events/${encodeURIComponent(eventCode)}?tab=communications&conversation=${encodeURIComponent(speaker.proposalConversationKey)}`}
            size="small"
            startIcon={<ForumIcon />}
          >
            {t('speakerDrawer.proposal.openConversation')}
          </Button>
        )}
      </Box>
    </Paper>
  );
};
