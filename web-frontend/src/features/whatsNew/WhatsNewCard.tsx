/**
 * "What's new" card at the top of the attendee dashboard (story 2 of 2026-11-13): the features
 * released since the user last closed it, at most three, each with a title, one sentence and a
 * link. Closing it stores "seen now" on the user's settings. Renders nothing when nothing is new.
 *
 * Tailwind only: it lives on the PublicLayout dashboard, which must not import MUI.
 */
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Sparkles, X } from 'lucide-react';
import { Card } from '@/components/public/ui/card';
import { scrollToFragment } from './scrollToFragment';
import { useWhatsNew } from './useWhatsNew';

export const WhatsNewCard = () => {
  const { t } = useTranslation('events');
  const location = useLocation();
  const { entries, dismiss, dismissing } = useWhatsNew();

  if (entries.length === 0) {
    return null;
  }

  return (
    <Card
      id="whats-new"
      className="p-5 mb-6 text-left scroll-mt-28"
      data-testid="whats-new-card"
      aria-labelledby="whats-new-title"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-5 w-5 text-blue-400" aria-hidden="true" />
          <h2 id="whats-new-title" className="text-sm font-medium text-zinc-300">
            {t('whatsNew.title')}
          </h2>
        </div>
        <button
          type="button"
          onClick={dismiss}
          disabled={dismissing}
          className="rounded-md p-1 text-zinc-400 hover:text-zinc-200 hover:bg-accent transition-colors disabled:opacity-50"
          aria-label={t('whatsNew.close')}
          data-testid="whats-new-close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <ul className="flex flex-col gap-3">
        {entries.map((entry) => {
          const [path, fragment] = entry.link.split('#');
          return (
            <li key={entry.key} data-testid={`whats-new-entry-${entry.key}`}>
              <Link
                to={entry.link}
                className="group block rounded-md -mx-2 px-2 py-1 hover:bg-accent transition-colors"
                onClick={() => {
                  // Same page: the router changes only the hash, so scroll ourselves.
                  if (fragment && path === location.pathname) scrollToFragment(fragment);
                }}
              >
                <span className="block text-sm font-medium text-foreground group-hover:underline">
                  {t(`whatsNew.entries.${entry.key}.title`)}
                </span>
                <span className="block text-sm text-zinc-500">
                  {t(`whatsNew.entries.${entry.key}.text`)}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </Card>
  );
};

export default WhatsNewCard;
