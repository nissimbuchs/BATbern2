/**
 * MailSentList (Story 16.3): "What went out" for an event, newest first.
 *
 * Filter chips per audience, one row per send (automatic mails collapsed per template and day by
 * the server), delivered/failed counts on the right. A click opens the mail as sent; a collapsed
 * row expands into its single mails first.
 */

import React, { useState } from 'react';
import {
  Box,
  Chip,
  Collapse,
  List,
  ListItemButton,
  Pagination,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useMailMessages } from '@/hooks/useMailMessages/useMailMessages';
import type { MailAudience, MailMessageSummary } from '@/services/mailMessageService';

const FILTERS: Array<MailAudience | 'ALL'> = [
  'ALL',
  'NEWSLETTER',
  'REGISTRANTS',
  'SPEAKERS',
  'VENUE',
  'PARTNERS',
];
const PAGE_SIZE = 20;
const ACTIVE_NEWSLETTER = ['PENDING', 'IN_PROGRESS'];

interface MailSentListProps {
  eventCode: string;
  onOpen: (mailKey: string) => void;
}

/** yyyy-MM-dd of the send in Europe/Zurich, the day the server groups by. */
function zurichDay(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Zurich' }).format(new Date(iso));
}

export const MailSentList: React.FC<MailSentListProps> = ({ eventCode, onOpen }) => {
  const { t } = useTranslation('events');
  const [filter, setFilter] = useState<MailAudience | 'ALL'>('ALL');
  const [page, setPage] = useState(1);
  const query = useMailMessages(eventCode, {
    audience: filter === 'ALL' ? undefined : filter,
    page,
    limit: PAGE_SIZE,
  });
  const rows = query.data?.data ?? [];
  const totalPages = query.data?.pagination.totalPages ?? 0;

  return (
    <Box data-testid="mail-sent-list">
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 2 }}>
        {FILTERS.map((f) => (
          <Chip
            key={f}
            label={t(`eventPage.mail.filters.${f}`)}
            color={filter === f ? 'primary' : 'default'}
            variant={filter === f ? 'filled' : 'outlined'}
            onClick={() => {
              setFilter(f);
              setPage(1);
            }}
            data-testid={`mail-filter-${f}`}
          />
        ))}
      </Stack>

      {query.isLoading ? (
        <Skeleton variant="rectangular" height={120} />
      ) : query.isError ? (
        <Typography color="error" variant="body2">
          {t('eventPage.mail.loadError')}
        </Typography>
      ) : rows.length === 0 ? (
        <Typography variant="body2" color="text.secondary" data-testid="mail-sent-empty">
          {filter === 'ALL' ? t('eventPage.mail.emptyAll') : t('eventPage.mail.emptyFilter')}
        </Typography>
      ) : (
        <List disablePadding>
          {rows.map((row) => (
            <MailRow key={row.mailKey} eventCode={eventCode} row={row} onOpen={onOpen} />
          ))}
        </List>
      )}

      {totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
          <Pagination
            count={totalPages}
            page={page}
            onChange={(_e, p) => setPage(p)}
            size="small"
          />
        </Box>
      )}
    </Box>
  );
};

interface MailRowProps {
  eventCode: string;
  row: MailMessageSummary;
  onOpen: (mailKey: string) => void;
}

const MailRow: React.FC<MailRowProps> = ({ eventCode, row, onOpen }) => {
  const { t } = useTranslation('events');
  const [expanded, setExpanded] = useState(false);
  const collapsed = row.messageCount > 1;
  const singles = useMailMessages(
    eventCode,
    {
      templateKey: row.templateKey ?? undefined,
      date: zurichDay(row.sentAt),
      audience: row.audience,
      grouped: false,
      limit: 100,
    },
    collapsed && expanded
  );

  return (
    <>
      <ListItemButton
        divider
        onClick={() => (collapsed ? setExpanded((e) => !e) : onOpen(row.mailKey))}
        aria-expanded={collapsed ? expanded : undefined}
        data-testid={`mail-row-${row.mailKey}`}
        sx={{ alignItems: 'flex-start', gap: 2, px: 1 }}
      >
        <MailRowContent row={row} />
      </ListItemButton>
      {collapsed && (
        <Collapse in={expanded} unmountOnExit>
          <List disablePadding sx={{ pl: 2 }} data-testid={`mail-group-${row.mailKey}`}>
            {singles.isLoading && <Skeleton height={40} />}
            {(singles.data?.data ?? []).map((single) => (
              <ListItemButton
                key={single.mailKey}
                divider
                onClick={() => onOpen(single.mailKey)}
                data-testid={`mail-row-${single.mailKey}`}
                sx={{ alignItems: 'flex-start', gap: 2, px: 1 }}
              >
                <MailRowContent row={single} />
              </ListItemButton>
            ))}
            {singles.data && singles.data.pagination.totalItems > singles.data.data.length && (
              <Typography variant="caption" color="text.secondary" sx={{ px: 1 }}>
                {t('eventPage.mail.groupTruncated', { count: singles.data.data.length })}
              </Typography>
            )}
          </List>
        </Collapse>
      )}
    </>
  );
};

const MailRowContent: React.FC<{ row: MailMessageSummary }> = ({ row }) => {
  const { t, i18n } = useTranslation('events');
  const sentAt = new Date(row.sentAt).toLocaleString(i18n.language, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  const sender = row.senderUsername ?? t('eventPage.mail.automatic');
  const audience = t(`eventPage.mail.filters.${row.audience}`, row.audience);
  const source =
    row.contentSource === 'OWN_TEXT' ? t('eventPage.mail.ownText') : t('eventPage.mail.template');
  const title =
    row.messageCount > 1
      ? t('eventPage.mail.groupTitle', {
          template: row.templateKey ?? '',
          count: row.messageCount,
        })
      : (row.subject ?? row.templateKey ?? '');
  const active = row.newsletterStatus && ACTIVE_NEWSLETTER.includes(row.newsletterStatus);

  return (
    <Box sx={{ display: 'flex', width: '100%', gap: 2, flexWrap: { xs: 'wrap', sm: 'nowrap' } }}>
      <Box sx={{ flex: '1 1 auto', minWidth: 0 }}>
        <Typography variant="caption" color="text.secondary" component="div">
          {sentAt} · {sender} → {audience} · {source}
          {row.test && (
            <Chip label="TEST" size="small" color="warning" variant="outlined" sx={{ ml: 1 }} />
          )}
        </Typography>
        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
          {title}
        </Typography>
      </Box>
      <Box sx={{ flex: '0 0 auto', textAlign: { xs: 'left', sm: 'right' } }}>
        {active ? (
          <Typography variant="caption" color="text.secondary">
            {t('eventPage.mail.sendingProgress', {
              sent: row.deliveredCount + row.failedCount,
              total: row.recipientCount,
            })}
          </Typography>
        ) : (
          <Typography variant="caption" component="div">
            {t('eventPage.mail.delivered', { count: row.deliveredCount })}
            {row.failedCount > 0 && (
              <Box component="span" sx={{ color: 'error.main' }}>
                {' · '}
                {t('eventPage.mail.failed', { count: row.failedCount })}
              </Box>
            )}
          </Typography>
        )}
        <Typography variant="caption" color="text.secondary" component="div">
          {t('eventPage.mail.recipients', { count: row.recipientCount })}
        </Typography>
      </Box>
    </Box>
  );
};

export default MailSentList;
