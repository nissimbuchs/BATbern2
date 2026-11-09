import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';

export function Mixed() {
  const { t } = useTranslation('events');
  return t('title');
}

/** These helpers get `t` from their caller: its namespace is not known here. */
export const label = (t: TFunction) => t('actions.save');

export function format(t: TFunction, n: number) {
  return [t('items', { count: n }), t('nowhere.either')];
}

export function After() {
  const { t } = useTranslation('events');
  return t('actions.cancel');
}
