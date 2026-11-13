/**
 * WhatsNewPage — `/attendee/whats-new` (owner 2026-11-13): every news entry that applies to the
 * logged-in user, the new ones first and marked "New", with a search over title and text. No close
 * button: opening the page counts as seen (the dot on the user menu goes away), while the markers
 * of this visit stay until the user leaves. The menu item "What's new" leads here; the dashboard
 * card shows only the new ones and links here.
 *
 * Under `/attendee`, which every logged-in role may open (authRules).
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, Sparkles } from 'lucide-react';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Input } from '@/components/public/ui/input';
import type { ListedEntry } from '@/features/whatsNew/catalogue';
import { useWhatsNew } from '@/features/whatsNew/useWhatsNew';
import { WhatsNewList } from '@/features/whatsNew/WhatsNewList';

/** Lower case without accents, so "vortrage" finds "Vorträge". */
const fold = (text: string) =>
  text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase();

const WhatsNewPage = () => {
  const { t } = useTranslation('events');
  const { all, hasNew, dismiss, ready } = useWhatsNew();
  const [query, setQuery] = useState('');

  // The list as the user found it: storing "seen" must not take the markers away mid-visit.
  // (Set during render, React's pattern for state derived from the first answer.)
  const [items, setItems] = useState<ListedEntry[] | null>(null);
  if (items === null && ready) setItems(all);

  const stored = useRef(false);
  useEffect(() => {
    if (ready && hasNew && !stored.current) {
      stored.current = true;
      dismiss();
    }
  }, [ready, hasNew, dismiss]);

  const shown = useMemo(() => {
    const words = fold(query).split(/\s+/).filter(Boolean);
    if (words.length === 0) return items ?? [];
    return (items ?? []).filter(({ entry }) => {
      const haystack = fold(
        `${t(`whatsNew.entries.${entry.key}.title`)} ${t(`whatsNew.entries.${entry.key}.text`)}`
      );
      return words.every((w) => haystack.includes(w));
    });
  }, [items, query, t]);

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-3xl">
        <div className="flex items-center gap-3 mb-6">
          <Sparkles className="h-6 w-6 text-blue-400" aria-hidden="true" />
          <h1 className="text-3xl font-light text-foreground">{t('whatsNew.title')}</h1>
        </div>
        <div className="relative mb-6">
          <Search
            className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-500"
            aria-hidden="true"
          />
          <Input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('whatsNew.searchPlaceholder')}
            aria-label={t('whatsNew.searchPlaceholder')}
            className="pl-9"
            data-testid="whats-new-search"
          />
        </div>
        {ready && shown.length === 0 ? (
          <p className="text-sm text-zinc-500" data-testid="whats-new-no-match">
            {t('whatsNew.noMatch')}
          </p>
        ) : (
          shown.length > 0 && <WhatsNewList items={shown} />
        )}
      </div>
    </PublicLayout>
  );
};

export default WhatsNewPage;
