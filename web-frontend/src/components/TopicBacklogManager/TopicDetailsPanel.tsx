/**
 * Topic Details Panel Component (Story 5.2 - AC5, AC6, AC7)
 *
 * Displays topic details with:
 * - Staleness score and color coding
 * - Similar topics warning (>70% similarity)
 * - Performance metrics
 * - Topic selection action
 * - Override staleness capability
 */

import React, { useState } from 'react';
import {
  Paper,
  Typography,
  Box,
  Chip,
  Button,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  TextField,
  List,
  ListItem,
  ListItemText,
} from '@mui/material';
import { Warning as WarningIcon, CheckCircle as CheckIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useSimilarTopics, useSelectTopicForEvent, useTopicUsageHistory } from '@/hooks/useTopics';
import { TopicHeatMap } from '@/components/TopicHeatMap';
import type { Topic } from '@/types/topic.types';

export interface TopicDetailsPanelProps {
  topic: Topic;
  eventCode?: string;
  onTopicConfirm?: (topicId: string) => void;
}

export const TopicDetailsPanel: React.FC<TopicDetailsPanelProps> = ({
  topic,
  eventCode,
  onTopicConfirm,
}) => {
  const { t } = useTranslation('organizer');
  const [showOverrideDialog, setShowOverrideDialog] = useState(false);
  const [showSimilarDialog, setShowSimilarDialog] = useState(false);
  const [justification, setJustification] = useState('');

  // Fetch similar topics for duplicate detection (AC5)
  const { data: similarTopics } = useSimilarTopics(topic.id);

  // Fetch usage history for heat map visualization (AC2)
  const { data: usageHistory } = useTopicUsageHistory(topic.id);

  // Mutation for selecting topic
  const selectTopicMutation = useSelectTopicForEvent();

  /**
   * Get status message based on staleness score (AC6)
   */
  const getStatusMessage = (score: number) => {
    if (score >= 83) {
      return {
        severity: 'success' as const,
        message: t(
          'topicBacklog.details.statusGreen',
          'Safe to reuse - last used over 12 months ago'
        ),
      };
    } else if (score >= 50) {
      return {
        severity: 'warning' as const,
        message: t(
          'topicBacklog.details.statusYellow',
          'Use with caution - last used 6-12 months ago'
        ),
      };
    } else {
      return {
        severity: 'error' as const,
        message: t(
          'topicBacklog.details.statusRed',
          'Too recent - last used within 6 months, high duplication risk'
        ),
      };
    }
  };

  const status = getStatusMessage(topic.stalenessScore);

  // Check if there are high similarity warnings (>70%) - AC5
  const highSimilarityTopics =
    similarTopics?.filter((st) => {
      const similarity = topic.similarityScores.find((s) => s.topicId === st.id);
      return similarity && similarity.score > 0.7;
    }) || [];

  const handleSelectTopic = () => {
    if (highSimilarityTopics.length > 0) {
      // Show similarity warning dialog (AC5)
      setShowSimilarDialog(true);
    } else {
      confirmTopicSelection();
    }
  };

  const confirmTopicSelection = () => {
    if (eventCode) {
      selectTopicMutation.mutate({
        eventCode,
        request: {
          topicId: topic.id,
          justification: justification || undefined,
        },
      });
    }
    if (onTopicConfirm) {
      onTopicConfirm(topic.id);
    }
    setShowSimilarDialog(false);
  };

  return (
    <>
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>
          {topic.title}
        </Typography>

        <Box sx={{ mb: 2 }}>
          <Chip label={topic.category} size="small" sx={{ mr: 1 }} />
          <Chip
            label={`${topic.stalenessScore}%`}
            size="small"
            color={
              topic.colorZone === 'red'
                ? 'error'
                : topic.colorZone === 'yellow'
                  ? 'warning'
                  : 'success'
            }
          />
        </Box>

        <Typography variant="body2" color="text.secondary" paragraph>
          {topic.description}
        </Typography>

        {/* Staleness Status Alert (AC6) */}
        <Alert severity={status.severity} icon={<CheckIcon />} sx={{ mb: 2 }}>
          {status.message}
        </Alert>

        {/* Similar Topics Warning (AC5) */}
        {highSimilarityTopics.length > 0 && (
          <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: 2 }}>
            {t(
              'topicBacklog.details.similarityWarning',
              '{{count}} similar topics detected (>70% similarity)',
              { count: highSimilarityTopics.length }
            )}
          </Alert>
        )}

        {/* Topic Metrics */}
        <Box sx={{ mb: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            {t('topicBacklog.details.metrics', 'Metrics')}
          </Typography>
          {topic.lastUsedDate && (
            <Typography variant="body2">
              {t('topicBacklog.details.lastUsed', 'Last Used')}:{' '}
              {new Date(topic.lastUsedDate).toLocaleDateString()}
            </Typography>
          )}
          {topic.usageCount !== undefined && (
            <Typography variant="body2">
              {t('topicBacklog.details.usageCount', 'Usage Count')}: {topic.usageCount}
            </Typography>
          )}
        </Box>

        {/* Usage History Heat Map (AC2) */}
        {usageHistory && usageHistory.length > 0 && (
          <Box sx={{ mb: 2 }}>
            <TopicHeatMap topicId={topic.id} usageHistory={usageHistory} />
          </Box>
        )}

        {/* Action Buttons */}
        {eventCode && (
          <Box sx={{ display: 'flex', gap: 1, flexDirection: 'column' }}>
            <Button
              variant="contained"
              color="primary"
              fullWidth
              onClick={handleSelectTopic}
              disabled={selectTopicMutation.isPending}
            >
              {selectTopicMutation.isPending
                ? t('topicBacklog.details.selecting', 'Selecting...')
                : t('topicBacklog.details.selectButton', 'Select for Event')}
            </Button>

            {topic.stalenessScore < 50 && (
              <Button
                variant="outlined"
                color="warning"
                fullWidth
                onClick={() => setShowOverrideDialog(true)}
              >
                {t('topicBacklog.details.overrideButton', 'Override Warning')}
              </Button>
            )}
          </Box>
        )}
      </Paper>

      {/* Similar Topics Warning Dialog (AC5) */}
      <Dialog
        open={showSimilarDialog}
        onClose={() => setShowSimilarDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {t('topicBacklog.dialogs.similar.title', 'Similar Topics Detected')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t(
              'topicBacklog.dialogs.similar.message',
              'The following topics have high similarity (>70%) with your selection. Are you sure you want to proceed?'
            )}
          </DialogContentText>
          <List dense>
            {highSimilarityTopics.map((st) => {
              const similarity = topic.similarityScores.find((s) => s.topicId === st.id);
              return (
                <ListItem key={st.id}>
                  <ListItemText
                    primary={st.title}
                    secondary={`${t('topicBacklog.dialogs.similar.similarity', 'Similarity')}: ${Math.round((similarity?.score || 0) * 100)}%`}
                  />
                </ListItem>
              );
            })}
          </List>
          <TextField
            fullWidth
            multiline
            rows={3}
            label={t('topicBacklog.dialogs.similar.justification', 'Justification (optional)')}
            value={justification}
            onChange={(e) => setJustification(e.target.value)}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowSimilarDialog(false)}>
            {t('topicBacklog.dialogs.similar.cancel', 'Cancel')}
          </Button>
          <Button onClick={confirmTopicSelection} color="primary" variant="contained">
            {t('topicBacklog.dialogs.similar.confirm', 'Select Anyway')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Override Staleness Dialog (AC7) */}
      <Dialog
        open={showOverrideDialog}
        onClose={() => setShowOverrideDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          {t('topicBacklog.dialogs.override.title', 'Override Staleness Warning')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t(
              'topicBacklog.dialogs.override.message',
              'This topic was used recently (staleness score: {{score}}%). Please provide justification for using it again.',
              { score: topic.stalenessScore }
            )}
          </DialogContentText>
          <TextField
            fullWidth
            required
            multiline
            rows={4}
            label={t('topicBacklog.dialogs.override.justification', 'Justification')}
            value={justification}
            onChange={(e) => setJustification(e.target.value)}
            sx={{ mt: 2 }}
            helperText={t(
              'topicBacklog.dialogs.override.helper',
              'Explain why this topic should be reused despite being recent'
            )}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowOverrideDialog(false)}>
            {t('topicBacklog.dialogs.override.cancel', 'Cancel')}
          </Button>
          <Button
            onClick={confirmTopicSelection}
            color="primary"
            variant="contained"
            disabled={!justification.trim()}
          >
            {t('topicBacklog.dialogs.override.confirm', 'Override and Select')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default TopicDetailsPanel;
