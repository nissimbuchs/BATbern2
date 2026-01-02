/**
 * AboutPage Component
 * Public page explaining BATbern association, organizers, and mission
 */

import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader } from '@/components/public/ui/card';
import { OrganizerDisplay } from '@/components/public/About/OrganizerDisplay';
import { useUserList } from '@/hooks/useUserManagement';
import { useTranslation } from 'react-i18next';
import { FileText, Loader2 } from 'lucide-react';

const AboutPage = () => {
  const { t } = useTranslation('about');

  // Fetch organizers
  const { data: organizersData, isLoading } = useUserList({
    filters: { role: ['ORGANIZER'] },
    pagination: { page: 1, limit: 100 },
  });

  const organizers = organizersData?.data || [];

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-6xl">
        {/* Hero Section */}
        <div className="mb-16 text-center">
          <h1 className="text-4xl md:text-5xl font-light mb-6 text-zinc-100">
            {t('hero.title')}
          </h1>
          <p className="text-lg md:text-xl text-zinc-300 max-w-3xl mx-auto leading-relaxed">
            {t('hero.subtitle')}
          </p>
        </div>

        {/* Association Section */}
        <div className="mb-16">
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-3xl font-light text-zinc-100">{t('association.title')}</h2>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-zinc-300 leading-relaxed">{t('association.history')}</p>
              <p className="text-zinc-300 leading-relaxed">{t('association.mission')}</p>
              <p className="text-zinc-300 leading-relaxed">{t('association.platform')}</p>

              {/* Link to Statuten */}
              <div className="pt-4 border-t border-zinc-800">
                <a
                  href="/assets/statuten_v1.0.pdf"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 text-blue-400 hover:text-blue-300 transition-colors"
                >
                  <FileText className="h-5 w-5" />
                  <span>{t('association.statuten')}</span>
                </a>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Organizers Section */}
        <div className="mb-16">
          <h2 className="text-3xl font-light mb-8 text-zinc-100">{t('organizers.title')}</h2>
          <p className="text-zinc-300 mb-8 leading-relaxed">{t('organizers.description')}</p>

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-blue-400" />
            </div>
          ) : organizers.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              {organizers.map((organizer) => (
                <Card
                  key={organizer.id}
                  className="bg-zinc-900/50 border-zinc-800 hover:border-blue-400 transition-colors"
                >
                  <CardContent className="pt-6">
                    <OrganizerDisplay organizer={organizer} showBio={true} />
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : (
            <p className="text-zinc-400 text-center py-8">{t('organizers.noOrganizers')}</p>
          )}
        </div>

        {/* Contact Section */}
        <div>
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-3xl font-light text-zinc-100">{t('contact.title')}</h2>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-zinc-300 leading-relaxed">{t('contact.description')}</p>
              <div className="pt-4">
                <a
                  href="mailto:info@berner-architekten-treffen.ch"
                  className="text-blue-400 hover:text-blue-300 transition-colors text-lg"
                >
                  info@berner-architekten-treffen.ch
                </a>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </PublicLayout>
  );
};

export default AboutPage;
