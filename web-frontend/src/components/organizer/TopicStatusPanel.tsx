/**
 * TopicStatusPanel
 * Story 8.2: AC4, AC5, AC7 — Task 9
 *
 * Organizer view of partner topic suggestions.
 * Sortable table with company logos, edit/delete for any topic, and
 * status dropdown + planned-event text input per row.
 * Only rendered for ORGANIZER role.
 */

import React, { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Container,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SaveIcon from '@mui/icons-material/Save';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { format, parseISO } from 'date-fns';
import CompanyLogo from '@/components/shared/Company/CompanyLogo';
import { TopicSuggestionForm } from '@/components/partner/TopicSuggestionForm';
import {
  deleteTopic,
  getTopics,
  updateTopic,
  updateTopicStatus,
  type TopicDTO,
} from '@/services/api/partnerTopicsApi';

// ─── Types ────────────────────────────────────────────────────────────────────

type SortKey = 'title' | 'suggestedByCompany' | 'voteCount' | 'createdAt';
type SortDir = 'asc' | 'desc';

// ─── Per-row local state ──────────────────────────────────────────────────────

interface RowState {
  status: 'PROPOSED' | 'SELECTED' | 'DECLINED';
  plannedEvent: string;
  saving: boolean;
}

// ─── Sorting helper ───────────────────────────────────────────────────────────

function sortTopics(topics: TopicDTO[], key: SortKey, dir: SortDir): TopicDTO[] {
  return [...topics].sort((a, b) => {
    let cmp = 0;
    if (key === 'voteCount') {
      cmp = a.voteCount - b.voteCount;
    } else if (key === 'createdAt') {
      cmp = a.createdAt.localeCompare(b.createdAt); // ISO strings sort lexicographically
    } else {
      cmp = (a[key] ?? '').localeCompare(b[key] ?? '');
    }
    return dir === 'asc' ? cmp : -cmp;
  });
}

// ─── Main component ───────────────────────────────────────────────────────────

const TopicStatusPanel: React.FC = () => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();
  const [sortKey, setSortKey] = useState<SortKey>('voteCount');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [editingTopic, setEditingTopic] = useState<TopicDTO | null>(null);

  // ─── Query ─────────────────────────────────────────────────────────────────

  const {
    data: topics,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['partnerTopics'],
    queryFn: getTopics,
    staleTime: 2 * 60 * 1000,
  });

  // ─── Sorted data ───────────────────────────────────────────────────────────

  const sortedTopics = useMemo(
    () => (topics ? sortTopics(topics, sortKey, sortDir) : []),
    [topics, sortKey, sortDir]
  );

  const handleSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir(key === 'voteCount' || key === 'createdAt' ? 'desc' : 'asc');
    }
  };

  // ─── Per-row local state ───────────────────────────────────────────────────

  const [rowStates, setRowStates] = useState<Record<string, RowState>>({});

  const getRowStateById = (id: string, tList?: TopicDTO[]): RowState => {
    const topic = (tList ?? []).find((t) => t.id === id);
    return (
      rowStates[id] ?? {
        status: (topic?.status ?? 'PROPOSED') as RowState['status'],
        plannedEvent: topic?.plannedEvent ?? '',
        saving: false,
      }
    );
  };

  const setRowField = <K extends keyof RowState>(topicId: string, field: K, value: RowState[K]) => {
    setRowStates((prev) => ({
      ...prev,
      [topicId]: { ...getRowStateById(topicId, topics), ...prev[topicId], [field]: value },
    }));
  };

  // ─── Status update mutation ────────────────────────────────────────────────

  const statusMutation = useMutation({
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
    if (row.status === 'PROPOSED') return;
    setRowField(topicId, 'saving', true);
    try {
      await statusMutation.mutateAsync({
        topicId,
        status: row.status as 'SELECTED' | 'DECLINED',
        plannedEvent: row.plannedEvent || undefined,
      });
    } finally {
      setRowField(topicId, 'saving', false);
    }
  };

  // ─── Edit mutation ─────────────────────────────────────────────────────────

  const editMutation = useMutation({
    mutationFn: ({ id, title, description }: { id: string; title: string; description: string }) =>
      updateTopic(id, { title, description }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  const handleEdit = async (title: string, description: string) => {
    if (!editingTopic) return;
    await editMutation.mutateAsync({ id: editingTopic.id, title, description });
  };

  // ─── Delete mutation ───────────────────────────────────────────────────────

  const deleteMutation = useMutation({
    mutationFn: (topicId: string) => deleteTopic(topicId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  // ─── Loading state ─────────────────────────────────────────────────────────

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

  // ─── Main render ───────────────────────────────────────────────────────────

  return (
    <Container maxWidth="xl" sx={{ py: 4 }} data-testid="topic-status-panel">
      <Typography variant="h5" sx={{ mb: 3 }}>
        {t('portal.topics.organizer.title')}
      </Typography>

      {!topics || topics.length === 0 ? (
        <Typography color="text.secondary">{t('portal.topics.empty')}</Typography>
      ) : (
        <TableContainer component={Paper} variant="outlined" data-testid="organizer-topics-table">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700, width: '30%' }}>
                  <TableSortLabel
                    active={sortKey === 'title'}
                    direction={sortKey === 'title' ? sortDir : 'asc'}
                    onClick={() => handleSort('title')}
                  >
                    {t('common:labels.title')}
                  </TableSortLabel>
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '15%' }}>
                  <TableSortLabel
                    active={sortKey === 'suggestedByCompany'}
                    direction={sortKey === 'suggestedByCompany' ? sortDir : 'asc'}
                    onClick={() => handleSort('suggestedByCompany')}
                  >
                    {t('portal.topics.organizer.col.company')}
                  </TableSortLabel>
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '6%' }} align="right">
                  <TableSortLabel
                    active={sortKey === 'voteCount'}
                    direction={sortKey === 'voteCount' ? sortDir : 'desc'}
                    onClick={() => handleSort('voteCount')}
                  >
                    {t('portal.topics.organizer.col.votes')}
                  </TableSortLabel>
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '10%' }}>
                  <TableSortLabel
                    active={sortKey === 'createdAt'}
                    direction={sortKey === 'createdAt' ? sortDir : 'desc'}
                    onClick={() => handleSort('createdAt')}
                  >
                    {t('common:labels.date')}
                  </TableSortLabel>
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '14%' }}>
                  {t('common:labels.status')}
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '16%' }}>
                  {t('portal.topics.organizer.plannedEvent')}
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '15%' }} align="right">
                  {t('common:labels.actions')}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedTopics.map((topic) => {
                const row = getRowStateById(topic.id, topics);
                return (
                  <TableRow key={topic.id} hover data-testid={`organizer-topic-row-${topic.id}`}>
                    {/* Title + description */}
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

                    {/* Company logo */}
                    <TableCell>
                      <CompanyLogo
                        companyName={topic.suggestedByCompany}
                        variant="full"
                        maxWidth={80}
                        maxHeight={40}
                      />
                    </TableCell>

                    {/* Vote count */}
                    <TableCell align="right">{topic.voteCount}</TableCell>

                    {/* Date column */}
                    <TableCell>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {format(parseISO(topic.createdAt), 'd MMM yyyy')}
                      </Typography>
                    </TableCell>

                    {/* Status select */}
                    <TableCell>
                      <Select
                        size="small"
                        value={row.status}
                        onChange={(e) =>
                          setRowField(topic.id, 'status', e.target.value as RowState['status'])
                        }
                        sx={{ minWidth: 120 }}
                        data-testid={`status-select-${topic.id}`}
                      >
                        <MenuItem value="PROPOSED">{t('portal.topics.status.proposed')}</MenuItem>
                        <MenuItem value="SELECTED">{t('portal.topics.status.selected')}</MenuItem>
                        <MenuItem value="DECLINED">{t('portal.topics.status.declined')}</MenuItem>
                      </Select>
                    </TableCell>

                    {/* Planned event */}
                    <TableCell>
                      {row.status === 'SELECTED' && (
                        <TextField
                          size="small"
                          placeholder={t('topicStatus.eventCodePlaceholder')}
                          value={row.plannedEvent}
                          onChange={(e) => setRowField(topic.id, 'plannedEvent', e.target.value)}
                          inputProps={{ maxLength: 100 }}
                          data-testid={`planned-event-${topic.id}`}
                        />
                      )}
                    </TableCell>

                    {/* Actions: save + edit + delete */}
                    <TableCell align="right">
                      <Box
                        sx={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'flex-end',
                          gap: 0.5,
                        }}
                      >
                        <Tooltip title={t('common:actions.save')}>
                          <span>
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => handleSave(topic.id)}
                              disabled={row.saving || row.status === 'PROPOSED'}
                              data-testid={`save-status-${topic.id}`}
                            >
                              <SaveIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title={t('common:actions.edit')}>
                          <IconButton
                            size="small"
                            onClick={() => setEditingTopic(topic)}
                            data-testid={`edit-topic-${topic.id}`}
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={t('common:actions.delete')}>
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => deleteMutation.mutate(topic.id)}
                            disabled={deleteMutation.isPending}
                            data-testid={`delete-topic-${topic.id}`}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Edit dialog */}
      <TopicSuggestionForm
        open={editingTopic !== null}
        onClose={() => setEditingTopic(null)}
        onSubmit={handleEdit}
        initialTitle={editingTopic?.title ?? ''}
        initialDescription={editingTopic?.description ?? ''}
        editMode
      />
    </Container>
  );
};

export default TopicStatusPanel;
