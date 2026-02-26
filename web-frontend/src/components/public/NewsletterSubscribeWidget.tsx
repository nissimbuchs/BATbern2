/**
 * NewsletterSubscribeWidget (Story 10.7 — AC4)
 *
 * Footer widget for subscribing anonymous visitors to the BATbern newsletter.
 * Shows success / already-subscribed / error inline states.
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import { Input } from '@/components/public/ui/input';
import { Button } from '@/components/public/ui/button';
import { useNewsletterSubscribe } from '@/hooks/useNewsletter/useNewsletter';

type WidgetState = 'idle' | 'submitting' | 'success' | 'already-subscribed' | 'error';

export function NewsletterSubscribeWidget() {
  const { t } = useTranslation('events');
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [widgetState, setWidgetState] = useState<WidgetState>('idle');

  const subscribeMutation = useNewsletterSubscribe();

  function validateEmail(value: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setEmailError('');

    if (!validateEmail(email)) {
      setEmailError(t('newsletter.widget.emailInvalid', 'Please enter a valid email address.'));
      return;
    }

    setWidgetState('submitting');

    subscribeMutation.mutate(
      { email },
      {
        onSuccess: () => {
          setWidgetState('success');
        },
        onError: (error) => {
          if (axios.isAxiosError(error) && error.response?.status === 409) {
            setWidgetState('already-subscribed');
          } else {
            setWidgetState('error');
          }
        },
      }
    );
  }

  if (widgetState === 'success') {
    return (
      <div className="py-4 text-center text-sm text-green-700 dark:text-green-400">
        {t('newsletter.widget.success')}
      </div>
    );
  }

  if (widgetState === 'already-subscribed') {
    return (
      <div className="py-4 text-center text-sm text-muted-foreground">
        {t('newsletter.widget.alreadySubscribed')}
      </div>
    );
  }

  return (
    <div className="py-4">
      <p className="mb-3 text-sm font-medium">{t('newsletter.widget.title')}</p>
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-2 sm:flex-row">
        <div className="flex-1">
          <Input
            type="email"
            placeholder={t('newsletter.widget.placeholder')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-label={t('newsletter.widget.placeholder')}
            disabled={widgetState === 'submitting'}
          />
          {emailError && <p className="mt-1 text-xs text-destructive">{emailError}</p>}
        </div>
        <Button type="submit" disabled={widgetState === 'submitting'} size="sm">
          {t('newsletter.widget.button')}
        </Button>
      </form>
      {widgetState === 'error' && (
        <p className="mt-2 text-xs text-destructive">{t('newsletter.widget.error')}</p>
      )}
    </div>
  );
}
