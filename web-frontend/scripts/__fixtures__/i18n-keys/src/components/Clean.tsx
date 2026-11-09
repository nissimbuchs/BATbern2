import { useTranslation } from 'react-i18next';
import i18n from 'i18next';

// t('commented.out.key') is not a call
export function Clean({ count, field }: { count: number; field: string }) {
  const { t } = useTranslation('events');
  const { t: tCommon } = useTranslation();
  const { t: tForm } = useTranslation('events', { keyPrefix: 'form.name' });
  const { t: tBoth } = useTranslation(['events', 'common']);
  return [
    t('title'),
    t('form.name.label'),
    t('common:actions.save'),
    t('actions.cancel', { ns: 'common' }),
    t(`form.${field}.label`),
    t(field),
    t('form.' + field),
    tCommon('items', { count }),
    tCommon('greeting', { context: 'male' }),
    tCommon('flat.key.with.dots'),
    tCommon('actions', { returnObjects: true }),
    tCommon('greeting', { context: field }),
    tForm('help'),
    tBoth('actions.save'),
    i18n.t('actions.save'),
  ];
}
