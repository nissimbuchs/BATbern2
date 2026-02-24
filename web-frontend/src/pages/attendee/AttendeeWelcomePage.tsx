/**
 * AttendeeWelcomePage
 * Landing page for logged-in attendees while the personal dashboard (Epic 7) is in development.
 * Provides context and directs users to the public event browsing experience.
 */

import { Link } from 'react-router-dom';
import { CalendarDays, Archive, Sparkles } from 'lucide-react';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Button } from '@/components/public/ui/button';
import { Card } from '@/components/public/ui/card';
import { useAuth } from '@/hooks/useAuth';
import { useTranslation } from 'react-i18next';

const AttendeeWelcomePage = () => {
  const { user } = useAuth();
  const { t } = useTranslation('common');

  // Derive a friendly first name from username (e.g. "john.doe" → "John")
  const rawFirst = user?.username?.split(/[.\s_-]/)[0] ?? '';
  const firstName = rawFirst.charAt(0).toUpperCase() + rawFirst.slice(1);

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-24 max-w-2xl text-center">
        {/* Greeting */}
        <Sparkles className="h-14 w-14 text-blue-400 mx-auto mb-6" />
        <h1 className="text-4xl font-light mb-3">
          {firstName
            ? t('attendee.welcome.titleWithName', { name: firstName })
            : t('attendee.welcome.title')}
        </h1>
        <p className="text-zinc-400 text-lg mb-3">{t('attendee.welcome.subtitle')}</p>
        <p className="text-sm text-zinc-500 mb-10">{t('attendee.welcome.dashboardSoon')}</p>

        {/* CTAs */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center mb-12">
          <Button asChild size="lg">
            <Link to="/">
              <CalendarDays className="h-5 w-5 mr-2" />
              {t('attendee.welcome.browseEvents')}
            </Link>
          </Button>
          <Button variant="secondary" asChild size="lg">
            <Link to="/archive">
              <Archive className="h-5 w-5 mr-2" />
              {t('attendee.welcome.viewArchive')}
            </Link>
          </Button>
        </div>

        {/* What you can do */}
        <Card className="p-6 text-left">
          <h2 className="text-sm font-medium text-zinc-300 mb-4">
            {t('attendee.welcome.canDoTitle')}
          </h2>
          <ul className="space-y-2 text-sm text-zinc-400">
            <li>• {t('attendee.welcome.canDo1')}</li>
            <li>• {t('attendee.welcome.canDo2')}</li>
            <li>• {t('attendee.welcome.canDo3')}</li>
          </ul>
        </Card>
      </div>
    </PublicLayout>
  );
};

export default AttendeeWelcomePage;
