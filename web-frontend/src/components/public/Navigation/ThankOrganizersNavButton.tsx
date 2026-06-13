/**
 * ThankOrganizersNavButton (Story 7.4 — "Thank the Organizers")
 *
 * A compact "like"-style button in the public top navigation showing the aggregate
 * thank-you count for the current live/completed event. Pressing it opens a small
 * Radix popover with an optional note + send action; clicking outside dismisses it.
 *
 * Tailwind + Radix only (NO MUI — lives in the public header). Anyone may thank:
 * logged-in attendees are deduped server-side; anonymous claps are Turnstile-guarded
 * + rate-limited. The aggregate count is public; notes are organizer-visible only (AC6).
 *
 * The invisible Turnstile widget is mounted OUTSIDE the popover (always in the DOM) so
 * useTurnstile's render effect can attach to it — a popover-gated ref would never render
 * the widget and the token would never generate in production.
 */

import { useState } from 'react';
import { Heart } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import { Button } from '@/components/public/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/public/ui/popover';
import { useThanksCount, useSubmitThanks } from '@/hooks/useThanks/useThanks';
import { useTurnstile } from '@/hooks/useTurnstile';
import { useAuth } from '@/hooks/useAuth';

type WidgetState = 'idle' | 'submitting' | 'success' | 'error';

interface ThankOrganizersNavButtonProps {
  eventCode: string;
}

/**
 * Soft one-per-browser guard for ANONYMOUS thank-yous (logged-in users are deduped server-side
 * and may re-open to update their note). localStorage can throw (private mode / quota) — never
 * let that break the button.
 */
const thankedKey = (eventCode: string) => `batbern.thanked.${eventCode}`;

function readThanked(eventCode: string): boolean {
  try {
    return localStorage.getItem(thankedKey(eventCode)) === 'true';
  } catch {
    return false;
  }
}

function rememberThanked(eventCode: string): void {
  try {
    localStorage.setItem(thankedKey(eventCode), 'true');
  } catch {
    /* ignore — soft guard only */
  }
}

export function ThankOrganizersNavButton({ eventCode }: ThankOrganizersNavButtonProps) {
  const { t } = useTranslation('events');
  const { isAuthenticated } = useAuth();
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [widgetState, setWidgetState] = useState<WidgetState>('idle');
  // Soft guard applies to anonymous visitors only.
  const [alreadyThanked, setAlreadyThanked] = useState(
    () => !isAuthenticated && readThanked(eventCode)
  );

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
            // Anonymous: persist now so this browser does not casually re-increment (the
            // server rate limit stays the hard backstop). We keep the popover on the success
            // message and only swap to the disabled "thanked" pill once it closes (below).
            if (!isAuthenticated) {
              rememberThanked(eventCode);
            }
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

  // Anonymous visitor who already thanked in this browser → a disabled "thanked" pill, no popover.
  if (alreadyThanked) {
    return (
      <Button
        variant="outline"
        disabled
        className="flex items-center gap-1.5 px-3"
        data-testid="thanks-nav-thanked"
        aria-label={t('thanks.widget.alreadyThanked')}
        title={t('thanks.widget.alreadyThanked')}
      >
        <Heart className="h-4 w-4 fill-rose-400 text-rose-400" aria-hidden="true" />
        <span className="text-sm tabular-nums" data-testid="thanks-nav-count">
          {count}
        </span>
      </Button>
    );
  }

  return (
    <div className="relative" data-testid="thanks-nav">
      <Popover
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (next) {
            setWidgetState('idle');
          } else if (!isAuthenticated && readThanked(eventCode)) {
            // Anonymous + just thanked → collapse to the disabled "thanked" pill on close.
            setAlreadyThanked(true);
          }
        }}
      >
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className="flex items-center gap-1.5 px-3"
            data-testid="thanks-nav-trigger"
            aria-label={t('thanks.widget.title')}
            title={t('thanks.widget.title')}
          >
            <Heart className="h-4 w-4 fill-rose-500/20 text-rose-400" aria-hidden="true" />
            <span className="text-sm tabular-nums" data-testid="thanks-nav-count">
              {count}
            </span>
          </Button>
        </PopoverTrigger>
        <PopoverContent align="end" className="w-72" data-testid="thanks-nav-popover">
          <p className="mb-1 text-sm font-medium text-foreground">🙏 {t('thanks.widget.title')}</p>
          <p className="mb-3 text-xs text-muted-foreground">{t('thanks.widget.subtitle')}</p>

          {widgetState === 'success' ? (
            <p className="py-2 text-sm text-green-400" data-testid="thanks-success">
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
              {/* Story 7.7: logged-in notes may be curated onto the public marquee with the author's
                  name — surface that up front (anonymous claps are never featured). */}
              {isAuthenticated && (
                <p className="text-xs text-muted-foreground" data-testid="thanks-public-notice">
                  {t('thanks.widget.publicNotice')}
                </p>
              )}
              <div className="flex items-center justify-between gap-2">
                <Button
                  type="submit"
                  size="sm"
                  disabled={widgetState === 'submitting'}
                  data-testid="thanks-submit"
                >
                  {t('thanks.widget.button')}
                </Button>
                <span className="text-xs text-muted-foreground" data-testid="thanks-count">
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
        </PopoverContent>
      </Popover>

      {/* Invisible Turnstile widget — always mounted (outside the popover) so the render
          effect can attach; zero-size + out of flow so it never affects the nav layout. */}
      <div ref={widgetRef} className="pointer-events-none absolute h-0 w-0 overflow-hidden" />
    </div>
  );
}
