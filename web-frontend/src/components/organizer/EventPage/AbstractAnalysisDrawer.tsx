import { useState } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Button,
  Chip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  CircularProgress,
  Snackbar,
  Alert,
  Stack,
} from '@mui/material';
import { Close as CloseIcon, ExpandMore as ExpandMoreIcon, AutoAwesome } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useAiAnalyzeAbstract } from '@/hooks/useAiAssist';

interface AbstractAnalysisDrawerProps {
  speakerId: string;
  speakerName: string;
  abstractText: string;
  open: boolean;
  onClose: () => void;
}

function getScoreColor(score: number): 'error' | 'warning' | 'success' {
  if (score <= 4) return 'error';
  if (score <= 7) return 'warning';
  return 'success';
}

export function AbstractAnalysisDrawer({
  speakerId,
  speakerName,
  abstractText,
  open,
  onClose,
}: AbstractAnalysisDrawerProps) {
  const { t } = useTranslation('organizer');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const analysisMutation = useAiAnalyzeAbstract(speakerId);

  const handleAnalyze = () => {
    setErrorMessage(null);
    analysisMutation.mutate(
      { abstract: abstractText, speakerName },
      {
        onError: (err) => {
          setErrorMessage(err.message);
        },
      }
    );
  };

  const handleCopyImproved = () => {
    if (analysisMutation.data?.improvedAbstract) {
      navigator.clipboard.writeText(analysisMutation.data.improvedAbstract).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    }
  };

  return (
    <>
      <Drawer anchor="right" open={open} onClose={onClose} PaperProps={{ sx: { width: 480 } }}>
        <Box sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="h6">
            <AutoAwesome sx={{ mr: 1, verticalAlign: 'middle' }} />
            {t('aiAssist.analyzeAbstract', 'Abstract analysieren')}
          </Typography>
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        </Box>
        <Divider />

        <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Typography variant="body2" color="text.secondary">
            {speakerName}
          </Typography>

          <Button
            variant="contained"
            startIcon={
              analysisMutation.isPending ? (
                <CircularProgress size={16} color="inherit" />
              ) : (
                <AutoAwesome />
              )
            }
            disabled={analysisMutation.isPending || !abstractText}
            onClick={handleAnalyze}
            fullWidth
          >
            {analysisMutation.isPending
              ? t('aiAssist.generating', 'Generieren...')
              : t('aiAssist.analyzeAbstract', 'Abstract analysieren')}
          </Button>

          {analysisMutation.isSuccess && analysisMutation.data && (
            <Stack spacing={2}>
              {/* Quality Score */}
              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('aiAssist.qualityScore', 'Qualitätsscore')}
                </Typography>
                <Chip
                  label={`${analysisMutation.data.qualityScore}/10`}
                  color={getScoreColor(analysisMutation.data.qualityScore)}
                  size="medium"
                />
              </Box>

              {/* Suggestion */}
              {analysisMutation.data.suggestion && (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    {t('aiAssist.suggestion', 'Verbesserungsvorschlag')}
                  </Typography>
                  <Typography variant="body2">{analysisMutation.data.suggestion}</Typography>
                </Box>
              )}

              {/* Key Themes */}
              {analysisMutation.data.keyThemes && analysisMutation.data.keyThemes.length > 0 && (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    {t('aiAssist.keyThemes', 'Kernthemen')}
                  </Typography>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {analysisMutation.data.keyThemes.map((theme) => (
                      <Chip key={theme} label={theme} size="small" variant="outlined" />
                    ))}
                  </Box>
                </Box>
              )}

              {/* Improved version accordion */}
              {analysisMutation.data.improvedAbstract && (
                <Accordion>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Typography variant="subtitle2">
                      {t('aiAssist.improvedVersion', 'Verbesserte Version')}
                    </Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', mb: 1 }}>
                      {analysisMutation.data.improvedAbstract}
                    </Typography>
                    <Button size="small" variant="outlined" onClick={handleCopyImproved}>
                      {copied
                        ? '✓ Kopiert'
                        : t('aiAssist.copyImproved', 'Verbesserte Version kopieren')}
                    </Button>
                  </AccordionDetails>
                </Accordion>
              )}
            </Stack>
          )}
        </Box>
      </Drawer>

      <Snackbar
        open={!!errorMessage}
        autoHideDuration={6000}
        onClose={() => setErrorMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="error" onClose={() => setErrorMessage(null)}>
          {errorMessage ?? t('aiAssist.error', 'AI generation failed, please write manually')}
        </Alert>
      </Snackbar>
    </>
  );
}
