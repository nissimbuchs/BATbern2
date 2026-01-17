/**
 * EventSchema Component (Story 4.1.8)
 * JSON-LD structured data for Schema.org Event type
 * Improves search engine rich results display
 */

import { Helmet } from 'react-helmet-async';

export interface EventSchemaProps {
  name: string;
  description: string;
  startDate: string; // ISO 8601 format
  endDate?: string; // ISO 8601 format
  eventStatus?: 'EventScheduled' | 'EventPostponed' | 'EventCancelled' | 'EventRescheduled';
  eventAttendanceMode?:
    | 'OfflineEventAttendanceMode'
    | 'OnlineEventAttendanceMode'
    | 'MixedEventAttendanceMode';
  venue?: {
    name: string;
    address: string;
    city: string;
    postalCode?: string;
    country?: string;
    latitude?: number;
    longitude?: number;
  };
  image?: string;
  url: string;
  offers?: {
    price: string | number;
    priceCurrency: string;
    availability?: string;
    url?: string;
  };
  organizer?: {
    name: string;
    url?: string;
  };
  performer?: Array<{
    type: 'Person' | 'Organization';
    name: string;
    url?: string;
  }>;
}

export const EventSchema = ({
  name,
  description,
  startDate,
  endDate,
  eventStatus = 'EventScheduled',
  eventAttendanceMode = 'OfflineEventAttendanceMode',
  venue,
  image,
  url,
  offers,
  organizer,
  performer,
}: EventSchemaProps) => {
  const schema = {
    '@context': 'https://schema.org',
    '@type': 'Event',
    name,
    description,
    startDate,
    ...(endDate && { endDate }),
    eventStatus: `https://schema.org/${eventStatus}`,
    eventAttendanceMode: `https://schema.org/${eventAttendanceMode}`,
    ...(image && { image }),
    url,
    ...(venue && {
      location: {
        '@type': 'Place',
        name: venue.name,
        address: {
          '@type': 'PostalAddress',
          streetAddress: venue.address,
          addressLocality: venue.city,
          ...(venue.postalCode && { postalCode: venue.postalCode }),
          addressCountry: venue.country || 'CH',
        },
        ...(venue.latitude &&
          venue.longitude && {
            geo: {
              '@type': 'GeoCoordinates',
              latitude: venue.latitude,
              longitude: venue.longitude,
            },
          }),
      },
    }),
    ...(offers && {
      offers: {
        '@type': 'Offer',
        price: offers.price.toString(),
        priceCurrency: offers.priceCurrency,
        availability: offers.availability || 'https://schema.org/InStock',
        ...(offers.url && { url: offers.url }),
      },
    }),
    ...(organizer && {
      organizer: {
        '@type': 'Organization',
        name: organizer.name,
        ...(organizer.url && { url: organizer.url }),
      },
    }),
    ...(performer &&
      performer.length > 0 && {
        performer: performer.map((p) => ({
          '@type': p.type,
          name: p.name,
          ...(p.url && { url: p.url }),
        })),
      }),
  };

  return (
    <Helmet>
      <script type="application/ld+json">{JSON.stringify(schema)}</script>
    </Helmet>
  );
};
