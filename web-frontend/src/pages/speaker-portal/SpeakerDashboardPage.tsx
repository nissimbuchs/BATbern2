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
  Loader2,
  User,
  FileText,
  Mail,
  ChevronRight,
  CheckCircle2,
  XCircle,
  AlertTriangle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
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
  green: 'text-green-600 bg-green-50',
  amber: 'text-amber-600 bg-amber-50',
  red: 'text-red-600 bg-red-50',
};

const statusBadgeColors: Record<string, string> = {
  INVITED: 'bg-blue-100 text-blue-800',
  ACCEPTED: 'bg-green-100 text-green-800',
  CONFIRMED: 'bg-emerald-100 text-emerald-800',
};

const contentStatusColors: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  SUBMITTED: 'bg-blue-100 text-blue-700',
  APPROVED: 'bg-green-100 text-green-700',
  REVISION_NEEDED: 'bg-orange-100 text-orange-700',
};

function UpcomingEventCard({ event, token }: { event: DashboardUpcomingEvent; token: string }) {
  const { t } = useTranslation();

  return (
    <Card className="p-6 mb-4">
      <div className="flex flex-col gap-4">
        {/* Event header */}
        <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-2">
          <div>
            <h3 className="text-lg font-semibold text-gray-900">{event.eventTitle}</h3>
            <div className="flex items-center gap-4 text-sm text-gray-500 mt-1">
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
              className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${statusBadgeColors[event.workflowState] || 'bg-gray-100 text-gray-700'}`}
            >
              {event.workflowStateLabel}
            </span>
            {event.contentStatus && (
              <span
                className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${contentStatusColors[event.contentStatus] || 'bg-gray-100 text-gray-700'}`}
              >
                {event.contentStatusLabel}
              </span>
            )}
          </div>
        </div>

        {/* Session title */}
        {event.sessionTitle && (
          <div className="text-sm text-gray-700">
            <span className="font-medium">{t('speakerPortal.dashboard.session')}:</span>{' '}
            {event.sessionTitle}
          </div>
        )}

        {/* Content status (AC4) */}
        <div className="grid grid-cols-3 gap-2 text-sm">
          <div className="flex items-center gap-1">
            {event.hasTitle ? (
              <CheckCircle2 className="w-4 h-4 text-green-500" />
            ) : (
              <XCircle className="w-4 h-4 text-gray-300" />
            )}
            <span>{t('speakerPortal.dashboard.titleStatus')}</span>
          </div>
          <div className="flex items-center gap-1">
            {event.hasAbstract ? (
              <CheckCircle2 className="w-4 h-4 text-green-500" />
            ) : (
              <XCircle className="w-4 h-4 text-gray-300" />
            )}
            <span>{t('speakerPortal.dashboard.abstractStatus')}</span>
          </div>
          <div className="flex items-center gap-1">
            {event.hasMaterial ? (
              <CheckCircle2 className="w-4 h-4 text-green-500" />
            ) : (
              <XCircle className="w-4 h-4 text-gray-300" />
            )}
            <span>{t('speakerPortal.dashboard.materialStatus')}</span>
          </div>
        </div>

        {/* Reviewer feedback (AC4) */}
        {event.reviewerFeedback && (
          <div className="bg-orange-50 border border-orange-200 rounded-md p-3 text-sm">
            <div className="flex items-center gap-1 font-medium text-orange-800 mb-1">
              <AlertTriangle className="w-4 h-4" />
              {t('speakerPortal.dashboard.revisionRequested')}
            </div>
            <p className="text-orange-700">{event.reviewerFeedback}</p>
          </div>
        )}

        {/* Deadlines */}
        <div className="flex flex-wrap gap-3 text-sm">
          {event.responseDeadline && event.workflowState === 'INVITED' && (
            <span
              className={`flex items-center gap-1 px-2 py-1 rounded ${urgencyColors[getDeadlineUrgency(event.responseDeadline)]}`}
            >
              <Clock className="w-3.5 h-3.5" />
              {t('speakerPortal.dashboard.responseBy')}: {event.responseDeadline}
            </span>
          )}
          {event.contentDeadline && (
            <span
              className={`flex items-center gap-1 px-2 py-1 rounded ${urgencyColors[getDeadlineUrgency(event.contentDeadline)]}`}
            >
              <Clock className="w-3.5 h-3.5" />
              {t('speakerPortal.dashboard.contentBy')}: {event.contentDeadline}
            </span>
          )}
        </div>

        {/* Organizer contact (AC5) */}
        {event.organizerName && (
          <div className="text-sm text-gray-600 flex items-center gap-1">
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
        <div className="flex flex-wrap gap-2 pt-2 border-t border-gray-100">
          {event.respondUrl && (
            <Link
              to={`${event.respondUrl}?token=${token}`}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md bg-blue-600 text-white hover:bg-blue-700"
            >
              {t('speakerPortal.dashboard.respond')}
              <ChevronRight className="w-3.5 h-3.5" />
            </Link>
          )}
          <Link
            to={`${event.profileUrl}?token=${token}`}
            className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
          >
            <User className="w-3.5 h-3.5" />
            {t('speakerPortal.dashboard.updateProfile')}
          </Link>
          {event.contentUrl && (
            <Link
              to={`${event.contentUrl}?token=${token}`}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
            >
              <FileText className="w-3.5 h-3.5" />
              {t('speakerPortal.dashboard.submitContent')}
            </Link>
          )}
        </div>
      </div>
    </Card>
  );
}

function PastEventCard({ event }: { event: DashboardPastEvent }) {
  return (
    <Card className="p-4 mb-3">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <h3 className="text-base font-medium text-gray-900">{event.eventTitle}</h3>
          <div className="flex items-center gap-3 text-sm text-gray-500 mt-0.5">
            <span className="flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5" />
              {event.eventDate}
            </span>
            {event.sessionTitle && <span>{event.sessionTitle}</span>}
          </div>
        </div>
        <div className="text-sm text-gray-500">
          {event.hasMaterial ? (
            <span className="flex items-center gap-1 text-green-600">
              <FileText className="w-4 h-4" />
              {event.materialFileName}
            </span>
          ) : (
            <span className="text-gray-400">No materials</span>
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
      <div className="min-h-screen bg-gray-50 py-8 px-4">
        <div className="max-w-3xl mx-auto">
          {/* Loading state (AC8) */}
          {pageState === 'loading' && (
            <div className="flex flex-col items-center justify-center py-20">
              <Loader2 className="w-8 h-8 animate-spin text-blue-600 mb-4" />
              <p className="text-gray-500">{t('speakerPortal.dashboard.loading')}</p>
            </div>
          )}

          {/* Error state (AC8) */}
          {pageState === 'error' && (
            <Card className="p-8 text-center">
              <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
              <h2 className="text-xl font-semibold text-gray-900 mb-2">
                {getErrorContent().title}
              </h2>
              <p className="text-gray-600">{getErrorContent().message}</p>
            </Card>
          )}

          {/* Dashboard content */}
          {pageState === 'dashboard' && dashboard && (
            <>
              {/* Header */}
              <div className="mb-8">
                <div className="flex items-center gap-4 mb-2">
                  {dashboard.profilePictureUrl ? (
                    <img
                      src={dashboard.profilePictureUrl}
                      alt={dashboard.speakerName}
                      className="w-16 h-16 rounded-full object-cover"
                    />
                  ) : (
                    <div className="w-16 h-16 rounded-full bg-gray-200 flex items-center justify-center">
                      <User className="w-8 h-8 text-gray-400" />
                    </div>
                  )}
                  <div>
                    <h1 className="text-2xl font-bold text-gray-900">
                      {t('speakerPortal.dashboard.welcome', { name: dashboard.speakerName })}
                    </h1>
                    <p className="text-gray-500">
                      {t('speakerPortal.dashboard.profileComplete', {
                        percent: dashboard.profileCompleteness,
                      })}
                    </p>
                  </div>
                </div>
              </div>

              {/* Upcoming Events (AC2) */}
              <section className="mb-8">
                <h2 className="text-xl font-semibold text-gray-900 mb-4">
                  {t('speakerPortal.dashboard.upcomingEvents')}
                </h2>
                {dashboard.upcomingEvents.length === 0 ? (
                  <Card className="p-6 text-center text-gray-500">
                    {t('speakerPortal.dashboard.noUpcomingEvents')}
                  </Card>
                ) : (
                  dashboard.upcomingEvents.map((event) => (
                    <UpcomingEventCard key={event.eventCode} event={event} token={token!} />
                  ))
                )}
              </section>

              {/* Past Events (AC3) */}
              <section>
                <h2 className="text-xl font-semibold text-gray-900 mb-4">
                  {t('speakerPortal.dashboard.pastEvents')}
                </h2>
                {dashboard.pastEvents.length === 0 ? (
                  <Card className="p-6 text-center text-gray-500">
                    {t('speakerPortal.dashboard.noPastEvents')}
                  </Card>
                ) : (
                  dashboard.pastEvents.map((event) => (
                    <PastEventCard key={event.eventCode} event={event} />
                  ))
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
