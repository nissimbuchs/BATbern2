/**
 * ArchiveEventDetailPage Component (Story 4.2 - Task 2b)
 *
 * Archive event detail page with full session list and speakers
 * No logistics (registration, venue details) - content focus only
 */

import { useParams, Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Helmet } from 'react-helmet-async';
import { PublicLayout } from '@/components/public/PublicLayout';
import { OpenGraphTags } from '@/components/SEO/OpenGraphTags';
import { EventDescriptionSection } from '@/components/public/Event/EventDescriptionSection';
import { InfiniteMarquee } from '@/components/public/Testimonials/InfiniteMarquee';
import { eventApiClient } from '@/services/eventApiClient';
import { useEventPhotos } from '@/hooks/useEventPhotos';
import type { EventDetailUI, SessionUI, SpeakerUI } from '@/types/event.types';

export default function ArchiveEventDetailPage() {
  const { eventCode } = useParams<{ eventCode: string }>();
  const location = useLocation();
  const { t } = useTranslation();

  // Preserve query parameters when navigating back
  const backToArchiveUrl = location.search ? `/archive${location.search}` : '/archive';

  // Event photos (Story 10.21 — AC7)
  const { data: photos } = useEventPhotos(eventCode ?? '');

  // Fetch event details (cast to EventDetailUI for extended properties)
  const {
    data: event,
    isLoading,
    isError,
    error,
  } = useQuery<EventDetailUI>({
    queryKey: ['event', eventCode],
    queryFn: () =>
      eventApiClient.getEvent(eventCode!, {
        expand: ['topics', 'sessions', 'speakers'],
      }) as Promise<EventDetailUI>,
    enabled: !!eventCode,
  });

  // Check if error is a 404 (event not found)
  const isNotFound =
    isError &&
    error &&
    typeof error === 'object' &&
    'response' in error &&
    typeof error.response === 'object' &&
    error.response &&
    'status' in error.response &&
    (error.response as { status: number }).status === 404;

  // Format date
  const formattedDate = event
    ? new Date(event.date).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : '';

  // Format file size (bytes to MB)
  const formatFileSize = (bytes?: number) => {
    if (!bytes) return '';
    const mb = bytes / 1000000;
    return `${mb.toFixed(1)} MB`;
  };

  // Get initials from full name (e.g., "John Doe" -> "JD")
  const getInitials = (fullName: string) => {
    const parts = fullName.trim().split(/\s+/);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    // First and last name initials
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
  };

  // Collect all unique speakers (cast to SpeakerUI for extended properties)
  const allSpeakers =
    (event?.sessions as SessionUI[] | undefined)
      ?.flatMap((session) => session.speakers || [])
      .filter(
        (speaker, index, arr) =>
          arr.findIndex((s) => (s as SpeakerUI).speakerId === (speaker as SpeakerUI).speakerId) ===
          index
      )
      .map((speaker) => speaker as SpeakerUI) || [];

  // SEO metadata
  const pageUrl = `${window.location.origin}/archive/${eventCode}`;
  const eventTitle = event?.title || 'Event Details';
  const eventDescription =
    event?.description ||
    `Explore ${event?.sessions?.length || 0} presentations from ${eventTitle}`;
  const eventImage =
    event?.themeImageUrl || 'https://cdn.batbern.ch/assets/default-event-cover.jpg';

  // JSON-LD structured data for event
  const eventStructuredData = event
    ? {
        '@context': 'https://schema.org',
        '@type': 'Event',
        name: event.title,
        description: event.description || eventTitle,
        image: eventImage,
        startDate: event.date,
        endDate: event.date,
        eventStatus: 'https://schema.org/EventScheduled',
        eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
        location: event.venueName
          ? {
              '@type': 'Place',
              name: event.venueName,
            }
          : undefined,
        organizer: {
          '@type': 'Organization',
          name: 'BATbern',
          url: window.location.origin,
        },
        performer:
          allSpeakers.length > 0
            ? allSpeakers.map((speaker) => ({
                '@type': 'Person',
                name: speaker.fullName,
                ...(speaker.companyName && {
                  affiliation: {
                    '@type': 'Organization',
                    name: speaker.companyName,
                  },
                }),
              }))
            : undefined,
      }
    : null;

  return (
    <PublicLayout>
      {event && (
        <>
          <OpenGraphTags
            title={eventTitle}
            description={eventDescription}
            url={pageUrl}
            image={eventImage}
            type="website"
          />

          {/* JSON-LD Structured Data for Event */}
          <Helmet>
            <script type="application/ld+json">{JSON.stringify(eventStructuredData)}</script>
          </Helmet>
        </>
      )}

      <div className="container mx-auto px-4 py-8">
        {/* Back Button */}
        <Link to={backToArchiveUrl} className="mb-6 text-blue-600 hover:text-blue-800 inline-block">
          {t('archive.detail.backToArchive')}
        </Link>

        {/* Loading State */}
        {isLoading && (
          <div className="text-center py-12 text-gray-600" role="status">
            Loading...
          </div>
        )}

        {/* Error State */}
        {isError && (
          <div className="text-center py-12">
            <div className="text-red-600 mb-4">
              {isNotFound ? t('archive.errors.notFound') : t('archive.errors.loadFailed')}
            </div>
          </div>
        )}

        {/* Event Details */}
        {event && (
          <>
            {/* Event Header */}
            <div className="mb-8">
              {/* Theme Image */}
              {event.themeImageUrl && (
                <img
                  src={event.themeImageUrl}
                  alt={event.title}
                  className="w-full h-64 object-cover rounded-lg mb-6"
                />
              )}

              {/* Topic Badge - Story BAT-109: Use expanded topic object */}
              {event.topic && typeof event.topic === 'object' ? (
                <span className="inline-block px-3 py-1 text-sm font-medium bg-blue-100 text-blue-800 rounded mb-3">
                  {event.topic.name}
                </span>
              ) : event.topicCode ? (
                <span className="inline-block px-3 py-1 text-sm font-medium bg-blue-100 text-blue-800 rounded mb-3">
                  {event.topicCode}
                </span>
              ) : null}

              {/* Title */}
              <h1 className="text-4xl font-bold text-gray-900 mb-3">{event.title}</h1>

              {/* Date */}
              <div className="text-lg text-gray-600 mb-4">{formattedDate}</div>
            </div>

            {/* Event Description Section (Story 10.23) — positioned after header, before sessions */}
            <EventDescriptionSection description={event.description} />

            {/* Sessions Section */}
            {event.sessions && event.sessions.length > 0 && (
              <div className="mb-12">
                <h2 className="text-2xl font-bold text-gray-900 mb-6">
                  {t('archive.detail.sessions')}
                </h2>

                <div className="space-y-6">
                  {(event.sessions as SessionUI[]).map((session) => (
                    <div
                      key={session.sessionId}
                      className="bg-white border border-gray-200 rounded-lg p-6"
                    >
                      {/* Session Title and Time */}
                      <div className="flex justify-between items-start mb-3">
                        <h3 className="text-xl font-semibold text-gray-900">{session.title}</h3>
                        {session.startTime && session.endTime && (
                          <span className="text-sm text-gray-600 whitespace-nowrap ml-4">
                            {session.startTime} - {session.endTime}
                          </span>
                        )}
                      </div>

                      {/* Session Description */}
                      {session.description && (
                        <p className="text-gray-700 mb-3">{session.description}</p>
                      )}

                      {/* Speakers */}
                      {session.speakers && session.speakers.length > 0 && (
                        <div className="mb-3">
                          <div className="text-sm text-gray-600">
                            {(session.speakers as SpeakerUI[]).map((speaker, idx) => (
                              <span key={speaker.speakerId}>
                                <span className="font-medium">{speaker.fullName}</span>
                                {speaker.companyName && (
                                  <span className="text-gray-500"> ({speaker.companyName})</span>
                                )}
                                {idx < session.speakers!.length - 1 && ', '}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Presentation Download */}
                      {session.presentationUrl && (
                        <a
                          href={session.presentationUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-2 text-blue-600 hover:text-blue-800 text-sm font-medium"
                        >
                          <span>📥</span>
                          {t('archive.detail.downloadPresentation')}
                          {session.presentationSize && (
                            <span className="text-gray-500">
                              ({formatFileSize(session.presentationSize)})
                            </span>
                          )}
                        </a>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Speakers Grid */}
            {allSpeakers.length > 0 && (
              <section>
                <h2 className="text-2xl font-bold text-gray-900 mb-6">
                  {t('archive.detail.speakers')}
                </h2>
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
                  {allSpeakers.map((speaker) => (
                    <div key={speaker.speakerId} className="text-center">
                      {speaker.photoUrl ? (
                        <img
                          src={speaker.photoUrl}
                          alt={speaker.fullName}
                          className="w-24 h-24 rounded-full mx-auto mb-3 object-cover"
                        />
                      ) : (
                        <div className="w-24 h-24 rounded-full bg-gray-200 mx-auto mb-3 flex items-center justify-center">
                          <span className="text-2xl font-medium text-gray-600">
                            {getInitials(speaker.fullName || '')}
                          </span>
                        </div>
                      )}
                      <div className="font-medium text-gray-900">
                        {speaker.fullName || 'Unknown'}
                      </div>
                      {speaker.companyName && (
                        <div className="text-sm text-gray-600">{speaker.companyName}</div>
                      )}
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* Event Photos Marquee (Story 10.21 — AC7): hidden when 0 photos */}
            {photos && photos.length > 0 && (
              <section className="relative left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] w-screen overflow-hidden py-6">
                <h2 className="text-2xl font-bold text-gray-900 mb-4 px-4 md:px-8">
                  {t('archive.detail.photos', 'Photos')}
                </h2>
                <InfiniteMarquee direction="left" speed="slow">
                  {photos.map((photo) => (
                    <img
                      key={photo.id}
                      src={photo.displayUrl}
                      alt={photo.filename || 'BATbern event photo'}
                      className="rounded-lg object-cover h-48 w-64 shrink-0"
                    />
                  ))}
                </InfiniteMarquee>
              </section>
            )}
          </>
        )}
      </div>
    </PublicLayout>
  );
}
