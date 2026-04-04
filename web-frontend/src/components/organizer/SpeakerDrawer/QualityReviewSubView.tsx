import React, { useState, useEffect } from 'react';
import {
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
  ArrowBack as ArrowBackIcon,
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

interface QualityReviewSubViewProps {
  speaker: SpeakerPoolEntry;
  eventCode: string;
  onBack: () => void;
  onClose: () => void;
}

function getScoreColor(score: number): 'error' | 'warning' | 'success' {
  if (score <= 4) return 'error';
  if (score <= 7) return 'warning';
  return 'success';
}

export const QualityReviewSubView: React.FC<QualityReviewSubViewProps> = ({
  speaker,
  eventCode,
  onBack,
  onClose,
}) => {
  const { t } = useTranslation('organizer');
  const queryClient = useQueryClient();

  const [rejecting, setRejecting] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [feedbackError, setFeedbackError] = useState('');
  const [copied, setCopied] = useState(false);
  const [aiErrorMessage, setAiErrorMessage] = useState<string | null>(null);

  const { aiContentEnabled } = useFeatureFlags();
  const analysisMutation = useAiAnalyzeAbstract(speaker.id);

  const { data: content, isLoading } = useQuery<SpeakerContentResponse>({
    queryKey: ['speakerContent', eventCode, speaker.id],
    queryFn: () => speakerContentService.getSpeakerContent(eventCode, speaker.id),
    enabled: !!speaker,
  });

  useEffect(() => {
    setRejecting(false);
    setFeedback('');
    setFeedbackError('');
    setCopied(false);
    setAiErrorMessage(null);
    analysisMutation.reset();
  }, [speaker.id]);

  const reviewMutation = useMutation({
    mutationFn: (request: ReviewRequest) =>
      speakerContentService.reviewContent(eventCode, speaker.id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['speakerStatusSummary', eventCode] });
      queryClient.invalidateQueries({ queryKey: speakerPoolKeys.list(eventCode) });
      queryClient.invalidateQueries({ queryKey: ['reviewQueue', eventCode] });
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
    reviewMutation.mutate({ action: 'REJECT', feedback: feedback.trim() });
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
      {/* Back Button */}
      <Box sx={{ p: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <IconButton onClick={onBack} size="small">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h6">{t('qualityReview.title')}</Typography>
      </Box>

      <Divider />

      <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <BATbernLoader size={96} />
          </Box>
        ) : content ? (
          <Stack spacing={2}>
            {/* Speaker Information */}
            <Paper sx={{ p: 2, bgcolor: 'background.default' }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                {t('common:role.speaker')}
              </Typography>
              <Typography variant="h6">{speaker.speakerName}</Typography>
              <Typography variant="body2" color="text.secondary">
                {speaker.company}
              </Typography>
              <Chip
                label={speaker.status?.replace(/_/g, ' ')}
                size="small"
                color="primary"
                sx={{ mt: 1 }}
              />
            </Paper>

            {/* Presentation Information */}
            <Paper sx={{ p: 2 }}>
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

            {/* Material */}
            <Paper sx={{ p: 2 }}>
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
            <Paper sx={{ p: 2 }}>
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
              <Alert severity="error">
                {reviewMutation.error instanceof Error
                  ? reviewMutation.error.message
                  : t('qualityReview.errors.unknown')}
              </Alert>
            )}

            {/* Reject Feedback Form */}
            {rejecting ? (
              <Box>
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
          </Stack>
        ) : (
          <Alert severity="warning">{t('qualityReview.noContent')}</Alert>
        )}
      </Box>

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
