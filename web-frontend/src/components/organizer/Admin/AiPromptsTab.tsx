/**
 * AiPromptsTab
 *
 * Admin tab for editing the three organizer-editable OpenAI prompts:
 *   - event_description  — GPT-4o event description generation
 *   - theme_image        — DALL-E theme image generation
 *   - abstract_quality   — GPT-4o abstract quality review
 *
 * Each card shows the current prompt text in a resizable textarea with
 * Save and Reset to Default buttons.
 */

import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Snackbar,
  Typography,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAiPrompts, useUpdateAiPrompt, useResetAiPrompt } from '@/hooks/useAiPrompts';
import type { AiPromptResponse } from '@/services/aiPromptService';

/** Variables documented per prompt key — shown as a hint below the textarea. */
const PROMPT_VARIABLES: Record<string, string> = {
  event_description:
    '{{EVENT_NR}}, {{EVENT_TITLE}}, {{TOPIC_TITLE}}, {{TOPIC_DESCRIPTION}}, {{TOPIC_CATEGORY}}, {{EVENT_DATE}}, {{EVENT_DESCRIPTION}}',
  theme_image:
    '{{TOPIC_TITLE}}, {{TOPIC_DESCRIPTION}}, {{TOPIC_CATEGORY}}, {{EVENT_TITLE}}, {{EVENT_DESCRIPTION}}',
  abstract_quality: '{{SPEAKER_NAME}}, {{SESSION_TITLE}}, {{ABSTRACT}}',
};

interface PromptCardProps {
  prompt: AiPromptResponse;
}

const PromptCard: React.FC<PromptCardProps> = ({ prompt }) => {
  const { t } = useTranslation('common');
  const [text, setText] = useState(prompt.promptText);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; error: boolean; message: string }>({
    open: false,
    error: false,
    message: '',
  });

  const updateMutation = useUpdateAiPrompt();
  const resetMutation = useResetAiPrompt();

  // Sync when server data refreshes
  useEffect(() => {
    setText(prompt.promptText);
  }, [prompt.promptText]);

  const isDirty = text !== prompt.promptText;
  const isBusy = updateMutation.isPending || resetMutation.isPending;

  const handleSave = () => {
    updateMutation.mutate(
      { promptKey: prompt.promptKey, promptText: text },
      {
        onSuccess: () =>
          setSnackbar({ open: true, error: false, message: t('admin.aiPrompts.saved') }),
        onError: () =>
          setSnackbar({ open: true, error: true, message: t('admin.aiPrompts.saveFailed') }),
      }
    );
  };

  const handleReset = () => {
    resetMutation.mutate(prompt.promptKey, {
      onSuccess: (updated) => {
        setText(updated.promptText);
        setSnackbar({ open: true, error: false, message: t('admin.aiPrompts.resetDone') });
      },
      onError: () =>
        setSnackbar({ open: true, error: true, message: t('admin.aiPrompts.resetFailed') }),
    });
    setResetDialogOpen(false);
  };

  const variables = PROMPT_VARIABLES[prompt.promptKey];

  return (
    <>
      <Card variant="outlined" sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {prompt.displayName}
          </Typography>

          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            disabled={isBusy}
            rows={12}
            style={{
              width: '100%',
              fontFamily: 'monospace',
              fontSize: 13,
              padding: '8px',
              boxSizing: 'border-box',
              resize: 'vertical',
              border: '1px solid #ccc',
              borderRadius: 4,
              backgroundColor: isBusy ? '#f5f5f5' : '#fff',
            }}
          />

          {variables && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
              {t('admin.aiPrompts.variablesLabel')} {variables}
            </Typography>
          )}

          <Box sx={{ display: 'flex', gap: 1, mt: 2, justifyContent: 'flex-end' }}>
            <Button
              variant="outlined"
              color="warning"
              size="small"
              disabled={isBusy}
              onClick={() => setResetDialogOpen(true)}
            >
              {t('admin.aiPrompts.resetToDefault')}
            </Button>
            <Button
              variant="contained"
              size="small"
              disabled={!isDirty || isBusy}
              onClick={handleSave}
            >
              {updateMutation.isPending ? (
                <CircularProgress size={16} />
              ) : (
                t('admin.aiPrompts.save')
              )}
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* Reset confirmation dialog */}
      <Dialog open={resetDialogOpen} onClose={() => setResetDialogOpen(false)}>
        <DialogTitle>{t('admin.aiPrompts.resetDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('admin.aiPrompts.resetDialogText')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResetDialogOpen(false)}>
            {t('admin.aiPrompts.resetDialogCancel')}
          </Button>
          <Button onClick={handleReset} color="warning" variant="contained">
            {t('admin.aiPrompts.resetDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
      >
        <Alert severity={snackbar.error ? 'error' : 'success'} sx={{ width: '100%' }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
};

export const AiPromptsTab: React.FC = () => {
  const { t } = useTranslation('common');
  const { data: prompts, isLoading, error } = useAiPrompts();

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error || !prompts) {
    return <Alert severity="error">{t('admin.aiPrompts.loadFailed')}</Alert>;
  }

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        {t('admin.aiPrompts.description')}
      </Typography>
      {prompts.map((prompt) => (
        <PromptCard key={prompt.promptKey} prompt={prompt} />
      ))}
    </Box>
  );
};
