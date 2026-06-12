/**
 * ThankOrganizersWidget (Story 7.4 — "Thank the Organizers")
 *
 * Tailwind-only (NO MUI — lives on the public event/archive page) one-click thank-you for the
 * volunteer organizers, shown once an event is live/completed. Anyone may thank — logged-in
 * attendees are deduped server-side; anonymous claps are Turnstile-guarded + rate-limited.
 *
 * The aggregate count is public; the optional note is organizer-visible only (AC6) — there is no
 * public note wall, so the widget never renders other people's notes.
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import { Button } from '@/components/public/ui/button';
import { useThanksCount, useSubmitThanks } from '@/hooks/useThanks/useThanks';
import { useTurnstile } from '@/hooks/useTurnstile';

type WidgetState = 'idle' | 'submitting' | 'success' | 'error';

interface ThankOrganizersWidgetProps {
  eventCode: string;
}

export function ThankOrganizersWidget({ eventCode }: ThankOrganizersWidgetProps) {
  const { t } = useTranslation('events');
  const [note, setNote] = useState('');
  const [widgetState, setWidgetState] = useState<WidgetState>('idle');

  const { data: thanks } = useThanksCount(eventCode);
  const submitMutation = useSubmitThanks(eventCode);
  const { getToken, resetWidget, widgetRef } = useTurnstile();

  const count = thanks?.count ?? 0;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setWidgetState('submitting');

    getToken().then((turnstileToken) => {
      submitMutation.mutate(
        { note: note.trim() || null, turnstileToken },
        {
          onSuccess: () => {
            setNote('');
            setWidgetState('success');
          },
          onError: (error) => {
            if (
              axios.isAxiosError(error) &&
              error.response?.status === 403 &&
              (error.response.data?.error === 'turnstile_required' ||
                error.response.data?.error === 'turnstile_failed')
            ) {
              resetWidget();
            }
            setWidgetState('error');
          },
        }
      );
    });
  }

  return (
    <section
      className="mt-12 rounded-lg border border-zinc-800 bg-zinc-900/50 p-8"
      data-testid="thank-organizers-widget"
    >
      <p className="mb-2 text-xl font-light text-zinc-100">🙏 {t('thanks.widget.title')}</p>
      <p className="mb-4 text-sm text-zinc-400">{t('thanks.widget.subtitle')}</p>

      {/* Invisible Turnstile widget container (anonymous submissions) */}
      <div ref={widgetRef} />

      {widgetState === 'success' ? (
        <p className="py-2 text-sm text-green-700 dark:text-green-400" data-testid="thanks-success">
          {t('thanks.widget.success')}
        </p>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-2">
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={500}
            rows={2}
            placeholder={t('thanks.widget.notePlaceholder')}
            aria-label={t('thanks.widget.notePlaceholder')}
            disabled={widgetState === 'submitting'}
            className="w-full resize-none rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
            data-testid="thanks-note-input"
          />
          <div className="flex items-center gap-3">
            <Button
              type="submit"
              size="sm"
              disabled={widgetState === 'submitting'}
              data-testid="thanks-submit"
            >
              {t('thanks.widget.button')}
            </Button>
            <span className="text-sm text-muted-foreground" data-testid="thanks-count">
              {t('thanks.widget.count', { count })}
            </span>
          </div>
        </form>
      )}

      {widgetState === 'error' && (
        <p className="mt-2 text-xs text-destructive" data-testid="thanks-error">
          {t('thanks.widget.error')}
        </p>
      )}
    </section>
  );
}
