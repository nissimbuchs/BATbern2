/**
 * TopicListPage
 * Story 8.2: AC1, AC2, AC5, AC7, AC8 — Task 7
 *
 * Displays all topic suggestions sorted by vote count descending.
 * Partner can vote/unvote and submit new topics.
 * Status badges: grey (Proposed), green (Selected), red (Declined).
 */

import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  IconButton,
  List,
  ListItem,
  Skeleton,
  Tooltip,
  Typography,
} from '@mui/material';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbUpOutlinedIcon from '@mui/icons-material/ThumbUpOutlined';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth/useAuth';
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

// ─── Status badge colours ─────────────────────────────────────────────────────

const STATUS_COLOR: Record<string, 'default' | 'success' | 'error'> = {
  PROPOSED: 'default',
  SELECTED: 'success',
  DECLINED: 'error',
};

// ─── Main component ───────────────────────────────────────────────────────────

const TopicListPage: React.FC = () => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [formOpen, setFormOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<TopicDTO | null>(null);

  // ─── Query ─────────────────────────────────────────────────────────────────

  const {
    data: topics,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['partnerTopics'],
    queryFn: getTopics,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });

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
    if (!editingTopic) {
      return;
    }
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
      if (ctx?.prev) {
        queryClient.setQueryData(['partnerTopics'], ctx.prev);
      }
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
      if (ctx?.prev) {
        queryClient.setQueryData(['partnerTopics'], ctx.prev);
      }
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
        <List disablePadding data-testid="topics-list">
          {topics.map((topic) => {
            const statusKey = topic.status.toLowerCase() as 'proposed' | 'selected' | 'declined';
            return (
              <ListItem
                key={topic.id}
                divider
                sx={{ py: 2, px: 0, alignItems: 'flex-start', gap: 2 }}
                data-testid={`topic-item-${topic.id}`}
              >
                {/* Content */}
                <Box sx={{ flex: 1 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                    <Typography variant="subtitle1" fontWeight={600}>
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
                  <Typography variant="caption" color="text.secondary">
                    {topic.suggestedByCompany}
                  </Typography>
                </Box>

                {/* Edit / Delete buttons — own company's topics only */}
                {topic.suggestedByCompany === user?.companyName && (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
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
                  </Box>
                )}

                {/* Vote button */}
                <Box
                  sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    minWidth: 56,
                  }}
                >
                  <Button
                    size="small"
                    variant={topic.currentPartnerHasVoted ? 'contained' : 'outlined'}
                    onClick={() => handleVoteToggle(topic)}
                    aria-label={
                      topic.currentPartnerHasVoted
                        ? t('portal.topics.unvote')
                        : t('portal.topics.vote')
                    }
                    data-testid={`vote-button-${topic.id}`}
                    sx={{ minWidth: 48 }}
                  >
                    {topic.currentPartnerHasVoted ? (
                      <ThumbUpIcon fontSize="small" />
                    ) : (
                      <ThumbUpOutlinedIcon fontSize="small" />
                    )}
                  </Button>
                  <Typography variant="caption" data-testid={`vote-count-${topic.id}`}>
                    {topic.voteCount}
                  </Typography>
                </Box>
              </ListItem>
            );
          })}
        </List>
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
