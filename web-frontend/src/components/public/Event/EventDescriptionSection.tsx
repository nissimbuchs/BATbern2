import { useTranslation } from 'react-i18next';

export function EventDescriptionSection({ description }: { description?: string | null }) {
  const { t } = useTranslation('events');

  if (!description?.trim()) return null;

  return (
    <div className="bg-zinc-900/50 border border-zinc-800 rounded-lg p-6 mb-6">
      <h2 className="text-xl font-light text-zinc-100 mb-3">{t('description.heading')}</h2>
      <p className="text-zinc-300 leading-relaxed whitespace-pre-wrap">{description}</p>
    </div>
  );
}
