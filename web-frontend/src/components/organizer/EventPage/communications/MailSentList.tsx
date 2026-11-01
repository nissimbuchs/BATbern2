/**
 * MailSentList (Story 16.3): "What went out" for an event, newest first.
 *
 * Filter chips per audience, one row per send (automatic mails collapsed per template and day by
 * the server), delivered/failed counts on the right. A click opens the mail as sent; a collapsed
 * row expands into its single mails first.
 *
 * Desktop opens the mail inline below its row, one at a time, and a second click closes it. Mobile
 * opens it as a full-screen dialog (see MailDetail).
 *
 * Story 16.8b: the row(s) of a mail just sent are highlighted and show "sending …" until the job
 * reports progress; a newsletter that ended PARTIAL or FAILED offers Retry on its row; organizer
 * test sends are hidden unless "show test sends" is on.
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Collapse,
  FormControlLabel,
  List,
  ListItem,
  ListItemButton,
  Pagination,
  Skeleton,
  Stack,
  Switch,
  Typography,
} from '@mui/material';
import axios from 'axios';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import { useMailMessages, useRetryMail } from '@/hooks/useMailMessages/useMailMessages';
import type { MailAudience, MailMessageSummary } from '@/services/mailMessageService';
import { MailDetail, MailDetailDialog } from './MailDetail';

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
const RETRYABLE_NEWSLETTER = ['PARTIAL', 'FAILED'];

interface MailSentListProps {
  eventCode: string;
  /** Story 16.8b: the mail(s) just sent; their rows are highlighted. */
  highlightKeys?: string[];
}

/** The mail that is open, and the title the mobile dialog shows for it. */
interface OpenMail {
  mailKey: string;
  title: string;
}

/** yyyy-MM-dd of the send in Europe/Zurich, the day the server groups by. */
function zurichDay(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Zurich' }).format(new Date(iso));
}

export const MailSentList: React.FC<MailSentListProps> = ({ eventCode, highlightKeys }) => {
  const { t } = useTranslation('events');
  const { isMobile } = useBreakpoints();
  const [filter, setFilter] = useState<MailAudience | 'ALL'>('ALL');
  const [page, setPage] = useState(1);
  const [includeTests, setIncludeTests] = useState(false);
  const [open, setOpen] = useState<OpenMail | null>(null);
  const retry = useRetryMail(eventCode);
  const toggle = (mail: OpenMail) =>
    setOpen((current) => (current?.mailKey === mail.mailKey ? null : mail));

  // A new send shows on top of the unfiltered first page.
  const highlightSignature = (highlightKeys ?? []).join(',');
  useEffect(() => {
    if (highlightSignature) {
      setFilter('ALL');
      setPage(1);
    }
  }, [highlightSignature]);

  const rowProps: RowProps = {
    eventCode,
    openKey: open?.mailKey ?? null,
    inline: !isMobile,
    onToggle: toggle,
    includeTests,
    highlightKeys: highlightKeys ?? [],
    retryingKey: retry.isPending ? (retry.variables ?? null) : null,
    onRetry: (mailKey) => retry.mutate(mailKey),
  };
  const query = useMailMessages(eventCode, {
    audience: filter === 'ALL' ? undefined : filter,
    includeTests,
    page,
    limit: PAGE_SIZE,
  });
  const retryError = retry.error
    ? axios.isAxiosError(retry.error)
      ? ((retry.error.response?.data as { message?: string } | undefined)?.message ??
        retry.error.message)
      : retry.error.message
    : null;
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
              setOpen(null);
            }}
            data-testid={`mail-filter-${f}`}
          />
        ))}
        <FormControlLabel
          sx={{ ml: { sm: 'auto' } }}
          control={
            <Switch
              size="small"
              checked={includeTests}
              onChange={(e) => {
                setIncludeTests(e.target.checked);
                setPage(1);
                setOpen(null);
              }}
            />
          }
          label={<Typography variant="body2">{t('eventPage.mail.showTests')}</Typography>}
          data-testid="mail-show-tests"
        />
      </Stack>

      {retryError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => retry.reset()}>
          {retryError}
        </Alert>
      )}

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
            <MailRow key={row.mailKey} row={row} {...rowProps} />
          ))}
        </List>
      )}

      {totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
          <Pagination
            count={totalPages}
            page={page}
            onChange={(_e, p) => {
              setPage(p);
              setOpen(null);
            }}
            size="small"
          />
        </Box>
      )}

      {isMobile && (
        <MailDetailDialog
          eventCode={eventCode}
          mailKey={open?.mailKey ?? null}
          title={open?.title}
          onClose={() => setOpen(null)}
        />
      )}
    </Box>
  );
};

interface RowProps {
  eventCode: string;
  openKey: string | null;
  /** Desktop: the open mail renders below its row. Mobile: the list's dialog shows it. */
  inline: boolean;
  onToggle: (mail: OpenMail) => void;
  includeTests: boolean;
  highlightKeys: string[];
  retryingKey: string | null;
  onRetry: (mailKey: string) => void;
}

const HIGHLIGHT_SX = {
  bgcolor: 'action.selected',
  boxShadow: (theme: { palette: { primary: { main: string } } }) =>
    `inset 3px 0 0 ${theme.palette.primary.main}`,
};

function rowTitle(row: MailMessageSummary): string {
  return row.subject ?? row.templateKey ?? row.mailKey;
}

/** One single mail: the row, and on desktop the mail itself below it while it is open. */
const SingleMail: React.FC<RowProps & { row: MailMessageSummary }> = ({
  eventCode,
  openKey,
  inline,
  onToggle,
  highlightKeys,
  retryingKey,
  onRetry,
  row,
}) => {
  const { t } = useTranslation('events');
  const isOpen = openKey === row.mailKey;
  const highlighted = highlightKeys.includes(row.mailKey);
  const retryable =
    row.audience === 'NEWSLETTER' &&
    Boolean(row.newsletterStatus && RETRYABLE_NEWSLETTER.includes(row.newsletterStatus));
  const button = (
    <ListItemButton
      divider={!retryable && !(inline && isOpen)}
      selected={isOpen}
      onClick={() => onToggle({ mailKey: row.mailKey, title: rowTitle(row) })}
      aria-expanded={inline ? isOpen : undefined}
      data-testid={`mail-row-${row.mailKey}`}
      data-highlighted={highlighted || undefined}
      sx={{ alignItems: 'flex-start', gap: 2, px: 1, ...(highlighted && HIGHLIGHT_SX) }}
    >
      <MailRowContent row={row} />
    </ListItemButton>
  );
  return (
    <>
      {retryable ? (
        // Retry sits beside the row, not inside it: a button inside a button is not operable.
        <ListItem
          disablePadding
          divider={!(inline && isOpen)}
          sx={{ flexWrap: { xs: 'wrap', sm: 'nowrap' }, alignItems: 'center' }}
        >
          {button}
          <Button
            size="small"
            color="error"
            onClick={() => onRetry(row.mailKey)}
            disabled={retryingKey !== null}
            sx={{ flex: '0 0 auto', mx: 1 }}
            data-testid={`mail-retry-${row.mailKey}`}
          >
            {t('eventPage.mail.retry')}
          </Button>
        </ListItem>
      ) : (
        button
      )}
      {inline && (
        <Collapse in={isOpen} unmountOnExit>
          <Box
            sx={{
              p: 2,
              borderBottom: 1,
              borderColor: 'divider',
              bgcolor: 'action.hover',
            }}
            data-testid={`mail-inline-${row.mailKey}`}
          >
            <MailDetail eventCode={eventCode} mailKey={row.mailKey} />
          </Box>
        </Collapse>
      )}
    </>
  );
};

const MailRow: React.FC<RowProps & { row: MailMessageSummary }> = (props) => {
  const { eventCode, row, includeTests } = props;
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
      includeTests,
      limit: 100,
    },
    collapsed && expanded
  );

  if (!collapsed) return <SingleMail {...props} />;

  return (
    <>
      <ListItemButton
        divider
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
        data-testid={`mail-row-${row.mailKey}`}
        sx={{ alignItems: 'flex-start', gap: 2, px: 1 }}
      >
        <MailRowContent row={row} />
      </ListItemButton>
      <Collapse in={expanded} unmountOnExit>
        <List disablePadding sx={{ pl: 2 }} data-testid={`mail-group-${row.mailKey}`}>
          {singles.isLoading && <Skeleton height={40} />}
          {(singles.data?.data ?? []).map((single) => (
            <SingleMail key={single.mailKey} {...props} row={single} />
          ))}
          {singles.data && singles.data.pagination.totalItems > singles.data.data.length && (
            <Typography variant="caption" color="text.secondary" sx={{ px: 1 }}>
              {t('eventPage.mail.groupTruncated', { count: singles.data.data.length })}
            </Typography>
          )}
        </List>
      </Collapse>
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
            {row.newsletterStatus === 'PENDING' || row.recipientCount === 0
              ? t('eventPage.mail.sending')
              : t('eventPage.mail.sendingProgress', {
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
