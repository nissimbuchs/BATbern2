/**
 * TopicListPage
 * Story 8.2: AC1, AC2, AC5, AC7, AC8 — Task 7
 *
 * Displays all topic suggestions as a sortable table.
 * Columns: Topic (title + description + status), Company, Actions (vote + edit/delete).
 * Partner can vote/unvote and submit new topics.
 */

import React, { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  IconButton,
  Paper,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  Tooltip,
  Typography,
} from '@mui/material';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbUpOutlinedIcon from '@mui/icons-material/ThumbUpOutlined';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { format, parseISO } from 'date-fns';
import { useAuth } from '@/hooks/useAuth/useAuth';
import CompanyLogo from '@/components/shared/Company/CompanyLogo';
import { TopicSuggestionForm } from './TopicSuggestionForm';
import {
  castVote,
  deleteTopic,
  getTopics,
  removeVote,
  suggestTopic,
  updateTopic,
  type TopicDTO,
} from '@/services/api/partnerTopicsApi';

// ─── Types ────────────────────────────────────────────────────────────────────

type SortKey = 'title' | 'suggestedByCompany' | 'voteCount' | 'createdAt';
type SortDir = 'asc' | 'desc';

// ─── Status badge colours ─────────────────────────────────────────────────────

const STATUS_COLOR: Record<string, 'default' | 'success' | 'error'> = {
  PROPOSED: 'default',
  SELECTED: 'success',
  DECLINED: 'error',
};

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

const TopicListPage: React.FC = () => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [formOpen, setFormOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<TopicDTO | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>('voteCount');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

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

  // ─── Suggest mutation ──────────────────────────────────────────────────────

  const suggestMutation = useMutation({
    mutationFn: ({ title, description }: { title: string; description: string }) =>
      suggestTopic({ title, description }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  // ─── Edit / Delete mutations ───────────────────────────────────────────────

  const editMutation = useMutation({
    mutationFn: ({ id, title, description }: { id: string; title: string; description: string }) =>
      updateTopic(id, { title, description }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (topicId: string) => deleteTopic(topicId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  const handleEdit = async (title: string, description: string) => {
    if (!editingTopic) return;
    await editMutation.mutateAsync({ id: editingTopic.id, title, description });
  };

  // ─── Vote mutations (optimistic) ───────────────────────────────────────────

  const castVoteMutation = useMutation({
    mutationFn: (topicId: string) => castVote(topicId),
    onMutate: async (topicId) => {
      await queryClient.cancelQueries({ queryKey: ['partnerTopics'] });
      const prev = queryClient.getQueryData<TopicDTO[]>(['partnerTopics']);
      queryClient.setQueryData<TopicDTO[]>(['partnerTopics'], (old) =>
        (old ?? []).map((t) =>
          t.id === topicId ? { ...t, voteCount: t.voteCount + 1, currentPartnerHasVoted: true } : t
        )
      );
      return { prev };
    },
    onError: (_err, _topicId, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(['partnerTopics'], ctx.prev);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  const removeVoteMutation = useMutation({
    mutationFn: (topicId: string) => removeVote(topicId),
    onMutate: async (topicId) => {
      await queryClient.cancelQueries({ queryKey: ['partnerTopics'] });
      const prev = queryClient.getQueryData<TopicDTO[]>(['partnerTopics']);
      queryClient.setQueryData<TopicDTO[]>(['partnerTopics'], (old) =>
        (old ?? []).map((t) =>
          t.id === topicId
            ? { ...t, voteCount: Math.max(0, t.voteCount - 1), currentPartnerHasVoted: false }
            : t
        )
      );
      return { prev };
    },
    onError: (_err, _topicId, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(['partnerTopics'], ctx.prev);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

  const handleVoteToggle = (topic: TopicDTO) => {
    if (topic.currentPartnerHasVoted) {
      removeVoteMutation.mutate(topic.id);
    } else {
      castVoteMutation.mutate(topic.id);
    }
  };

  const handleSuggest = async (title: string, description: string) => {
    await suggestMutation.mutateAsync({ title, description });
  };

  // ─── Loading state ─────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }} data-testid="topics-loading">
        <Skeleton variant="text" width={300} height={40} sx={{ mb: 2 }} />
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} variant="rectangular" height={72} sx={{ mb: 1, borderRadius: 1 }} />
        ))}
      </Container>
    );
  }

  // ─── Error state ───────────────────────────────────────────────────────────

  if (isError) {
    return (
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Alert severity="error" data-testid="topics-error">
          {t('portal.topics.error')}
        </Alert>
      </Container>
    );
  }

  // ─── Main render ───────────────────────────────────────────────────────────

  return (
    <Container maxWidth="xl" sx={{ py: 4 }} data-testid="topic-list-page">
      {/* Header row */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5">{t('portal.topics.title')}</Typography>
        <Button
          variant="contained"
          onClick={() => setFormOpen(true)}
          data-testid="suggest-topic-button"
        >
          {t('portal.topics.suggest')}
        </Button>
      </Box>

      {/* Empty state */}
      {!topics || topics.length === 0 ? (
        <Typography color="text.secondary" data-testid="topics-empty">
          {t('portal.topics.empty')}
        </Typography>
      ) : (
        <TableContainer component={Paper} variant="outlined" data-testid="topics-list">
          <Table size="medium">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700, width: '55%' }}>
                  <TableSortLabel
                    active={sortKey === 'title'}
                    direction={sortKey === 'title' ? sortDir : 'asc'}
                    onClick={() => handleSort('title')}
                  >
                    {t('portal.topics.col.topic')}
                  </TableSortLabel>
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '15%' }}>
                  <TableSortLabel
                    active={sortKey === 'suggestedByCompany'}
                    direction={sortKey === 'suggestedByCompany' ? sortDir : 'asc'}
                    onClick={() => handleSort('suggestedByCompany')}
                  >
                    {t('portal.topics.col.company')}
                  </TableSortLabel>
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '10%' }}>
                  <TableSortLabel
                    active={sortKey === 'createdAt'}
                    direction={sortKey === 'createdAt' ? sortDir : 'desc'}
                    onClick={() => handleSort('createdAt')}
                  >
                    {t('portal.topics.col.date')}
                  </TableSortLabel>
                </TableCell>
                <TableCell sx={{ fontWeight: 700, width: '20%' }} align="right">
                  {t('portal.topics.col.actions')}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedTopics.map((topic) => {
                const statusKey = topic.status.toLowerCase() as
                  | 'proposed'
                  | 'selected'
                  | 'declined';
                const isOwn = topic.suggestedByCompany === user?.companyName;
                return (
                  <TableRow key={topic.id} hover data-testid={`topic-item-${topic.id}`}>
                    {/* Topic column */}
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        <Typography variant="subtitle2" fontWeight={600}>
                          {topic.title}
                        </Typography>
                        <Chip
                          label={t(`portal.topics.status.${statusKey}`)}
                          color={STATUS_COLOR[topic.status]}
                          size="small"
                          data-testid={`topic-status-${topic.id}`}
                        />
                        {topic.status === 'SELECTED' && topic.plannedEvent && (
                          <Typography variant="caption" color="text.secondary">
                            {t('portal.topics.plannedFor')}: {topic.plannedEvent}
                          </Typography>
                        )}
                      </Box>
                      {topic.description && (
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          {topic.description}
                        </Typography>
                      )}
                    </TableCell>

                    {/* Company column */}
                    <TableCell>
                      <CompanyLogo
                        companyName={topic.suggestedByCompany}
                        variant="full"
                        maxWidth={80}
                        maxHeight={48}
                      />
                    </TableCell>

                    {/* Date column */}
                    <TableCell>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {format(parseISO(topic.createdAt), 'd MMM yyyy')}
                      </Typography>
                    </TableCell>

                    {/* Actions column */}
                    <TableCell align="right">
                      <Box
                        sx={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'flex-end',
                          gap: 1,
                        }}
                      >
                        {/* Edit / Delete — own company only */}
                        {isOwn && (
                          <>
                            <Tooltip title={t('portal.topics.edit')}>
                              <IconButton
                                size="small"
                                onClick={() => setEditingTopic(topic)}
                                data-testid={`edit-topic-${topic.id}`}
                              >
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title={t('portal.topics.delete')}>
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
                          </>
                        )}

                        {/* Vote button + count */}
                        <Box
                          sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}
                        >
                          <Tooltip
                            title={
                              topic.currentPartnerHasVoted
                                ? t('portal.topics.unvote')
                                : t('portal.topics.vote')
                            }
                          >
                            <IconButton
                              size="small"
                              color={topic.currentPartnerHasVoted ? 'primary' : 'default'}
                              onClick={() => handleVoteToggle(topic)}
                              aria-label={
                                topic.currentPartnerHasVoted
                                  ? t('portal.topics.unvote')
                                  : t('portal.topics.vote')
                              }
                              data-testid={`vote-button-${topic.id}`}
                            >
                              {topic.currentPartnerHasVoted ? (
                                <ThumbUpIcon fontSize="small" />
                              ) : (
                                <ThumbUpOutlinedIcon fontSize="small" />
                              )}
                            </IconButton>
                          </Tooltip>
                          <Typography variant="caption" data-testid={`vote-count-${topic.id}`}>
                            {topic.voteCount}
                          </Typography>
                        </Box>
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Suggestion form dialog */}
      <TopicSuggestionForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSubmit={handleSuggest}
      />

      {/* Edit form dialog */}
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

export default TopicListPage;
