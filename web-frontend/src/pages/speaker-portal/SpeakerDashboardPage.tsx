/**
 * SpeakerDashboardPage Component (Story 6.4)
 *
 * Speaker dashboard showing upcoming and past events.
 * Accessed via magic link: /speaker-portal/dashboard?token={token}
 *
 * Features:
 * - View upcoming events with status, deadlines, and actions
 * - View past events with material info
 * - Profile completeness indicator
 * - Organizer contact information
 */

import { useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import {
  Calendar,
  MapPin,
  Clock,
  AlertCircle,
  User,
  FileText,
  Mail,
  ChevronRight,
  CheckCircle2,
  XCircle,
  AlertTriangle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import {
  speakerPortalService,
  DashboardUpcomingEvent,
  DashboardPastEvent,
} from '@/services/speakerPortalService';

type PageState = 'loading' | 'dashboard' | 'error';

/**
 * Calculate urgency level for deadline display.
 * Green: >14 days, Amber: 7-14 days, Red: <7 days
 */
function getDeadlineUrgency(deadline: string): 'green' | 'amber' | 'red' {
  const deadlineDate = new Date(deadline);
  const now = new Date();
  const diffDays = Math.ceil((deadlineDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
  if (diffDays < 7) return 'red';
  if (diffDays <= 14) return 'amber';
  return 'green';
}

const urgencyColors = {
  green: 'text-green-600 bg-green-50 dark:text-green-400 dark:bg-green-950/40',
  amber: 'text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-950/40',
  red: 'text-red-600 bg-red-50 dark:text-red-400 dark:bg-red-950/40',
};

const statusBadgeColors: Record<string, string> = {
  INVITED: 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300',
  ACCEPTED: 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300',
  CONFIRMED: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300',
};

const contentStatusColors: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  SUBMITTED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300',
  APPROVED: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  REVISION_NEEDED: 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300',
};

function UpcomingEventCard({ event, token }: { event: DashboardUpcomingEvent; token: string }) {
  const { t } = useTranslation();

  return (
    <Card
      className="p-6 mb-4"
      role="article"
      aria-label={t('speakerPortal.dashboard.eventCardAriaLabel', {
        title: event.eventTitle,
        status: event.workflowStateLabel,
      })}
    >
      <div className="flex flex-col gap-4">
        {/* Event header */}
        <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-2">
          <div>
            <h3 className="text-lg font-semibold text-foreground">{event.eventTitle}</h3>
            <div className="flex items-center gap-4 text-sm text-muted-foreground mt-1">
              <span className="flex items-center gap-1">
                <Calendar className="w-4 h-4" />
                {event.eventDate}
              </span>
              {event.eventLocation && (
                <span className="flex items-center gap-1">
                  <MapPin className="w-4 h-4" />
                  {event.eventLocation}
                </span>
              )}
            </div>
          </div>
          <div className="flex gap-2 flex-wrap">
            <span
              className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${statusBadgeColors[event.workflowState] || 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'}`}
              aria-label={`${t('speakerPortal.dashboard.speakerStatus')}: ${event.workflowStateLabel}`}
            >
              {event.workflowStateLabel}
            </span>
            {event.contentStatus && (
              <span
                className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${contentStatusColors[event.contentStatus] || 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'}`}
                aria-label={`${t('speakerPortal.dashboard.contentStatus')}: ${event.contentStatusLabel}`}
              >
                {event.contentStatusLabel}
              </span>
            )}
          </div>
        </div>

        {/* Session title */}
        {event.sessionTitle && (
          <div className="text-sm text-muted-foreground">
            <span className="font-medium">{t('speakerPortal.dashboard.session')}:</span>{' '}
            {event.sessionTitle}
          </div>
        )}

        {/* Content status (AC4) */}
        <div
          className="grid grid-cols-3 gap-2 text-sm"
          role="status"
          aria-label={t('speakerPortal.dashboard.materialProgress')}
        >
          <div className="flex items-center gap-1">
            {event.hasTitle ? (
              <CheckCircle2 className="w-4 h-4 text-green-500" aria-hidden="true" />
            ) : (
              <XCircle className="w-4 h-4 text-gray-300 dark:text-gray-600" aria-hidden="true" />
            )}
            <span>{t('speakerPortal.dashboard.titleStatus')}</span>
          </div>
          <div className="flex items-center gap-1">
            {event.hasAbstract ? (
              <CheckCircle2 className="w-4 h-4 text-green-500" aria-hidden="true" />
            ) : (
              <XCircle className="w-4 h-4 text-gray-300 dark:text-gray-600" aria-hidden="true" />
            )}
            <span>{t('speakerPortal.dashboard.abstractStatus')}</span>
          </div>
          <div className="flex items-center gap-1">
            {event.hasMaterial ? (
              <CheckCircle2 className="w-4 h-4 text-green-500" aria-hidden="true" />
            ) : (
              <XCircle className="w-4 h-4 text-gray-300 dark:text-gray-600" aria-hidden="true" />
            )}
            <span>{t('speakerPortal.dashboard.materialStatus')}</span>
          </div>
        </div>

        {/* Reviewer feedback (AC4) */}
        {event.reviewerFeedback && (
          <div className="bg-orange-50 border border-orange-200 rounded-md p-3 text-sm dark:bg-orange-950/30 dark:border-orange-800">
            <div className="flex items-center gap-1 font-medium text-orange-800 dark:text-orange-300 mb-1">
              <AlertTriangle className="w-4 h-4" />
              {t('speakerPortal.dashboard.revisionRequested')}
            </div>
            <p className="text-orange-700 dark:text-orange-400">{event.reviewerFeedback}</p>
          </div>
        )}

        {/* Deadlines */}
        <div
          className="flex flex-wrap gap-3 text-sm"
          role="status"
          aria-label={t('speakerPortal.dashboard.deadlines')}
        >
          {event.responseDeadline && event.workflowState === 'INVITED' && (
            <span
              className={`flex items-center gap-1 px-2 py-1 rounded ${urgencyColors[getDeadlineUrgency(event.responseDeadline)]}`}
              aria-label={`${t('speakerPortal.dashboard.responseBy')}: ${event.responseDeadline}, ${getDeadlineUrgency(event.responseDeadline) === 'red' ? t('speakerPortal.dashboard.urgent') : getDeadlineUrgency(event.responseDeadline) === 'amber' ? t('speakerPortal.dashboard.soon') : t('speakerPortal.dashboard.onTime')}`}
            >
              <Clock className="w-3.5 h-3.5" aria-hidden="true" />
              {t('speakerPortal.dashboard.responseBy')}: {event.responseDeadline}
            </span>
          )}
          {event.contentDeadline && (
            <span
              className={`flex items-center gap-1 px-2 py-1 rounded ${urgencyColors[getDeadlineUrgency(event.contentDeadline)]}`}
              aria-label={`${t('speakerPortal.dashboard.contentBy')}: ${event.contentDeadline}, ${getDeadlineUrgency(event.contentDeadline) === 'red' ? t('speakerPortal.dashboard.urgent') : getDeadlineUrgency(event.contentDeadline) === 'amber' ? t('speakerPortal.dashboard.soon') : t('speakerPortal.dashboard.onTime')}`}
            >
              <Clock className="w-3.5 h-3.5" aria-hidden="true" />
              {t('speakerPortal.dashboard.contentBy')}: {event.contentDeadline}
            </span>
          )}
        </div>

        {/* Organizer contact (AC5) */}
        {event.organizerName && (
          <div className="text-sm text-muted-foreground flex items-center gap-1">
            <User className="w-4 h-4" />
            <span>
              {t('speakerPortal.dashboard.organizer')}: {event.organizerName}
            </span>
            {event.organizerEmail && (
              <a
                href={`mailto:${event.organizerEmail}?subject=BATbern%20-%20${encodeURIComponent(event.eventTitle)}`}
                className="text-blue-600 hover:underline flex items-center gap-0.5 ml-1"
              >
                <Mail className="w-3.5 h-3.5" />
                {t('speakerPortal.dashboard.contact')}
              </a>
            )}
          </div>
        )}

        {/* Quick actions (AC2) */}
        <nav
          className="flex flex-wrap gap-2 pt-2 border-t border-border"
          aria-label={t('labels.actions')}
        >
          {event.respondUrl && (
            <Link
              to={`${event.respondUrl}?token=${token}`}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md bg-blue-600 text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
              aria-label={`${t('speakerPortal.dashboard.respond')} to ${event.eventTitle}`}
            >
              {t('speakerPortal.dashboard.respond')}
              <ChevronRight className="w-3.5 h-3.5" aria-hidden="true" />
            </Link>
          )}
          <Link
            to={`${event.profileUrl}?token=${token}`}
            className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md border border-border text-foreground hover:bg-accent focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            aria-label={`${t('speakerPortal.dashboard.updateProfile')} for ${event.eventTitle}`}
          >
            <User className="w-3.5 h-3.5" aria-hidden="true" />
            {t('speakerPortal.dashboard.updateProfile')}
          </Link>
          {event.contentUrl && (
            <Link
              to={`${event.contentUrl}?token=${token}`}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md border border-border text-foreground hover:bg-accent focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
              aria-label={`${t('speakerPortal.dashboard.submitContent')} for ${event.eventTitle}`}
            >
              <FileText className="w-3.5 h-3.5" aria-hidden="true" />
              {t('speakerPortal.dashboard.submitContent')}
            </Link>
          )}
        </nav>
      </div>
    </Card>
  );
}

function PastEventCard({ event }: { event: DashboardPastEvent }) {
  const { t } = useTranslation();

  return (
    <Card
      className="p-4 mb-3"
      role="article"
      aria-label={`${t('speakerPortal.dashboard.pastEvent')}: ${event.eventTitle}`}
    >
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <h3 className="text-base font-medium text-foreground">{event.eventTitle}</h3>
          <div className="flex items-center gap-3 text-sm text-muted-foreground mt-0.5">
            <span className="flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5" aria-hidden="true" />
              {event.eventDate}
            </span>
            {event.sessionTitle && <span>{event.sessionTitle}</span>}
          </div>
        </div>
        <div className="text-sm text-muted-foreground">
          {event.hasMaterial ? (
            <span
              className="flex items-center gap-1 text-green-600 dark:text-green-400"
              aria-label={`${t('speakerPortal.dashboard.materialAvailable')}: ${event.materialFileName}`}
            >
              <FileText className="w-4 h-4" aria-hidden="true" />
              {event.materialFileName}
            </span>
          ) : (
            <span
              className="text-muted-foreground"
              aria-label={t('speakerPortal.dashboard.noMaterials')}
            >
              {t('speakerPortal.dashboard.noMaterials')}
            </span>
          )}
        </div>
      </div>
    </Card>
  );
}

const SpeakerDashboardPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const { t } = useTranslation();

  // Fetch dashboard data
  const {
    data: dashboard,
    error: fetchError,
    isLoading,
  } = useQuery({
    queryKey: ['speaker-dashboard', token],
    queryFn: () => speakerPortalService.getDashboard(token!),
    enabled: !!token,
    retry: false,
  });

  // Determine page state
  let pageState: PageState = 'loading';
  if (!token || fetchError) {
    pageState = 'error';
  } else if (!isLoading && dashboard) {
    pageState = 'dashboard';
  }

  // Error handling
  const getErrorContent = () => {
    if (!token) {
      return {
        title: t('speakerPortal.dashboard.invalidLink'),
        message: t('speakerPortal.dashboard.invalidLinkMessage'),
      };
    }
    const err = fetchError as Error & { errorCode?: string };
    if (err?.errorCode === 'EXPIRED') {
      return {
        title: t('speakerPortal.dashboard.linkExpired'),
        message: t('speakerPortal.dashboard.linkExpiredMessage'),
      };
    }
    return {
      title: t('speakerPortal.dashboard.error'),
      message: err?.message || t('speakerPortal.dashboard.errorMessage'),
    };
  };

  return (
    <PublicLayout>
      <div className="min-h-screen bg-background py-8 px-4">
        <div className="max-w-3xl mx-auto">
          {/* Loading state (AC8) */}
          {pageState === 'loading' && (
            <div className="flex flex-col items-center justify-center py-20">
              <BATbernLoader size={64} />
              <p className="text-muted-foreground">{t('speakerPortal.dashboard.loading')}</p>
            </div>
          )}

          {/* Error state (AC8) */}
          {pageState === 'error' && (
            <Card className="p-8 text-center">
              <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
              <h2 className="text-xl font-semibold text-foreground mb-2">
                {getErrorContent().title}
              </h2>
              <p className="text-muted-foreground">{getErrorContent().message}</p>
            </Card>
          )}

          {/* Dashboard content */}
          {pageState === 'dashboard' && dashboard && (
            <>
              {/* Header */}
              <header className="mb-8">
                <div className="flex items-center gap-4 mb-2">
                  {dashboard.profilePictureUrl ? (
                    <img
                      src={dashboard.profilePictureUrl}
                      alt={`${dashboard.speakerName} profile picture`}
                      className="w-16 h-16 rounded-full object-cover"
                    />
                  ) : (
                    <div
                      className="w-16 h-16 rounded-full bg-muted flex items-center justify-center"
                      role="img"
                      aria-label={t('speakerPortal.dashboard.profilePlaceholder', {
                        name: dashboard.speakerName,
                      })}
                    >
                      <User className="w-8 h-8 text-muted-foreground" aria-hidden="true" />
                    </div>
                  )}
                  <div>
                    <h1 className="text-2xl font-bold text-foreground">
                      {t('speakerPortal.dashboard.welcome', { name: dashboard.speakerName })}
                    </h1>
                    <p className="text-muted-foreground" role="status" aria-live="polite">
                      {t('speakerPortal.dashboard.profileComplete', {
                        percent: dashboard.profileCompleteness,
                      })}
                    </p>
                  </div>
                </div>
              </header>

              {/* Upcoming Events (AC2) */}
              <section className="mb-8" aria-labelledby="upcoming-events-heading">
                <h2
                  id="upcoming-events-heading"
                  className="text-xl font-semibold text-foreground mb-4"
                >
                  {t('speakerPortal.dashboard.upcomingEvents')}
                </h2>
                {dashboard.upcomingEvents.length === 0 ? (
                  <Card className="p-6 text-center text-muted-foreground" role="status">
                    {t('speakerPortal.dashboard.noUpcomingEvents')}
                  </Card>
                ) : (
                  <ul className="list-none">
                    {dashboard.upcomingEvents.map((event) => (
                      <li key={event.eventCode}>
                        <UpcomingEventCard event={event} token={token!} />
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              {/* Past Events (AC3) */}
              <section aria-labelledby="past-events-heading">
                <h2 id="past-events-heading" className="text-xl font-semibold text-foreground mb-4">
                  {t('speakerPortal.dashboard.pastEvents')}
                </h2>
                {dashboard.pastEvents.length === 0 ? (
                  <Card className="p-6 text-center text-muted-foreground" role="status">
                    {t('speakerPortal.dashboard.noPastEvents')}
                  </Card>
                ) : (
                  <ul className="list-none">
                    {dashboard.pastEvents.map((event) => (
                      <li key={event.eventCode}>
                        <PastEventCard event={event} />
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </div>
      </div>
    </PublicLayout>
  );
};

export default SpeakerDashboardPage;
