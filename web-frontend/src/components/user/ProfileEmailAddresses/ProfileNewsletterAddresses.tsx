/**
 * #1244 — the newsletter per address on /profile: one checkbox for the primary and each
 * confirmed additional address (owner decision 2026-11-07). Replaces the single switch that
 * only covered the login address.
 */

import { useTranslation } from 'react-i18next';
import { useMySubscriptions, usePutMySubscription } from '@/hooks/useNewsletter/useNewsletter';

export function ProfileNewsletterAddresses() {
  const { t, i18n } = useTranslation('common');
  const { data, isLoading } = useMySubscriptions();
  const put = usePutMySubscription();
  const language = i18n.language?.startsWith('de') ? 'de' : 'en';

  if (isLoading) {
    return null;
  }

  return (
    <ul className="space-y-3" data-testid="newsletter-addresses">
      {(data?.addresses ?? []).map((a) => (
        <li key={a.email}>
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              className="h-4 w-4"
              checked={a.subscribed}
              disabled={put.isPending}
              onChange={(e) =>
                put.mutate({ email: a.email, subscribed: e.target.checked, language })
              }
              data-testid={`newsletter-address-${a.email}`}
            />
            <span className="text-sm text-zinc-300 break-all">
              {a.email}
              {a.primary && (
                <span className="text-xs text-zinc-500 ml-2">{t('profile.emails.primary')}</span>
              )}
            </span>
          </label>
        </li>
      ))}
    </ul>
  );
}

export default ProfileNewsletterAddresses;
