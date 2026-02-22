/**
 * TopicStatusPanel
 * Story 8.2: AC4, AC5, AC7 — Task 9
 *
 * Organizer view of partner topic suggestions.
 * Same list as TopicListPage but with status dropdown + planned-event text input per row.
 * Only rendered for ORGANIZER role.
 */

import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  MenuItem,
  Select,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getTopics, updateTopicStatus, type TopicDTO } from '@/services/api/partnerTopicsApi';

const STATUS_COLOR: Record<string, 'default' | 'success' | 'error'> = {
  PROPOSED: 'default',
  SELECTED: 'success',
  DECLINED: 'error',
};

interface RowState {
  status: 'PROPOSED' | 'SELECTED' | 'DECLINED';
  plannedEvent: string;
  saving: boolean;
}

const TopicStatusPanel: React.FC = () => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();

  const {
    data: topics,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['partnerTopics'],
    queryFn: getTopics,
    staleTime: 2 * 60 * 1000,
  });

  // Per-row local state for status + plannedEvent edits
  const [rowStates, setRowStates] = useState<Record<string, RowState>>({});

  const setRowField = <K extends keyof RowState>(topicId: string, field: K, value: RowState[K]) => {
    setRowStates((prev) => ({
      ...prev,
      [topicId]: { ...getRowStateById(topicId, topics), ...prev[topicId], [field]: value },
    }));
  };

  const getRowStateById = (id: string, tList?: TopicDTO[]): RowState => {
    const topic = (tList ?? []).find((t) => t.id === id);
    return (
      rowStates[id] ?? {
        status: topic?.status ?? 'PROPOSED',
        plannedEvent: topic?.plannedEvent ?? '',
        saving: false,
      }
    );
  };

  const updateMutation = useMutation({
    mutationFn: ({
      topicId,
      status,
      plannedEvent,
    }: {
      topicId: string;
      status: 'SELECTED' | 'DECLINED';
      plannedEvent?: string;
    }) => updateTopicStatus(topicId, { status, plannedEvent: plannedEvent || null }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  const handleSave = async (topicId: string) => {
    const row = getRowStateById(topicId, topics);
    if (row.status === 'PROPOSED') return; // organizer can only set SELECTED or DECLINED
    setRowField(topicId, 'saving', true);
    try {
      await updateMutation.mutateAsync({
        topicId,
        status: row.status as 'SELECTED' | 'DECLINED',
        plannedEvent: row.plannedEvent || undefined,
      });
    } finally {
      setRowField(topicId, 'saving', false);
    }
  };

  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }} data-testid="topics-status-panel-loading">
        <Skeleton variant="text" width={300} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={300} />
      </Container>
    );
  }

  if (isError) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error">{t('portal.topics.error')}</Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }} data-testid="topic-status-panel">
      <Typography variant="h5" sx={{ mb: 3 }}>
        {t('portal.topics.organizer.title')}
      </Typography>

      {!topics || topics.length === 0 ? (
        <Typography color="text.secondary">{t('portal.topics.empty')}</Typography>
      ) : (
        <Table size="small" data-testid="organizer-topics-table">
          <TableHead>
            <TableRow>
              <TableCell>Title</TableCell>
              <TableCell>Company</TableCell>
              <TableCell align="right">Votes</TableCell>
              <TableCell>{t('portal.topics.organizer.status')}</TableCell>
              <TableCell>{t('portal.topics.organizer.plannedEvent')}</TableCell>
              <TableCell></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {topics.map((topic) => {
              const row = getRowStateById(topic.id, topics);
              const statusKey = topic.status.toLowerCase() as 'proposed' | 'selected' | 'declined';
              return (
                <TableRow key={topic.id} data-testid={`organizer-topic-row-${topic.id}`}>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>
                      {topic.title}
                    </Typography>
                    {topic.description && (
                      <Typography variant="caption" color="text.secondary">
                        {topic.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{topic.suggestedByCompany}</TableCell>
                  <TableCell align="right">{topic.voteCount}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Chip
                        label={t(`portal.topics.status.${statusKey}`)}
                        color={STATUS_COLOR[topic.status]}
                        size="small"
                      />
                      <Select
                        size="small"
                        value={row.status}
                        onChange={(e) =>
                          setRowField(topic.id, 'status', e.target.value as RowState['status'])
                        }
                        sx={{ minWidth: 130 }}
                        data-testid={`status-select-${topic.id}`}
                      >
                        <MenuItem value="PROPOSED">{t('portal.topics.status.proposed')}</MenuItem>
                        <MenuItem value="SELECTED">{t('portal.topics.status.selected')}</MenuItem>
                        <MenuItem value="DECLINED">{t('portal.topics.status.declined')}</MenuItem>
                      </Select>
                    </Box>
                  </TableCell>
                  <TableCell>
                    {row.status === 'SELECTED' && (
                      <TextField
                        size="small"
                        placeholder="e.g. BATbern58"
                        value={row.plannedEvent}
                        onChange={(e) => setRowField(topic.id, 'plannedEvent', e.target.value)}
                        inputProps={{ maxLength: 100 }}
                        data-testid={`planned-event-${topic.id}`}
                      />
                    )}
                  </TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      variant="contained"
                      onClick={() => handleSave(topic.id)}
                      disabled={row.saving || row.status === 'PROPOSED'}
                      data-testid={`save-status-${topic.id}`}
                    >
                      {row.saving
                        ? t('portal.topics.organizer.saving')
                        : t('portal.topics.organizer.save')}
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </Container>
  );
};

export default TopicStatusPanel;
