/**
 * NeedsAnswerSection (Story 16.2a AC9–AC12): "Needs an answer", the first section of the event's
 * mail page. One row per conversation: status dot, counterpart, where it arrived, date, subject and
 * who answers. Open and waiting first, newest activity first; done below them, greyed. The badge
 * counts the open ones only. Empty: one line, no table (`docs/ux/mail-center-spec.md`, section 1).
 *
 * Same open/close rules as "What went out": one conversation open at a time, inline below its row
 * on desktop, a full-screen dialog on mobile; a second click closes it.
 */

import React from 'react';
import { Box, Chip, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useEventConversations } from '@/hooks/useMailConversations/useMailConversations';
import { ConversationList } from './ConversationList';

interface NeedsAnswerSectionProps {
  eventCode: string;
  /** The open conversation (e.g. from `?conversation=`), or null. */
  openKey: string | null;
  onOpenChange: (conversationKey: string | null) => void;
}

export const NeedsAnswerSection: React.FC<NeedsAnswerSectionProps> = ({
  eventCode,
  openKey,
  onOpenChange,
}) => {
  const { t } = useTranslation('events');
  const query = useEventConversations(eventCode);
  const rows = query.data?.data ?? [];
  const openCount = rows.filter((row) => row.status === 'OPEN').length;

  return (
    <Box data-testid="needs-answer">
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2, gap: 2 }}>
        <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h6">{t('eventPage.mail.conversations.title')}</Typography>
          {openCount > 0 && (
            <Chip
              size="small"
              color="warning"
              label={openCount}
              aria-label={t('eventPage.mail.conversations.openCount', { count: openCount })}
              data-testid="needs-answer-badge"
            />
          )}
        </Box>
      </Box>

      <ConversationList
        query={query}
        openKey={openKey}
        onOpenChange={onOpenChange}
        emptyText={t('eventPage.mail.conversations.nothingOpen')}
        testId="needs-answer"
      />
    </Box>
  );
};

export default NeedsAnswerSection;
