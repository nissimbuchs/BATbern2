/**
 * EventPhotosMarquee — the full-bleed event-photo strip on the public event page. Each photo
 * opens a zoomable, swipeable lightbox (see {@link ClickablePhotoMarquee}).
 *
 * Tailwind-only (public bundle boundary). Owns the full-bleed <section> wrapper; the clickable
 * marquee + lazy lightbox live in the shared ClickablePhotoMarquee.
 */
import { ClickablePhotoMarquee } from './ClickablePhotoMarquee';
import type { PhotoLike } from './PhotoLightbox';

interface EventPhotosMarqueeProps {
  photos: PhotoLike[];
}

export function EventPhotosMarquee({ photos }: EventPhotosMarqueeProps) {
  if (!photos || photos.length === 0) {
    return null;
  }

  return (
    <section className="relative left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] w-screen overflow-hidden py-6 mt-12">
      <ClickablePhotoMarquee photos={photos} />
    </section>
  );
}
