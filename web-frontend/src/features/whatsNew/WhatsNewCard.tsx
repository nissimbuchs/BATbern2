/**
 * "What's new" card at the top of the attendee dashboard (story 2 of 2026-11-13): the features
 * released since the user last closed it, at most three, each with a title, one sentence and a
 * link. Closing it stores "seen now" on the user's settings. Renders nothing when nothing is new.
 *
 * Opened from the menu item (the URL carries #whats-new) it lists everything, new ones first and
 * marked, also when nothing is new (owner 2026-11-13). Closing that list stores "seen now" only when
 * something was new, and drops the fragment so the list closes.
 *
 * Tailwind only: it lives on the PublicLayout dashboard, which must not import MUI.
 */
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Sparkles, X } from 'lucide-react';
import { Card } from '@/components/public/ui/card';
import { scrollToFragment } from './scrollToFragment';
import { useWhatsNew } from './useWhatsNew';

export const WhatsNewCard = () => {
  const { t } = useTranslation('events');
  const location = useLocation();
  const navigate = useNavigate();
  const { entries, all, hasNew, dismiss, dismissing } = useWhatsNew();
  const showAll = location.hash === '#whats-new';
  const listed = showAll ? all : entries.map((entry) => ({ entry, isNew: false }));

  if (listed.length === 0) {
    return null;
  }

  const close = () => {
    if (hasNew) dismiss();
    if (showAll) {
      navigate({ pathname: location.pathname, search: location.search }, { replace: true });
    }
  };

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
          onClick={close}
          disabled={dismissing}
          className="rounded-md p-1 text-zinc-400 hover:text-zinc-200 hover:bg-accent transition-colors disabled:opacity-50"
          aria-label={t('whatsNew.close')}
          data-testid="whats-new-close"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <ul className="flex flex-col gap-3">
        {listed.map(({ entry, isNew }) => {
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
                <span className="flex items-center gap-2 text-sm font-medium text-foreground">
                  <span className="group-hover:underline">
                    {t(`whatsNew.entries.${entry.key}.title`)}
                  </span>
                  {isNew && (
                    <span
                      className="rounded-full bg-blue-500/15 px-2 py-0.5 text-xs font-medium text-blue-400"
                      data-testid={`whats-new-new-${entry.key}`}
                    >
                      {t('whatsNew.isNew')}
                    </span>
                  )}
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
