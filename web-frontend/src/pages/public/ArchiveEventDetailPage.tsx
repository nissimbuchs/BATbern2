/**
 * ArchiveEventDetailPage Component (Story 4.2 - Task 2b)
 *
 * Archive event detail page with full session list and speakers
 * No logistics (registration, venue details) - content focus only
 */

import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { PublicLayout } from '@/components/public/PublicLayout';
import { eventApiClient } from '@/services/eventApiClient';
import type { EventDetailUI, SessionUI, SpeakerUI } from '@/types/event.types';

// Simple OpenGraph mock component (to be replaced with actual SEO component)
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function OpenGraphTags({
  title: _title,
  description: _description,
}: {
  title: string;
  description: string;
}) {
  return null; // Meta tags would be injected in <head> via react-helmet or similar
}

export default function ArchiveEventDetailPage() {
  const { eventCode } = useParams<{ eventCode: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();

  // Fetch event details (cast to EventDetailUI for extended properties)
  const {
    data: event,
    isLoading,
    isError,
  } = useQuery<EventDetailUI>({
    queryKey: ['event', eventCode],
    queryFn: () => eventApiClient.getEvent(eventCode!) as Promise<EventDetailUI>,
    enabled: !!eventCode,
  });

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

  return (
    <PublicLayout>
      {event && (
        <OpenGraphTags
          title={event.title}
          description={event.description || `${event.title} - BATbern Archive`}
        />
      )}

      <div className="container mx-auto px-4 py-8">
        {/* Loading State */}
        {isLoading && <div className="text-center py-12 text-gray-600">Loading...</div>}

        {/* Error State */}
        {isError && (
          <div className="text-center py-12">
            <div className="text-red-600 mb-4">{t('archive.errors.loadFailed')}</div>
            <Link to="/archive" className="text-blue-600 hover:text-blue-800">
              {t('archive.detail.backToArchive')}
            </Link>
          </div>
        )}

        {/* Event Details */}
        {event && (
          <>
            {/* Back Button */}
            <button
              onClick={() => navigate('/archive')}
              className="mb-6 text-blue-600 hover:text-blue-800 flex items-center gap-2"
            >
              ← {t('archive.detail.backToArchive')}
            </button>

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

              {/* Topic Badge */}
              {event.topic && (
                <span className="inline-block px-3 py-1 text-sm font-medium bg-blue-100 text-blue-800 rounded mb-3">
                  {event.topic}
                </span>
              )}

              {/* Title */}
              <h1 className="text-4xl font-bold text-gray-900 mb-3">{event.title}</h1>

              {/* Date */}
              <div className="text-lg text-gray-600 mb-4">{formattedDate}</div>

              {/* Description */}
              {event.description && (
                <p className="text-gray-700 leading-relaxed">{event.description}</p>
              )}
            </div>

            {/* Sessions Section */}
            <div className="mb-12">
              <h2 className="text-2xl font-bold text-gray-900 mb-6">
                {t('archive.detail.sessions')}
              </h2>

              {event.sessions && event.sessions.length > 0 ? (
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
              ) : (
                <div className="text-gray-600">No sessions available</div>
              )}
            </div>

            {/* Speakers Grid */}
            {allSpeakers.length > 0 && (
              <div>
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
                          <span className="text-2xl text-gray-500">
                            {speaker.fullName?.charAt(0) || '?'}
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
              </div>
            )}
          </>
        )}
      </div>
    </PublicLayout>
  );
}
