/**
 * Own text (Story 16.5): the organizer's subject and body, with the placeholder chips above the
 * body (AC6). A chip inserts `{{name}}` at the cursor of the field that last had focus: the subject
 * through its selection range, the body through the editor. The body editor is TinyMCE, loaded
 * lazily (`OwnTextBodyEditor`).
 */
import React, { Suspense, lazy, useRef } from 'react';
import { Box, Skeleton, Stack, TextField, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { PlaceholderChips } from './PlaceholderChips';
import type { BodyEditorHandle } from './OwnTextBodyEditor';

const OwnTextBodyEditor = lazy(() => import('./OwnTextBodyEditor'));

export const OWN_SUBJECT_MAX = 500;

/** The body the editor starts from; a new version remounts it (starting point). */
export interface BodySeed {
  html: string;
  version: number;
}

interface OwnTextEditorProps {
  subject: string;
  onSubjectChange: (subject: string) => void;
  bodySeed: BodySeed;
  onBodyChange: (html: string) => void;
  /** From the latest preview; empty until the server has answered once. */
  offeredVariables: string[];
  personalized: boolean;
}

export const OwnTextEditor: React.FC<OwnTextEditorProps> = ({
  subject,
  onSubjectChange,
  bodySeed,
  onBodyChange,
  offeredVariables,
  personalized,
}) => {
  const { t } = useTranslation('events');
  const subjectRef = useRef<HTMLInputElement>(null);
  const bodyRef = useRef<BodyEditorHandle | null>(null);
  // The body is where most placeholders go; it is the target until the subject had focus.
  const lastFocus = useRef<'subject' | 'body'>('body');

  const insert = (name: string) => {
    const token = `{{${name}}}`;
    if (lastFocus.current === 'subject' && subjectRef.current) {
      const input = subjectRef.current;
      const start = input.selectionStart ?? subject.length;
      const end = input.selectionEnd ?? start;
      const next = (subject.slice(0, start) + token + subject.slice(end)).slice(0, OWN_SUBJECT_MAX);
      onSubjectChange(next);
      const caret = Math.min(start + token.length, next.length);
      // After React has written the new value, put the cursor behind the placeholder.
      requestAnimationFrame(() => {
        input.focus();
        input.setSelectionRange(caret, caret);
      });
      return;
    }
    bodyRef.current?.insertContent(token);
  };

  return (
    <Stack spacing={1.5} data-testid="composer-own-text">
      <TextField
        label={t('eventPage.mail.composer.ownText.subject')}
        value={subject}
        // A single-line input: the browser drops pasted line breaks (the server rejects CR/LF).
        onChange={(e) => onSubjectChange(e.target.value)}
        onFocus={() => {
          lastFocus.current = 'subject';
        }}
        inputRef={subjectRef}
        size="small"
        fullWidth
        required
        inputProps={{ maxLength: OWN_SUBJECT_MAX, 'data-testid': 'composer-own-subject' }}
      />
      {offeredVariables.length > 0 && (
        <PlaceholderChips
          variables={offeredVariables}
          personalized={personalized}
          onInsert={insert}
        />
      )}
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 0.5 }} id="composer-own-body-label">
          {t('eventPage.mail.composer.ownText.body')}
        </Typography>
        <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, minHeight: 120 }}>
          <Suspense fallback={<Skeleton variant="rectangular" height={360} />}>
            <OwnTextBodyEditor
              key={bodySeed.version}
              initialValue={bodySeed.html}
              onChange={onBodyChange}
              onReady={(handle) => {
                bodyRef.current = handle;
              }}
              onFocus={() => {
                lastFocus.current = 'body';
              }}
              label={t('eventPage.mail.composer.ownText.body')}
            />
          </Suspense>
        </Box>
      </Box>
    </Stack>
  );
};
