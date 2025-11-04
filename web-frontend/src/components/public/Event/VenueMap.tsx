/**
 * VenueMap Component (Story 4.1.4)
 * Displays venue location with embedded Google Maps and directions link
 * Uses address-based search when coordinates are not available
 */

import { Button } from '@/components/public/ui/button';
import { MapPin, Navigation } from 'lucide-react';
import type { Venue } from '@/types/event.types';
import { useTranslation } from 'react-i18next';

interface VenueMapProps {
  venue: Venue;
}

export const VenueMap = ({ venue }: VenueMapProps) => {
  const { t } = useTranslation('events');

  // Generate Google Maps URLs using address-based search
  const getMapUrl = (): string | null => {
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

    // If no API key, we can still use iframe embed without API key for basic maps
    if (!apiKey) {
      console.warn('VITE_GOOGLE_MAPS_API_KEY not configured, using basic embed');
      // Use basic Google Maps embed with q parameter (doesn't require API key)
      const query = encodeURIComponent(`${venue.name}, ${venue.address}`);
      return `https://maps.google.com/maps?q=${query}&output=embed`;
    }

    // Use Google Maps Embed API with place search
    const query = encodeURIComponent(`${venue.name}, ${venue.address}`);
    return `https://www.google.com/maps/embed/v1/place?key=${apiKey}&q=${query}&zoom=15`;
  };

  const getDirectionsUrl = (): string => {
    // Address-based directions
    const query = encodeURIComponent(`${venue.name}, ${venue.address}`);
    return `https://www.google.com/maps/search/?api=1&query=${query}`;
  };

  const mapUrl = getMapUrl();

  return (
    <div className="py-12">
      <h2 className="text-3xl font-light mb-8 text-zinc-100">{t('public.venue.title')}</h2>

      <div className="bg-zinc-900/50 border border-zinc-800 rounded-lg overflow-hidden">
        {/* Map embed */}
        {mapUrl ? (
          <div className="w-full h-[400px] bg-zinc-800">
            <iframe
              src={mapUrl}
              width="100%"
              height="400"
              style={{ border: 0 }}
              allowFullScreen
              loading="lazy"
              referrerPolicy="no-referrer-when-downgrade"
              title={`Map showing ${venue.name}`}
            />
          </div>
        ) : (
          <div className="w-full h-[400px] bg-zinc-800 flex items-center justify-center">
            <div className="text-center text-zinc-500">
              <MapPin className="h-12 w-12 mx-auto mb-2" />
              <p>{t('public.venue.mapNotAvailable')}</p>
            </div>
          </div>
        )}

        {/* Venue details */}
        <div className="p-6 space-y-4">
          <div>
            <h3 className="text-xl font-light text-zinc-100 mb-2">{venue.name}</h3>
            <p className="text-zinc-400 flex items-start gap-2">
              <MapPin className="h-5 w-5 flex-shrink-0 mt-0.5" />
              <span>{venue.address}</span>
            </p>
          </div>

          <Button
            asChild
            variant="outline"
            className="w-full sm:w-auto border-zinc-800 hover:border-blue-400 hover:text-blue-400"
          >
            <a
              href={getDirectionsUrl()}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2"
            >
              <Navigation className="h-4 w-4" />
              {t('public.venue.getDirections')}
            </a>
          </Button>
        </div>
      </div>
    </div>
  );
};
