/**
 * PublicFooter Component
 * Footer for public website with contact info, links, and copyright
 */

import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export const PublicFooter = () => {
  const { t } = useTranslation('common');
  const currentYear = new Date().getFullYear();

  return (
    <footer className="border-t border-zinc-800 bg-zinc-900">
      <div className="container mx-auto px-4 py-8">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
          {/* Branding */}
          <div>
            <h3 className="text-lg font-light">{t('app.name')}</h3>
            <p className="mt-2 text-sm text-zinc-400">{t('footer.tagline')}</p>
            <p className="mt-4 text-sm text-zinc-400">
              <a
                href="mailto:info@berner-architekten-treffen.ch"
                className="hover:text-blue-400 transition-colors"
              >
                info@berner-architekten-treffen.ch
              </a>
            </p>
          </div>

          {/* Links */}
          <div>
            <h4 className="text-sm font-medium text-zinc-300">{t('footer.quickLinks')}</h4>
            <ul className="mt-2 space-y-2">
              <li>
                <Link
                  to="/"
                  className="text-sm text-zinc-400 hover:text-blue-400 transition-colors"
                >
                  {t('footer.currentEvent')}
                </Link>
              </li>
              <li>
                <Link
                  to="/archive"
                  className="text-sm text-zinc-400 hover:text-blue-400 transition-colors"
                >
                  {t('navigation.pastEvents')}
                </Link>
              </li>
              <li>
                <Link
                  to="/about"
                  className="text-sm text-zinc-400 hover:text-blue-400 transition-colors"
                >
                  {t('navigation.about')}
                </Link>
              </li>
            </ul>
          </div>

          {/* Legal */}
          <div>
            <h4 className="text-sm font-medium text-zinc-300">{t('footer.legal')}</h4>
            <ul className="mt-2 space-y-2">
              <li>
                <Link
                  to="/privacy"
                  className="text-sm text-zinc-400 hover:text-blue-400 transition-colors"
                >
                  {t('footer.privacyPolicy')}
                </Link>
              </li>
              <li>
                <Link
                  to="/terms"
                  className="text-sm text-zinc-400 hover:text-blue-400 transition-colors"
                >
                  {t('footer.termsOfService')}
                </Link>
              </li>
            </ul>
            <p className="mt-4 text-sm text-zinc-400">
              {t('footer.copyright', { year: currentYear })}
            </p>
          </div>
        </div>
      </div>
    </footer>
  );
};
