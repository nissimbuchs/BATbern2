/**
 * PartnerTopicsTab
 *
 * Organizer-only tab on the Partner Detail page. Shows topic suggestions that have been
 * submitted on behalf of a specific partner company, and allows the organizer to add new
 * ones during partner meetings when the organizer is sharing their screen.
 *
 * The company name comes from the partner detail context (URL param) — never from the
 * logged-in user — so the organizer can submit topics attributed to the correct partner.
 */

import React, { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  IconButton,
  Paper,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import LightbulbIcon from '@mui/icons-material/Lightbulb';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { format, parseISO } from 'date-fns';
import {
  deleteTopic,
  getTopics,
  suggestTopic,
  updateTopic,
  type TopicDTO,
} from '@/services/api/partnerTopicsApi';
import { TopicSuggestionForm } from '@/components/partner/TopicSuggestionForm';

// ─── Types ─────────────────────────────────────────────────────────────────

interface Props {
  /** Company name from the partner detail context — never from auth. */
  companyName: string;
}

// ─── Status badge colours ──────────────────────────────────────────────────

const STATUS_COLOR: Record<string, 'default' | 'success' | 'error'> = {
  PROPOSED: 'default',
  SELECTED: 'success',
  DECLINED: 'error',
};

// ─── Component ─────────────────────────────────────────────────────────────

export const PartnerTopicsTab: React.FC<Props> = ({ companyName }) => {
  const { t } = useTranslation('partners');
  const queryClient = useQueryClient();

  const [addOpen, setAddOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<TopicDTO | null>(null);
  const [deletingTopicId, setDeletingTopicId] = useState<string | null>(null);

  // ─── Query ───────────────────────────────────────────────────────────────

  const {
    data: allTopics,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['partnerTopics'],
    queryFn: getTopics,
    staleTime: 2 * 60 * 1000,
  });

  /** Show only this company's topics. */
  const topics: TopicDTO[] = useMemo(
    () => (allTopics ?? []).filter((t) => t.suggestedByCompany === companyName),
    [allTopics, companyName]
  );

  // ─── Mutations ───────────────────────────────────────────────────────────

  const suggestMutation = useMutation({
    mutationFn: ({ title, description }: { title: string; description: string }) =>
      suggestTopic({ title, description, companyName }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['partnerTopics'] });
    },
  });

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

  // ─── Handlers ────────────────────────────────────────────────────────────

  const handleAdd = async (title: string, description: string) => {
    await suggestMutation.mutateAsync({ title, description });
    setAddOpen(false);
  };

  const handleEdit = async (title: string, description: string) => {
    if (!editingTopic) return;
    await editMutation.mutateAsync({ id: editingTopic.id, title, description });
    setEditingTopic(null);
  };

  const handleDeleteConfirm = async () => {
    if (!deletingTopicId) return;
    await deleteMutation.mutateAsync(deletingTopicId);
    setDeletingTopicId(null);
  };

  // ─── Render helpers ──────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <Box data-testid="partner-topics-tab-loading">
        <Skeleton variant="rectangular" height={200} />
      </Box>
    );
  }

  if (isError) {
    return <Alert severity="error">{t('portal.topics.error')}</Alert>;
  }

  return (
    <Box data-testid="partner-topics-tab">
      {/* Header row */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h6" component="h2">
          {t('portal.topics.title')}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setAddOpen(true)}
          data-testid="add-topic-button"
        >
          {t('portal.topics.suggest')}
        </Button>
      </Box>

      {/* Empty state */}
      {topics.length === 0 ? (
        <Alert severity="info" icon={<LightbulbIcon />} data-testid="no-topics-message">
          {t('portal.topics.empty')}
        </Alert>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" aria-label={t('portal.topics.organizer.tableAriaLabel')}>
            <TableHead>
              <TableRow>
                <TableCell>{t('portal.topics.col.topic')}</TableCell>
                <TableCell align="center" width={120}>
                  Status
                </TableCell>
                <TableCell align="center" width={80}>
                  {t('portal.topics.organizer.col.votes')}
                </TableCell>
                <TableCell width={110}>{t('common:labels.date')}</TableCell>
                <TableCell align="right" width={100}>
                  {t('common:labels.actions')}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {topics.map((topic) => (
                <TableRow key={topic.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={500}>
                      {topic.title}
                    </Typography>
                    {topic.description && (
                      <Typography variant="caption" color="text.secondary" display="block">
                        {topic.description}
                      </Typography>
                    )}
                    {topic.plannedEvent && (
                      <Typography variant="caption" color="primary" display="block">
                        {t('portal.topics.plannedFor')} {topic.plannedEvent}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={t(`portal.topics.status.${topic.status.toLowerCase()}`)}
                      color={STATUS_COLOR[topic.status] ?? 'default'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Typography variant="body2">{topic.voteCount}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {format(parseISO(topic.createdAt), 'd MMM yyyy')}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title={t('common:actions.edit')}>
                      <IconButton
                        size="small"
                        onClick={() => setEditingTopic(topic)}
                        aria-label={t('portal.topics.organizer.editAriaLabel', {
                          title: topic.title,
                        })}
                        data-testid={`edit-topic-${topic.id}`}
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={t('common:actions.delete')}>
                      <IconButton
                        size="small"
                        onClick={() => setDeletingTopicId(topic.id)}
                        aria-label={t('portal.topics.organizer.deleteAriaLabel', {
                          title: topic.title,
                        })}
                        data-testid={`delete-topic-${topic.id}`}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Add topic dialog */}
      <TopicSuggestionForm open={addOpen} onClose={() => setAddOpen(false)} onSubmit={handleAdd} />

      {/* Edit topic dialog */}
      <TopicSuggestionForm
        open={editingTopic !== null}
        onClose={() => setEditingTopic(null)}
        onSubmit={handleEdit}
        initialTitle={editingTopic?.title ?? ''}
        initialDescription={editingTopic?.description ?? ''}
        editMode
      />

      {/* Delete confirmation dialog */}
      <Dialog
        open={deletingTopicId !== null}
        onClose={() => setDeletingTopicId(null)}
        aria-labelledby="delete-topic-dialog-title"
      >
        <DialogTitle id="delete-topic-dialog-title">{t('common:actions.delete')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {topics.find((t) => t.id === deletingTopicId)?.title}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeletingTopicId(null)}>{t('common.cancel', 'Cancel')}</Button>
          <Button
            onClick={() => void handleDeleteConfirm()}
            color="error"
            data-testid="confirm-delete-button"
          >
            {t('common:actions.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
