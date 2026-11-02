/**
 * MailOpenBadge (Story 16.7 AC2): the number of open conversations across all events and without
 * event, on the "Mail" navigation entry. Hidden at zero. Refreshed every 60 s and after every change
 * made in this browser (the conversation mutations invalidate the counts).
 */

import React from 'react';
import { Badge } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useConversationCounts } from '@/hooks/useMailConversations/useMailConversations';

export const MailOpenBadge: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { t } = useTranslation('common');
  const open = useConversationCounts().data?.open ?? 0;
  return (
    <Badge
      badgeContent={open}
      color="warning"
      invisible={open === 0}
      max={99}
      slotProps={{ badge: { 'aria-label': t('navigation.mailOpenCount', { count: open }) } }}
      data-testid="nav-mail-badge"
      data-count={open}
    >
      {children}
    </Badge>
  );
};

export default MailOpenBadge;
