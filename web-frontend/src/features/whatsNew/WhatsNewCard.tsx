/**
 * "What's new" card at the top of the attendee dashboard (story 2 of 2026-11-13): the features
 * released since the user last closed it, at most three, each with a title, one sentence and a
 * link. Closing it stores "seen now" on the user's settings. Renders nothing when nothing is new.
 * The full, searchable list is the page {@link WHATS_NEW_PAGE_PATH}, linked from the card.
 *
 * Tailwind only: it lives on the PublicLayout dashboard, which must not import MUI.
 */
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Sparkles, X } from 'lucide-react';
import { Card } from '@/components/public/ui/card';
import { WHATS_NEW_PAGE_PATH } from './catalogue';
import { useWhatsNew } from './useWhatsNew';
import { WhatsNewList } from './WhatsNewList';

export const WhatsNewCard = () => {
  const { t } = useTranslation('events');
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
      <WhatsNewList items={entries.map((entry) => ({ entry, isNew: false }))} />
      <Link
        to={WHATS_NEW_PAGE_PATH}
        className="mt-3 inline-block text-sm text-blue-400 hover:underline"
        data-testid="whats-new-all-link"
      >
        {t('whatsNew.allNews')}
      </Link>
    </Card>
  );
};

export default WhatsNewCard;
