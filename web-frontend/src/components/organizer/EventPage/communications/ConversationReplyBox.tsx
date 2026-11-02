/**
 * ConversationReplyBox (Story 16.6 AC1–AC3): the answer under a conversation's messages. Text,
 * "Mark as done", the hint that the mail goes out as "{name} via BATbern" and the answer lands here,
 * and one inline confirm before the send, like the composer. Sticky at the bottom so the send button
 * stays visible in the full-screen mobile dialog.
 */
import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  Snackbar,
  TextField,
  Typography,
} from '@mui/material';
import { Send as SendIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useReplyToConversation } from '@/hooks/useMailConversations/useMailConversations';
import { useUserProfile } from '@/hooks/useUserProfile/useUserProfile';

/** Same bound as the API (`MailConversationReply.text`). */
const MAX_LENGTH = 20000;

interface ConversationReplyBoxProps {
  conversationKey: string;
}

function status(error: unknown): number | undefined {
  return (error as { response?: { status?: number } } | null)?.response?.status;
}

export const ConversationReplyBox: React.FC<ConversationReplyBoxProps> = ({ conversationKey }) => {
  const { t } = useTranslation('events');
  const { user } = useAuth();
  const { userProfile } = useUserProfile();
  const reply = useReplyToConversation();
  const [text, setText] = useState('');
  const [markDone, setMarkDone] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [sent, setSent] = useState(false);
  const yesButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (confirming) yesButton.current?.focus();
  }, [confirming]);

  const name =
    [userProfile?.firstName, userProfile?.lastName]
      .filter((part) => part && part.trim())
      .join(' ') ||
    user?.username ||
    '';
  const empty = text.trim().length === 0;

  const send = () => {
    reply.mutate(
      { key: conversationKey, reply: { text, markDone } },
      {
        onSuccess: () => {
          setText('');
          setMarkDone(false);
          setSent(true);
        },
        onSettled: () => setConfirming(false),
      }
    );
  };

  return (
    <Box
      sx={{
        borderTop: 1,
        borderColor: 'divider',
        pt: 2,
        position: 'sticky',
        bottom: 0,
        bgcolor: 'background.paper',
        pb: 1,
        zIndex: 1,
      }}
      data-testid="conversation-reply-box"
    >
      <TextField
        label={t('eventPage.mail.conversations.reply.label')}
        value={text}
        onChange={(e) => setText(e.target.value)}
        multiline
        minRows={3}
        maxRows={12}
        fullWidth
        disabled={reply.isPending}
        slotProps={{ htmlInput: { maxLength: MAX_LENGTH } }}
        data-testid="conversation-reply-text"
      />
      <Typography
        variant="caption"
        color="text.secondary"
        component="div"
        sx={{ mt: 0.5 }}
        data-testid="conversation-reply-hint"
      >
        {t('eventPage.mail.conversations.reply.hint', { name })}
      </Typography>

      {reply.isError && (
        <Alert
          severity="error"
          onClose={() => reply.reset()}
          sx={{ mt: 1 }}
          data-testid="conversation-reply-error"
        >
          {status(reply.error) === 409
            ? t('eventPage.mail.conversations.reply.noAddress')
            : t('eventPage.mail.conversations.reply.error')}
        </Alert>
      )}

      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'flex-end',
          gap: 1,
          mt: 1,
        }}
      >
        <FormControlLabel
          sx={{ mr: 'auto' }}
          control={
            <Checkbox
              checked={markDone}
              onChange={(e) => setMarkDone(e.target.checked)}
              disabled={reply.isPending}
              data-testid="conversation-reply-done"
            />
          }
          label={t('eventPage.mail.conversations.reply.markDone')}
        />
        {confirming ? (
          <Box
            role="group"
            aria-live="polite"
            sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}
          >
            <Typography variant="body2" sx={{ fontWeight: 500 }}>
              {t('eventPage.mail.conversations.reply.confirm')}
            </Typography>
            <Button
              onClick={() => setConfirming(false)}
              disabled={reply.isPending}
              data-testid="conversation-reply-confirm-back"
            >
              {t('eventPage.mail.composer.footer.back')}
            </Button>
            <Button
              ref={yesButton}
              variant="contained"
              onClick={send}
              disabled={reply.isPending}
              startIcon={
                reply.isPending ? <CircularProgress size={16} color="inherit" /> : <SendIcon />
              }
              data-testid="conversation-reply-confirm-yes"
            >
              {t('eventPage.mail.composer.footer.yes')}
            </Button>
          </Box>
        ) : (
          <Button
            variant="contained"
            onClick={() => setConfirming(true)}
            disabled={empty || reply.isPending}
            startIcon={<SendIcon />}
            data-testid="conversation-reply-send"
          >
            {t('eventPage.mail.conversations.reply.send')}
          </Button>
        )}
      </Box>

      <Snackbar
        open={sent}
        autoHideDuration={5000}
        onClose={() => setSent(false)}
        message={t('eventPage.mail.conversations.reply.sent')}
        data-testid="conversation-reply-sent"
      />
    </Box>
  );
};

export default ConversationReplyBox;
