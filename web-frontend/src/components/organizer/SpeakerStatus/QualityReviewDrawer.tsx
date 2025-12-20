/**
 * Quality Review Drawer Component (Story 5.5 Phase 4)
 *
 * Drawer for reviewing speaker presentation content
 * Features:
 * - Slide-in drawer from right side (600px)
 * - Speaker and presentation information display
 * - Quality criteria checklist:
 *   - Abstract length ≤ 1000 characters
 *   - "Lessons learned" detected
 *   - No product promotion
 *   - Professional tone
 * - Approve/Reject actions
 * - Reject requires feedback
 */

import React, { useState, useEffect } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  TextField,
  Button,
  Paper,
  Chip,
  Alert,
  CircularProgress,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import {
  Close as CloseIcon,
  CheckCircle as CheckCircleIcon,
  Warning as WarningIcon,
  ThumbUp as ThumbUpIcon,
  ThumbDown as ThumbDownIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { speakerContentService } from '@/services/speakerContentService';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { ReviewRequest, SpeakerContentResponse } from '@/services/speakerContentService';

interface QualityReviewDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
}

const MAX_ABSTRACT_LENGTH = 1000;

export const QualityReviewDrawer: React.FC<QualityReviewDrawerProps> = ({
  open,
  onClose,
  speaker,
  eventCode,
}) => {
  const { t } = useTranslation('organizer');
  const queryClient = useQueryClient();

  const [rejecting, setRejecting] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [feedbackError, setFeedbackError] = useState('');

  // Fetch speaker content when drawer opens
  const { data: content, isLoading } = useQuery<SpeakerContentResponse>({
    queryKey: ['speakerContent', eventCode, speaker?.id],
    queryFn: () => speakerContentService.getSpeakerContent(eventCode, speaker?.id || ''),
    enabled: open && !!speaker,
  });

  // Reset state when drawer opens/closes
  useEffect(() => {
    if (!open) {
      setRejecting(false);
      setFeedback('');
      setFeedbackError('');
    }
  }, [open]);

  // Mutation for reviewing content
  const reviewMutation = useMutation({
    mutationFn: (request: ReviewRequest) =>
      speakerContentService.reviewContent(eventCode, speaker?.id || '', request),
    onSuccess: () => {
      // Invalidate queries to refetch data
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['speakerPool', eventCode] });
      queryClient.invalidateQueries({ queryKey: ['reviewQueue', eventCode] });

      // Close drawer
      onClose();
    },
  });

  const handleApprove = () => {
    reviewMutation.mutate({ action: 'APPROVE' });
  };

  const handleRejectClick = () => {
    setRejecting(true);
  };

  const handleCancelReject = () => {
    setRejecting(false);
    setFeedback('');
    setFeedbackError('');
  };

  const handleSubmitReject = () => {
    if (!feedback.trim()) {
      setFeedbackError(t('qualityReview.errors.feedbackRequired'));
      return;
    }

    reviewMutation.mutate({
      action: 'REJECT',
      feedback: feedback.trim(),
    });
  };

  // Quality criteria checks
  const qualityCriteria = content
    ? [
        {
          label: t('qualityReview.criteria.abstractLength'),
          passed: content.presentationAbstract.length <= MAX_ABSTRACT_LENGTH,
          value: `${content.presentationAbstract.length} / ${MAX_ABSTRACT_LENGTH} ${t('qualityReview.characters')}`,
        },
        {
          label: t('qualityReview.criteria.lessonsLearned'),
          passed: /lessons?\s+learned/i.test(content.presentationAbstract),
          value: /lessons?\s+learned/i.test(content.presentationAbstract)
            ? t('qualityReview.detected')
            : t('qualityReview.notDetected'),
        },
        {
          label: t('qualityReview.criteria.noPromotion'),
          passed: !/buy|purchase|discount|sale|order now|contact us/i.test(
            content.presentationAbstract
          ),
          value: !/buy|purchase|discount|sale|order now|contact us/i.test(
            content.presentationAbstract
          )
            ? t('qualityReview.passed')
            : t('qualityReview.promotionDetected'),
        },
      ]
    : [];

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: {
          width: { xs: '100%', sm: 600 },
          p: 3,
        },
      }}
    >
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5">{t('qualityReview.title')}</Typography>
        <IconButton onClick={onClose} edge="end">
          <CloseIcon />
        </IconButton>
      </Box>

      <Divider sx={{ mb: 3 }} />

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      ) : content ? (
        <>
          {/* Speaker Information */}
          <Paper sx={{ p: 2, mb: 3, bgcolor: 'background.default' }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('qualityReview.speaker')}
            </Typography>
            <Typography variant="h6">{speaker?.speakerName}</Typography>
            <Typography variant="body2" color="text.secondary">
              {speaker?.company}
            </Typography>
            <Chip
              label={speaker?.status?.replace(/_/g, ' ')}
              size="small"
              color="primary"
              sx={{ mt: 1 }}
            />
          </Paper>

          {/* Presentation Information */}
          <Paper sx={{ p: 2, mb: 3 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              {t('qualityReview.presentationTitle')}
            </Typography>
            <Typography variant="h6" gutterBottom>
              {content.presentationTitle}
            </Typography>

            <Typography variant="subtitle2" color="text.secondary" gutterBottom sx={{ mt: 2 }}>
              {t('qualityReview.abstract')}
            </Typography>
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
              {content.presentationAbstract}
            </Typography>
          </Paper>

          {/* Quality Criteria */}
          <Paper sx={{ p: 2, mb: 3 }}>
            <Typography variant="subtitle2" gutterBottom>
              {t('qualityReview.qualityCriteria')}
            </Typography>
            <List dense>
              {qualityCriteria.map((criterion, index) => (
                <ListItem key={index} disablePadding sx={{ py: 0.5 }}>
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    {criterion.passed ? (
                      <CheckCircleIcon color="success" fontSize="small" />
                    ) : (
                      <WarningIcon color="warning" fontSize="small" />
                    )}
                  </ListItemIcon>
                  <ListItemText
                    primary={criterion.label}
                    secondary={criterion.value}
                    primaryTypographyProps={{ variant: 'body2' }}
                    secondaryTypographyProps={{ variant: 'caption' }}
                  />
                </ListItem>
              ))}
            </List>
          </Paper>

          {/* Error Message */}
          {reviewMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {reviewMutation.error instanceof Error
                ? reviewMutation.error.message
                : t('qualityReview.errors.unknown')}
            </Alert>
          )}

          {/* Reject Feedback Form */}
          {rejecting ? (
            <Box sx={{ mb: 2 }}>
              <Alert severity="info" sx={{ mb: 2 }}>
                {t('qualityReview.rejectInstructions')}
              </Alert>
              <TextField
                fullWidth
                multiline
                rows={4}
                label={t('qualityReview.feedbackLabel')}
                value={feedback}
                onChange={(e) => {
                  setFeedback(e.target.value);
                  setFeedbackError('');
                }}
                error={!!feedbackError}
                helperText={feedbackError}
                placeholder={t('qualityReview.feedbackPlaceholder')}
                sx={{ mb: 2 }}
              />
              <Box sx={{ display: 'flex', gap: 2 }}>
                <Button
                  variant="contained"
                  color="error"
                  onClick={handleSubmitReject}
                  disabled={reviewMutation.isPending}
                  startIcon={
                    reviewMutation.isPending ? <CircularProgress size={20} /> : <ThumbDownIcon />
                  }
                  fullWidth
                >
                  {reviewMutation.isPending
                    ? t('qualityReview.submitting')
                    : t('qualityReview.confirmReject')}
                </Button>
                <Button
                  variant="outlined"
                  onClick={handleCancelReject}
                  disabled={reviewMutation.isPending}
                  fullWidth
                >
                  {t('common.cancel')}
                </Button>
              </Box>
            </Box>
          ) : (
            /* Action Buttons */
            <Box sx={{ display: 'flex', gap: 2 }}>
              <Button
                variant="contained"
                color="success"
                onClick={handleApprove}
                disabled={reviewMutation.isPending}
                startIcon={
                  reviewMutation.isPending ? <CircularProgress size={20} /> : <ThumbUpIcon />
                }
                fullWidth
              >
                {reviewMutation.isPending
                  ? t('qualityReview.approving')
                  : t('qualityReview.approve')}
              </Button>
              <Button
                variant="outlined"
                color="error"
                onClick={handleRejectClick}
                disabled={reviewMutation.isPending}
                startIcon={<ThumbDownIcon />}
                fullWidth
              >
                {t('qualityReview.reject')}
              </Button>
            </Box>
          )}
        </>
      ) : (
        <Alert severity="warning">{t('qualityReview.noContent')}</Alert>
      )}
    </Drawer>
  );
};
