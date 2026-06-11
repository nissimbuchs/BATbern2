/**
 * ClickablePhotoMarquee — an auto-scrolling photo strip whose photos open a zoomable, swipeable
 * lightbox on click. Shared by the event-page photo strip ({@link EventPhotosMarquee}) and the
 * homepage's "recent event photos" row (TestimonialSection).
 *
 * Tailwind-only (public bundle boundary). The lightbox is lazy-loaded — its chunk (and the YARL
 * CSS) only load the first time a photo is opened. Renders only the inner marquee + lightbox (no
 * outer <section>), so callers control the surrounding layout / full-bleed wrapper.
 */
import { lazy, Suspense, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { InfiniteMarquee } from '@/components/public/Testimonials/InfiniteMarquee';
import { buildCdnImageUrl } from '@/utils/cdnImage';
import type { PhotoLike } from './PhotoLightbox';

const PhotoLightbox = lazy(() => import('./PhotoLightbox'));

interface ClickablePhotoMarqueeProps {
  photos: PhotoLike[];
  direction?: 'left' | 'right';
}

export function ClickablePhotoMarquee({ photos, direction = 'left' }: ClickablePhotoMarqueeProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [index, setIndex] = useState(0);

  if (!photos || photos.length === 0) {
    return null;
  }

  const openAt = (i: number) => {
    setIndex(i);
    setOpen(true);
  };

  // The marquee renders its children twice (seamless loop), so both copies of a given photo
  // share the same index → clicking either opens the correct slide.
  const thumbnails = photos.map((photo, i) => (
    <button
      key={photo.id}
      type="button"
      onClick={() => openAt(i)}
      aria-label={t('archive.photos.viewPhoto')}
      data-testid="marquee-photo"
      className="shrink-0 rounded-lg overflow-hidden focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-2 focus:ring-offset-zinc-900 cursor-zoom-in"
    >
      <img
        src={
          buildCdnImageUrl(photo.displayUrl, { w: 512, h: 384, fit: 'cover' }) ?? photo.displayUrl
        }
        alt={photo.filename || 'BATbern event photo'}
        loading="lazy"
        decoding="async"
        className="object-cover h-48 w-64 transition-transform duration-200 hover:scale-105"
      />
    </button>
  ));

  return (
    <>
      <InfiniteMarquee direction={direction} speed="slow">
        {thumbnails}
      </InfiniteMarquee>

      {open && (
        <Suspense fallback={null}>
          <PhotoLightbox photos={photos} index={index} open={open} onClose={() => setOpen(false)} />
        </Suspense>
      )}
    </>
  );
}
