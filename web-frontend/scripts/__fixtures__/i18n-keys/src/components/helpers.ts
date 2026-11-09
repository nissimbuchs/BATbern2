import type { TFunction } from 'i18next';

/** `t` comes in as a parameter: its namespace is not known here. */
export const label = (t: TFunction) => [t('form.name.help'), t('nowhere.at.all')];
