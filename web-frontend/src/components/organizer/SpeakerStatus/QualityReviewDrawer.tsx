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
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Stack,
  Snackbar,
} from '@mui/material';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import {
  Close as CloseIcon,
  ThumbUp as ThumbUpIcon,
  ThumbDown as ThumbDownIcon,
  AttachFile as AttachFileIcon,
  AutoAwesome,
  ExpandMore as ExpandMoreIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { speakerContentService } from '@/services/speakerContentService';
import { speakerPoolKeys } from '@/hooks/useSpeakerPool';
import { useAiAnalyzeAbstract } from '@/hooks/useAiAssist';
import { useFeatureFlags } from '@/hooks/useFeatureFlags';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { ReviewRequest, SpeakerContentResponse } from '@/services/speakerContentService';

interface QualityReviewDrawerProps {
  open: boolean;
  onClose: () => void;
  speaker: SpeakerPoolEntry | null;
  eventCode: string;
}

function getScoreColor(score: number): 'error' | 'warning' | 'success' {
  if (score <= 4) return 'error';
  if (score <= 7) return 'warning';
  return 'success';
}

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
  const [copied, setCopied] = useState(false);
  const [aiErrorMessage, setAiErrorMessage] = useState<string | null>(null);

  const { aiContentEnabled } = useFeatureFlags();
  const analysisMutation = useAiAnalyzeAbstract(speaker?.id ?? '');

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
      setCopied(false);
      setAiErrorMessage(null);
      analysisMutation.reset();
    }
  }, [open]);

  // Mutation for reviewing content
  const reviewMutation = useMutation({
    mutationFn: (request: ReviewRequest) =>
      speakerContentService.reviewContent(eventCode, speaker?.id || '', request),
    onSuccess: () => {
      // Invalidate queries to refetch data
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
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

  const handleAnalyze = () => {
    setAiErrorMessage(null);
    analysisMutation.mutate(undefined, {
      onError: (err) => {
        setAiErrorMessage(err.message);
      },
    });
  };

  const handleCopyShortened = () => {
    if (analysisMutation.data?.shortenedAbstract) {
      navigator.clipboard.writeText(analysisMutation.data.shortenedAbstract).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    }
  };

  const abstract = content?.presentationAbstract || '';

  return (
    <>
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
            <BATbernLoader size={96} />
          </Box>
        ) : content ? (
          <>
            {/* Speaker Information */}
            <Paper sx={{ p: 2, mb: 3, bgcolor: 'background.default' }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('common:role.speaker')}
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
                {content.presentationTitle || t('qualityReview.noTitle', 'Untitled')}
              </Typography>

              <Typography variant="subtitle2" color="text.secondary" gutterBottom sx={{ mt: 2 }}>
                {t('qualityReview.abstract')}
              </Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                {content.presentationAbstract ||
                  t('qualityReview.noAbstract', 'No abstract provided')}
              </Typography>
            </Paper>

            {/* Material Upload */}
            <Paper sx={{ p: 2, mb: 3 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('qualityReview.material', 'Presentation Material')}
              </Typography>
              {content.hasMaterial && content.materialUrl ? (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                  <AttachFileIcon color="primary" fontSize="small" />
                  <Button
                    variant="text"
                    href={content.materialUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    sx={{ textTransform: 'none' }}
                  >
                    {content.materialFileName ||
                      t('qualityReview.downloadMaterial', 'Download Material')}
                  </Button>
                </Box>
              ) : (
                <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                  {t('qualityReview.noMaterial', 'No material uploaded yet')}
                </Typography>
              )}
            </Paper>

            {/* Quality Criteria */}
            <Paper sx={{ p: 2, mb: 3 }}>
              <Box
                sx={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  mb: 1,
                }}
              >
                <Typography variant="subtitle2">{t('qualityReview.qualityCriteria')}</Typography>
                {aiContentEnabled && (
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={
                      analysisMutation.isPending ? (
                        <CircularProgress size={14} color="inherit" />
                      ) : (
                        <AutoAwesome fontSize="small" />
                      )
                    }
                    disabled={analysisMutation.isPending || !abstract}
                    onClick={handleAnalyze}
                  >
                    {analysisMutation.isPending
                      ? t('aiAssist.generating', 'Generieren...')
                      : analysisMutation.isSuccess
                        ? t('aiAssist.reanalyze', 'Neu analysieren')
                        : t('aiAssist.analyzeAbstract', 'Abstract analysieren')}
                  </Button>
                )}
              </Box>

              {analysisMutation.isSuccess && analysisMutation.data ? (
                <Stack spacing={2} sx={{ mt: 1 }}>
                  {/* No-promotion score */}
                  <Box>
                    <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
                      <Chip
                        label={`${analysisMutation.data.noPromotionScore}/10`}
                        color={getScoreColor(analysisMutation.data.noPromotionScore)}
                        size="small"
                      />
                      <Typography variant="body2" color="text.secondary">
                        {t('aiAssist.noPromotion', 'Keine Produktwerbung')}
                      </Typography>
                    </Stack>
                    {analysisMutation.data.noPromotionFeedback && (
                      <Typography variant="body2" sx={{ pl: 0.5 }}>
                        {analysisMutation.data.noPromotionFeedback}
                      </Typography>
                    )}
                  </Box>

                  {/* Lessons-learned score */}
                  <Box>
                    <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
                      <Chip
                        label={`${analysisMutation.data.lessonsLearnedScore}/10`}
                        color={getScoreColor(analysisMutation.data.lessonsLearnedScore)}
                        size="small"
                      />
                      <Typography variant="body2" color="text.secondary">
                        {t('aiAssist.lessonsLearned', 'Lessons Learned aus Praxis')}
                      </Typography>
                    </Stack>
                    {analysisMutation.data.lessonsLearnedFeedback && (
                      <Typography variant="body2" sx={{ pl: 0.5 }}>
                        {analysisMutation.data.lessonsLearnedFeedback}
                      </Typography>
                    )}
                  </Box>

                  {/* Word count + shortened abstract */}
                  {analysisMutation.data.shortenedAbstract && (
                    <Accordion disableGutters>
                      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                        <Typography variant="subtitle2">
                          {t('aiAssist.shortenedAbstract', 'Gekürzter Abstract')}{' '}
                          <Typography component="span" variant="caption" color="text.secondary">
                            ({analysisMutation.data.wordCount} {t('aiAssist.words', 'Wörter')} →
                            max. 150)
                          </Typography>
                        </Typography>
                      </AccordionSummary>
                      <AccordionDetails>
                        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', mb: 1 }}>
                          {analysisMutation.data.shortenedAbstract}
                        </Typography>
                        <Button size="small" variant="outlined" onClick={handleCopyShortened}>
                          {copied
                            ? '✓ Kopiert'
                            : t('aiAssist.copyShortened', 'Gekürzten Abstract kopieren')}
                        </Button>
                      </AccordionDetails>
                    </Accordion>
                  )}
                </Stack>
              ) : null}
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
                    {t('common:actions.cancel')}
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
                  data-testid="approve-content-button"
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

      <Snackbar
        open={!!aiErrorMessage}
        autoHideDuration={6000}
        onClose={() => setAiErrorMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="error" onClose={() => setAiErrorMessage(null)}>
          {aiErrorMessage ?? t('aiAssist.error', 'AI generation failed, please write manually')}
        </Alert>
      </Snackbar>
    </>
  );
};
