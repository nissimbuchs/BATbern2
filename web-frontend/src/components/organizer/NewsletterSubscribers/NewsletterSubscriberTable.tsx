/**
 * NewsletterSubscriberTable — Table with backend-driven sort
 *
 * Mirrors UserTable.tsx pattern but NO in-memory sort — render array as-is.
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import React from 'react';
import {
  Card,
  CardActions,
  CardContent,
  Chip,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  Typography,
} from '@mui/material';
import {
  DeleteOutline,
  MarkEmailRead,
  PeopleAltOutlined,
  PersonOutline as PersonIcon,
  RestartAlt,
  Unsubscribe,
} from '@mui/icons-material';
import Tooltip from '@mui/material/Tooltip';
import Box from '@mui/material/Box';
import { useTranslation } from 'react-i18next';
import { useBreakpoints } from '@/hooks/useBreakpoints';
import type { components } from '@/types/generated/events-api.types';

type SubscriberResponse = components['schemas']['SubscriberResponse'];

type SubscriberAction = 'unsubscribe' | 'resubscribe' | 'unsuppress' | 'delete';

interface NewsletterSubscriberTableProps {
  subscribers: SubscriberResponse[];
  sortBy: string;
  sortDir: 'asc' | 'desc';
  onSortChange: (field: string, dir: 'asc' | 'desc') => void;
  onAction: (action: SubscriberAction, subscriber: SubscriberResponse) => void;
}

const SORTABLE_COLUMNS = ['email', 'firstName', 'language', 'source', 'subscribedAt'] as const;

// Low-value columns hidden on phones (<600px) to avoid horizontal crowding.
const HIDE_ON_XS = new Set<string>(['language', 'source', 'subscribedAt']);
const hideOnXsSx = (col: string) =>
  HIDE_ON_XS.has(col) ? { display: { xs: 'none', sm: 'table-cell' } } : undefined;

const NewsletterSubscriberTable: React.FC<NewsletterSubscriberTableProps> = ({
  subscribers,
  sortBy,
  sortDir,
  onSortChange,
  onAction,
}) => {
  const { t } = useTranslation('newsletterSubscribers');
  const { isMobile } = useBreakpoints();

  const handleSort = (field: string) => {
    if (field === sortBy) {
      onSortChange(field, sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      onSortChange(field, 'asc');
    }
  };

  const isActive = (sub: SubscriberResponse) => !sub.unsubscribedAt && !sub.suppressedAt;
  const isSuppressed = (sub: SubscriberResponse) => !!sub.suppressedAt;

  if (subscribers.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <PeopleAltOutlined sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
        <Typography color="text.secondary">{t('table.empty')}</Typography>
      </Paper>
    );
  }

  const renderStatusChip = (sub: SubscriberResponse) =>
    isSuppressed(sub) ? (
      <Tooltip title={`${sub.bounceType ?? ''} — ${sub.bounceCount ?? 0} bounce(s)`}>
        <Chip
          label={t('status.suppressed')}
          color="warning"
          size="small"
          data-testid={`suppressed-chip-${sub.id}`}
        />
      </Tooltip>
    ) : (
      <Chip
        label={isActive(sub) ? t('status.active') : t('status.unsubscribed')}
        color={isActive(sub) ? 'success' : 'default'}
        size="small"
      />
    );

  // Directly-visible, status-conditional action buttons (no kebab menu).
  // Rendered in both the desktop actions cell and the mobile card actions.
  const renderActions = (sub: SubscriberResponse) => (
    <Box
      sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}
      data-testid={`actions-${sub.id}`}
    >
      {isActive(sub) && (
        <Tooltip title={t('actions.unsubscribe')}>
          <IconButton
            size="small"
            onClick={() => onAction('unsubscribe', sub)}
            aria-label={t('actions.unsubscribe')}
            data-testid={`action-unsubscribe-${sub.id}`}
          >
            <Unsubscribe fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      {!isActive(sub) && !isSuppressed(sub) && (
        <Tooltip title={t('actions.resubscribe')}>
          <IconButton
            size="small"
            onClick={() => onAction('resubscribe', sub)}
            aria-label={t('actions.resubscribe')}
            data-testid={`action-resubscribe-${sub.id}`}
          >
            <MarkEmailRead fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      {isSuppressed(sub) && (
        <Tooltip title={t('actions.unsuppress')}>
          <IconButton
            size="small"
            onClick={() => onAction('unsuppress', sub)}
            aria-label={t('actions.unsuppress')}
            data-testid={`action-unsuppress-${sub.id}`}
          >
            <RestartAlt fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      <Tooltip title={t('actions.delete')}>
        <IconButton
          size="small"
          color="error"
          onClick={() => onAction('delete', sub)}
          aria-label={t('actions.delete')}
          data-testid={`action-delete-${sub.id}`}
        >
          <DeleteOutline fontSize="small" />
        </IconButton>
      </Tooltip>
    </Box>
  );

  if (isMobile) {
    return (
      <Box data-testid="subscriber-cards">
        {subscribers.map((sub) => (
          <Card key={sub.id} sx={{ mb: 2 }} data-testid={`subscriber-card-${sub.id}`}>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="subtitle1" sx={{ wordBreak: 'break-word' }}>
                  {sub.email}
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <Typography variant="body2" color="text.secondary">
                    {sub.firstName ?? '—'}
                  </Typography>
                  {sub.username && (
                    <Tooltip title={t('table.registeredUser')}>
                      <PersonIcon
                        sx={{ fontSize: 16, color: 'text.secondary' }}
                        aria-label={t('table.registeredUser')}
                        data-testid={`registered-badge-${sub.id}`}
                      />
                    </Tooltip>
                  )}
                </Box>
                <Box>{renderStatusChip(sub)}</Box>
              </Stack>
            </CardContent>
            <CardActions sx={{ justifyContent: 'flex-end' }}>{renderActions(sub)}</CardActions>
          </Card>
        ))}
      </Box>
    );
  }

  return (
    <TableContainer component={Paper}>
      <Table data-testid="subscriber-table">
        <TableHead>
          <TableRow>
            {SORTABLE_COLUMNS.map((col) => (
              <TableCell key={col} sx={hideOnXsSx(col)}>
                <TableSortLabel
                  active={sortBy === col}
                  direction={sortBy === col ? sortDir : 'asc'}
                  onClick={() => handleSort(col)}
                  data-testid={`sort-${col}`}
                >
                  {t(`table.headers.${col}`)}
                </TableSortLabel>
              </TableCell>
            ))}
            <TableCell>{t('table.headers.status')}</TableCell>
            <TableCell align="right">{t('table.headers.actions')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {subscribers.map((sub) => (
            <TableRow key={sub.id} hover>
              <TableCell>{sub.email}</TableCell>
              <TableCell>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  {sub.firstName ?? '—'}
                  {sub.username && (
                    <Tooltip title={t('table.registeredUser')}>
                      <PersonIcon
                        sx={{ fontSize: 16, color: 'text.secondary' }}
                        aria-label={t('table.registeredUser')}
                        data-testid={`registered-badge-${sub.id}`}
                      />
                    </Tooltip>
                  )}
                </Box>
              </TableCell>
              <TableCell sx={hideOnXsSx('language')}>
                <Chip label={sub.language ?? '—'} size="small" variant="outlined" />
              </TableCell>
              <TableCell sx={hideOnXsSx('source')}>
                <Chip label={sub.source ?? '—'} size="small" variant="outlined" />
              </TableCell>
              <TableCell sx={hideOnXsSx('subscribedAt')}>
                {sub.subscribedAt ? new Date(sub.subscribedAt).toLocaleDateString() : '—'}
              </TableCell>
              <TableCell>{renderStatusChip(sub)}</TableCell>
              <TableCell align="right">{renderActions(sub)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default NewsletterSubscriberTable;
