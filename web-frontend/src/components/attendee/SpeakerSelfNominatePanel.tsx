/**
 * SpeakerSelfNominatePanel — Story 7.2 "I Could Speak on That".
 *
 * An "I could speak on that" button on an upcoming-event card. Visible only to logged-in users
 * (the caller also gates on topic-set + published). Clicking opens a small form (title + abstract)
 * that self-nominates the attendee as a speaker FOR THAT event — the card supplies the eventCode,
 * so no event picker is needed and multiple simultaneous open events each get their own button.
 *
 * Tailwind-only by design: the upcoming-event card lives on the public (no-MUI) homepage. The form
 * lives in a Radix Dialog (rendered in a portal) so its clicks don't bubble to the card's <Link>;
 * the trigger button stops propagation + prevents default for the same reason.
 *
 * Identity (name + company) is auto-filled server-side from the attendee's profile — the body
 * carries only the proposed talk. One self-nomination per attendee per event (the backend rejects
 * a repeat with 409); we render the already-done state on success or on a duplicate error.
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import { Mic } from 'lucide-react';
import { isAxiosError } from 'axios';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { Button } from '@/components/public/ui/button';
import { Input } from '@/components/public/ui/input';
import { Textarea } from '@/components/public/ui/textarea';
import { Label } from '@/components/public/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/public/ui/dialog';
import { selfNominateSpeaker } from '@/services/api/speakerNominationApi';

const MIN_TITLE = 5;
const MAX_TITLE = 255;
const MIN_ABSTRACT = 10;
const MAX_ABSTRACT = 5000;

interface SpeakerSelfNominatePanelProps {
  /** The event this nomination targets (supplied by the card). */
  eventCode: string;
  /** Optional topic name shown in the dialog for context. */
  topicName?: string;
}

export const SpeakerSelfNominatePanel = ({
  eventCode,
  topicName,
}: SpeakerSelfNominatePanelProps) => {
  const { t } = useTranslation('common');
  const { isAuthenticated } = useAuth();
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [abstract, setAbstract] = useState('');
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      selfNominateSpeaker(eventCode, { sessionTitle: title.trim(), abstract: abstract.trim() }),
    onSuccess: () => {
      setDone(true);
      setTitle('');
      setAbstract('');
    },
    onError: (err) => {
      // A 409 means the attendee already self-nominated for this event — treat as already-done.
      if (isAxiosError(err) && err.response?.status === 409) {
        setDone(true);
      }
    },
  });

  // Login gate: the button is for logged-in attendees only.
  if (!isAuthenticated) {
    return null;
  }

  const titleValid = title.trim().length >= MIN_TITLE && title.trim().length <= MAX_TITLE;
  const abstractValid =
    abstract.trim().length >= MIN_ABSTRACT && abstract.trim().length <= MAX_ABSTRACT;
  const canSubmit = titleValid && abstractValid && !mutation.isPending;
  const isDuplicateError =
    mutation.isError && isAxiosError(mutation.error) && mutation.error.response?.status === 409;

  return (
    <div data-testid={`self-nominate-panel-${eventCode}`}>
      {done ? (
        <p
          className="text-sm text-green-400 flex items-center gap-2"
          role="status"
          data-testid="self-nominate-done"
        >
          <Mic className="h-4 w-4" />
          {t('attendee.selfNominate.alreadyDone')}
        </p>
      ) : (
        <Button
          type="button"
          variant="outline"
          className="w-full"
          data-testid="self-nominate-button"
          onClick={(e) => {
            // The card is a <Link> — don't navigate when opening the form.
            e.preventDefault();
            e.stopPropagation();
            setOpen(true);
          }}
        >
          <Mic className="h-4 w-4 mr-2" />
          {t('attendee.selfNominate.button')}
        </Button>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent data-testid="self-nominate-dialog">
          <DialogHeader>
            <DialogTitle>{t('attendee.selfNominate.heading')}</DialogTitle>
            <DialogDescription>
              {topicName
                ? t('attendee.selfNominate.introWithTopic', { topic: topicName })
                : t('attendee.selfNominate.intro')}
            </DialogDescription>
          </DialogHeader>

          {done ? (
            <p className="text-sm text-green-400" role="status" data-testid="self-nominate-success">
              {t('attendee.selfNominate.success')}
            </p>
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (canSubmit) mutation.mutate();
              }}
              className="space-y-4"
            >
              <div className="space-y-1.5">
                <Label htmlFor="self-nominate-title">{t('attendee.selfNominate.titleLabel')}</Label>
                <Input
                  id="self-nominate-title"
                  data-testid="self-nominate-title"
                  value={title}
                  maxLength={MAX_TITLE}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder={t('attendee.selfNominate.titlePlaceholder')}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="self-nominate-abstract">
                  {t('attendee.selfNominate.abstractLabel')}
                </Label>
                <Textarea
                  id="self-nominate-abstract"
                  data-testid="self-nominate-abstract"
                  value={abstract}
                  maxLength={MAX_ABSTRACT}
                  onChange={(e) => setAbstract(e.target.value)}
                  placeholder={t('attendee.selfNominate.abstractPlaceholder')}
                  rows={4}
                />
              </div>
              {mutation.isError && (
                <p className="text-sm text-red-400" role="alert" data-testid="self-nominate-error">
                  {isDuplicateError
                    ? t('attendee.selfNominate.alreadyDone')
                    : t('attendee.selfNominate.error')}
                </p>
              )}
              <Button type="submit" disabled={!canSubmit} data-testid="self-nominate-submit">
                {mutation.isPending
                  ? t('attendee.selfNominate.submitting')
                  : t('attendee.selfNominate.submit')}
              </Button>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default SpeakerSelfNominatePanel;
