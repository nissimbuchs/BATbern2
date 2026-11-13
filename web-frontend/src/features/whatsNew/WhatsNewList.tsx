/**
 * The entries of "What's new" as a list of links: title, one sentence, and a "New" marker on the
 * entries the user has not seen yet. Used by the dashboard card and the "What's new" page.
 *
 * Tailwind only: both live on PublicLayout pages, which must not import MUI.
 */
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { ListedEntry } from './catalogue';
import { scrollToFragment } from './scrollToFragment';

export const WhatsNewList = ({ items }: { items: ListedEntry[] }) => {
  const { t } = useTranslation('events');
  const location = useLocation();

  return (
    <ul className="flex flex-col gap-3" data-testid="whats-new-list">
      {items.map(({ entry, isNew }) => {
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
  );
};

export default WhatsNewList;
