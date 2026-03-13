/**
 * NewsletterSubscriberTable — Table with backend-driven sort
 *
 * Mirrors UserTable.tsx pattern but NO in-memory sort — render array as-is.
 *
 * Story 10.28: Newsletter Subscriber Management Page
 */

import React, { useState } from 'react';
import {
  Chip,
  IconButton,
  Menu,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  Typography,
} from '@mui/material';
import { Cloud as CloudIcon, MoreVert, PeopleAltOutlined } from '@mui/icons-material';
import Tooltip from '@mui/material/Tooltip';
import Box from '@mui/material/Box';
import { useTranslation } from 'react-i18next';
import type { components } from '@/types/generated/events-api.types';

type SubscriberResponse = components['schemas']['SubscriberResponse'];

interface NewsletterSubscriberTableProps {
  subscribers: SubscriberResponse[];
  sortBy: string;
  sortDir: 'asc' | 'desc';
  onSortChange: (field: string, dir: 'asc' | 'desc') => void;
  onAction: (
    action: 'unsubscribe' | 'resubscribe' | 'delete',
    subscriber: SubscriberResponse
  ) => void;
}

const SORTABLE_COLUMNS = ['email', 'firstName', 'language', 'source', 'subscribedAt'] as const;

const NewsletterSubscriberTable: React.FC<NewsletterSubscriberTableProps> = ({
  subscribers,
  sortBy,
  sortDir,
  onSortChange,
  onAction,
}) => {
  const { t } = useTranslation('newsletterSubscribers');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [menuSubscriber, setMenuSubscriber] = useState<SubscriberResponse | null>(null);

  const handleSort = (field: string) => {
    if (field === sortBy) {
      onSortChange(field, sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      onSortChange(field, 'asc');
    }
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, subscriber: SubscriberResponse) => {
    setAnchorEl(event.currentTarget);
    setMenuSubscriber(subscriber);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    setMenuSubscriber(null);
  };

  const handleAction = (action: 'unsubscribe' | 'resubscribe' | 'delete') => {
    if (menuSubscriber) {
      onAction(action, menuSubscriber);
    }
    handleMenuClose();
  };

  const isActive = (sub: SubscriberResponse) => !sub.unsubscribedAt;

  if (subscribers.length === 0) {
    return (
      <Paper sx={{ p: 4, textAlign: 'center' }}>
        <PeopleAltOutlined sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
        <Typography color="text.secondary">{t('table.empty')}</Typography>
      </Paper>
    );
  }

  return (
    <>
      <TableContainer component={Paper}>
        <Table data-testid="subscriber-table">
          <TableHead>
            <TableRow>
              {SORTABLE_COLUMNS.map((col) => (
                <TableCell key={col}>
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
                        <CloudIcon
                          sx={{ fontSize: 16, color: 'info.main' }}
                          aria-label={t('table.registeredUser')}
                          data-testid={`registered-badge-${sub.id}`}
                        />
                      </Tooltip>
                    )}
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip label={sub.language ?? '—'} size="small" variant="outlined" />
                </TableCell>
                <TableCell>
                  <Chip label={sub.source ?? '—'} size="small" variant="outlined" />
                </TableCell>
                <TableCell>
                  {sub.subscribedAt ? new Date(sub.subscribedAt).toLocaleDateString() : '—'}
                </TableCell>
                <TableCell>
                  <Chip
                    label={isActive(sub) ? t('status.active') : t('status.unsubscribed')}
                    color={isActive(sub) ? 'success' : 'default'}
                    size="small"
                  />
                </TableCell>
                <TableCell align="right">
                  <IconButton
                    size="small"
                    onClick={(e) => handleMenuOpen(e, sub)}
                    aria-label={t('actions.openMenu')}
                    data-testid={`actions-${sub.id}`}
                  >
                    <MoreVert />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleMenuClose}>
        {menuSubscriber && isActive(menuSubscriber) && (
          <MenuItem onClick={() => handleAction('unsubscribe')} data-testid="action-unsubscribe">
            {t('actions.unsubscribe')}
          </MenuItem>
        )}
        {menuSubscriber && !isActive(menuSubscriber) && (
          <MenuItem onClick={() => handleAction('resubscribe')} data-testid="action-resubscribe">
            {t('actions.resubscribe')}
          </MenuItem>
        )}
        <MenuItem
          onClick={() => handleAction('delete')}
          data-testid="action-delete"
          sx={{ color: 'error.main' }}
        >
          {t('actions.delete')}
        </MenuItem>
      </Menu>
    </>
  );
};

export default NewsletterSubscriberTable;
