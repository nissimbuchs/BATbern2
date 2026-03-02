import { useState } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Button,
  CircularProgress,
  Snackbar,
  Alert,
} from '@mui/material';
import { Close as CloseIcon, AutoAwesome } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useAiGenerateDescription, useAiGenerateThemeImage } from '@/hooks/useAiAssist';

interface AiAssistDrawerProps {
  eventCode: string;
  topicTitle: string;
  topicCategory: string;
  open: boolean;
  onClose: () => void;
  onDescriptionGenerated: (text: string) => void;
  onImageGenerated: (imageUrl: string, s3Key: string) => void;
}

export function AiAssistDrawer({
  eventCode,
  topicTitle,
  topicCategory,
  open,
  onClose,
  onDescriptionGenerated,
  onImageGenerated,
}: AiAssistDrawerProps) {
  const { t } = useTranslation('organizer');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [generatedImageUrl, setGeneratedImageUrl] = useState<string | null>(null);
  const [generatedImageS3Key, setGeneratedImageS3Key] = useState<string | null>(null);

  const descriptionMutation = useAiGenerateDescription(eventCode);
  const imageMutation = useAiGenerateThemeImage(eventCode);

  const handleGenerateDescription = () => {
    setErrorMessage(null);
    descriptionMutation.mutate(
      { topicTitle, topicCategory },
      {
        onSuccess: (data) => {
          onDescriptionGenerated(data.description);
        },
        onError: (err) => {
          setErrorMessage(err.message);
        },
      }
    );
  };

  const handleGenerateImage = () => {
    setErrorMessage(null);
    setGeneratedImageUrl(null);
    setGeneratedImageS3Key(null);
    imageMutation.mutate(
      { topicTitle, topicCategory },
      {
        onSuccess: (data) => {
          setGeneratedImageUrl(data.imageUrl);
          setGeneratedImageS3Key(data.s3Key);
        },
        onError: (err) => {
          setErrorMessage(err.message);
        },
      }
    );
  };

  const handleUseImage = () => {
    if (generatedImageUrl && generatedImageS3Key) {
      onImageGenerated(generatedImageUrl, generatedImageS3Key);
      setGeneratedImageUrl(null);
      setGeneratedImageS3Key(null);
    }
  };

  return (
    <>
      <Drawer anchor="right" open={open} onClose={onClose} PaperProps={{ sx: { width: 480 } }}>
        <Box sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="h6">
            <AutoAwesome sx={{ mr: 1, verticalAlign: 'middle' }} />
            {t('aiAssist.generateDescription', '✨ Generate with AI')}
          </Typography>
          <IconButton onClick={onClose} size="small">
            <CloseIcon />
          </IconButton>
        </Box>
        <Divider />

        <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
          {/* Description generation */}
          <Box>
            <Typography variant="subtitle1" gutterBottom>
              {t('aiAssist.generateDescription', '✨ Beschreibung generieren')}
            </Typography>
            <Button
              variant="contained"
              startIcon={
                descriptionMutation.isPending ? (
                  <CircularProgress size={16} color="inherit" />
                ) : (
                  <AutoAwesome />
                )
              }
              disabled={descriptionMutation.isPending || !topicTitle}
              onClick={handleGenerateDescription}
              fullWidth
            >
              {descriptionMutation.isPending
                ? t('aiAssist.generating', 'Generieren...')
                : t('aiAssist.generateDescription', '✨ Beschreibung generieren')}
            </Button>
            {descriptionMutation.isSuccess && (
              <Alert severity="success" sx={{ mt: 1 }}>
                {t('aiAssist.generateDescription', 'Beschreibung generiert und eingefügt!')}
              </Alert>
            )}
          </Box>

          <Divider />

          {/* Image generation */}
          <Box>
            <Typography variant="subtitle1" gutterBottom>
              {t('aiAssist.generateImage', '✨ Titelbild generieren')}
            </Typography>
            <Button
              variant="outlined"
              startIcon={
                imageMutation.isPending ? (
                  <CircularProgress size={16} color="inherit" />
                ) : (
                  <AutoAwesome />
                )
              }
              disabled={imageMutation.isPending || !topicTitle}
              onClick={handleGenerateImage}
              fullWidth
            >
              {imageMutation.isPending
                ? t('aiAssist.generating', 'Generieren...')
                : t('aiAssist.generateImage', '✨ Titelbild generieren')}
            </Button>

            {generatedImageUrl && (
              <Box sx={{ mt: 1 }}>
                <Box
                  component="img"
                  src={generatedImageUrl}
                  alt="Generated theme"
                  sx={{ width: '100%', borderRadius: 1, mb: 1 }}
                />
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button variant="contained" size="small" onClick={handleUseImage} fullWidth>
                    {t('aiAssist.useImage', 'Bild verwenden')}
                  </Button>
                  <Button variant="outlined" size="small" onClick={handleGenerateImage} fullWidth>
                    {t('aiAssist.regenerateImage', '↻ Neu generieren')}
                  </Button>
                  <Button
                    variant="text"
                    size="small"
                    onClick={() => {
                      setGeneratedImageUrl(null);
                      setGeneratedImageS3Key(null);
                    }}
                  >
                    {t('aiAssist.cancelImage', 'Abbrechen')}
                  </Button>
                </Box>
              </Box>
            )}
          </Box>
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
