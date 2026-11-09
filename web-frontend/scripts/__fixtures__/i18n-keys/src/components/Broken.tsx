import { useTranslation } from 'react-i18next';

export function Broken() {
  const { t } = useTranslation('events');
  return [t('events:does.not.exist'), t('title'), t("form.missing", 'Fallback')];
}

export function Second() {
  const { t } = useTranslation('common');
  return t('title');
}
