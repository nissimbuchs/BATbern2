/**
 * TopicsList Component (Task 12b - GREEN Phase)
 *
 * Story 2.5.3 - AC7: Topic Management
 * Wireframe: docs/wireframes/story-1.16-event-detail-edit.md v1.1 (lines 68-81)
 *
 * Displays assigned topics with:
 * - Topic cards showing title, last used info, partner votes
 * - [View] button to show topic details modal
 * - [Remove] button with confirmation dialog
 * - [+ Add Topic from Backlog] button
 * - Empty state, loading state, error state
 * - Full i18n support (German/English)
 * - WCAG 2.1 AA accessibility
 */

import React, { useState } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  CardActions,
  Button,
  Skeleton,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { Add as AddIcon, Visibility as ViewIcon, Delete as DeleteIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { Topic } from '@/types/event.types';

export interface TopicsListProps {
  topics?: Topic[];
  eventCode: string;
  onRemoveTopic: (eventCode: string, topicId: string) => void;
  onViewTopic: (topicId: string) => void;
  isLoading?: boolean;
  error?: Error | null;
}

export const TopicsList: React.FC<TopicsListProps> = ({
  topics,
  eventCode,
  onRemoveTopic,
  onViewTopic,
  isLoading = false,
  error = null,
}) => {
  const { t } = useTranslation('events');
  const navigate = useNavigate();
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);
  const [topicToRemove, setTopicToRemove] = useState<string | null>(null);

  const handleRemoveClick = (topicId: string) => {
    setTopicToRemove(topicId);
    setConfirmDialogOpen(true);
  };

  const handleConfirmRemove = () => {
    if (topicToRemove) {
      onRemoveTopic(eventCode, topicToRemove);
      setConfirmDialogOpen(false);
      setTopicToRemove(null);
    }
  };

  const handleCancelRemove = () => {
    setConfirmDialogOpen(false);
    setTopicToRemove(null);
  };

  const handleAddTopic = () => {
    console.log('[TopicsList] handleAddTopic clicked', { eventCode });
    navigate(`/organizer/topics?eventCode=${eventCode}`);
  };

  const formatLastUsed = (topic: Topic): string => {
    if (!topic.lastUsedEvent || !topic.lastUsedDate) {
      return t('topics.neverUsed', 'Never');
    }
    const year = new Date(topic.lastUsedDate).getFullYear();
    return t('topics.lastUsed', {
      event: topic.lastUsedEvent,
      year,
      defaultValue: `${topic.lastUsedEvent} (${year})`,
    });
  };

  // Loading state
  if (isLoading) {
    return (
      <Box data-testid="topics-loading" sx={{ mt: 3 }}>
        <Skeleton variant="text" width={200} height={40} />
        <Grid container spacing={2} sx={{ mt: 1 }}>
          {[1, 2, 3].map((i) => (
            <Grid size={{ xs: 12, md: 6 }} key={i}>
              <Skeleton variant="rectangular" height={150} />
            </Grid>
          ))}
        </Grid>
      </Box>
    );
  }

  // Error state
  if (error) {
    return (
      <Box sx={{ mt: 3 }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          {error.message || t('topics.loadError', 'Failed to load topics')}
          <Button size="small" sx={{ ml: 2 }} onClick={() => window.location.reload()}>
            {t('common.retry', 'Retry')}
          </Button>
        </Alert>
      </Box>
    );
  }

  const topicCount = topics?.length || 0;

  return (
    <Box sx={{ mt: 3 }}>
      {/* Section Title with Count */}
      <Typography
        variant="h6"
        sx={{ mb: 2, fontWeight: 600, textTransform: 'uppercase' }}
        aria-label={t('topics.sectionLabel', {
          count: topicCount,
          defaultValue: `Assigned Topics (${topicCount})`,
        })}
      >
        {t('topics.sectionTitle', {
          count: topicCount,
          defaultValue: `ASSIGNED TOPICS (${topicCount})`,
        })}
      </Typography>

      {/* Empty State */}
      {topicCount === 0 && (
        <Box
          sx={{
            textAlign: 'center',
            py: 4,
            border: '1px dashed',
            borderColor: 'divider',
            borderRadius: 1,
            mb: 2,
          }}
        >
          <Typography variant="body1" color="text.secondary">
            {t('topics.emptyState', 'No topics assigned yet')}
          </Typography>
        </Box>
      )}

      {/* Topic Cards Grid */}
      {topicCount > 0 && (
        <Grid container spacing={2} sx={{ mb: 2 }}>
          {topics?.map((topic) => (
            <Grid size={{ xs: 12, md: 6 }} key={topic.id}>
              <Card
                sx={{
                  height: '100%',
                  display: 'flex',
                  flexDirection: 'column',
                  '&:hover': {
                    boxShadow: 3,
                  },
                }}
              >
                <CardContent sx={{ flexGrow: 1 }}>
                  {/* Topic Title */}
                  <Typography variant="h6" gutterBottom sx={{ fontWeight: 500 }}>
                    {topic.title}
                  </Typography>

                  {/* Last Used Info */}
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                    {t('topics.lastUsedLabel', 'Last used')}:{' '}
                    <strong>{formatLastUsed(topic)}</strong>
                  </Typography>

                  {/* Partner Votes */}
                  <Typography variant="body2" color="text.secondary">
                    {t('topics.partnerVotes', 'Partner votes')}:{' '}
                    <strong>{topic.partnerVotes}</strong>
                  </Typography>
                </CardContent>

                {/* Actions */}
                <CardActions sx={{ justifyContent: 'flex-end', px: 2, pb: 2 }}>
                  <Button
                    size="small"
                    startIcon={<ViewIcon />}
                    onClick={() => onViewTopic(topic.id)}
                    aria-label={t('topics.viewButton', {
                      title: topic.title,
                      defaultValue: `View ${topic.title}`,
                    })}
                  >
                    {t('topics.view', 'View')}
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    startIcon={<DeleteIcon />}
                    onClick={() => handleRemoveClick(topic.id)}
                    aria-label={t('topics.removeButton', {
                      title: topic.title,
                      defaultValue: `Remove ${topic.title}`,
                    })}
                  >
                    {t('topics.remove', 'Remove')}
                  </Button>
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {/* Add Topic Button */}
      <Button
        variant="outlined"
        startIcon={<AddIcon />}
        onClick={handleAddTopic}
        fullWidth
        sx={{ mt: 2 }}
        aria-label={t('topics.addFromBacklog', 'Add Topic from Backlog')}
      >
        {t('topics.addFromBacklog', 'Add Topic from Backlog')}
      </Button>

      {/* Remove Confirmation Dialog */}
      <Dialog
        open={confirmDialogOpen}
        onClose={handleCancelRemove}
        aria-labelledby="remove-topic-dialog-title"
        aria-describedby="remove-topic-dialog-description"
      >
        <DialogTitle id="remove-topic-dialog-title">
          {t('topics.removeConfirmTitle', 'Remove Topic?')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="remove-topic-dialog-description">
            {t(
              'topics.removeConfirmMessage',
              'Are you sure you want to remove this topic from the event?'
            )}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelRemove} color="inherit">
            {t('common:actions.cancel')}
          </Button>
          <Button onClick={handleConfirmRemove} color="error" autoFocus>
            {t('common.confirm', 'Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
