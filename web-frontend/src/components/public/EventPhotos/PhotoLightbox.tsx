/**
 * PhotoLightbox — full-screen zoomable photo viewer for the event-photos marquee.
 *
 * Wraps `yet-another-react-lightbox` (MIT) with the Zoom + Counter plugins: tap/click to open,
 * pinch / double-tap / scroll to zoom, prev/next arrow buttons, swipe, and keyboard (←/→/Esc)
 * all come for free, with a focus trap + body-scroll-lock + ARIA handled by the lib.
 *
 * Bundle boundary: this file (and the YARL CSS it imports) is loaded LAZILY by
 * {@link EventPhotosMarquee} only when a photo is first opened, so it never enters the public
 * homepage's critical bundle (no MUI here — Tailwind-only public surface).
 *
 * Images are served through the CDN resize Lambda at a viewer-appropriate width (WebP), so the
 * lightbox fetches a sensibly sized variant rather than the raw origin. (No responsive `srcSet`:
 * YARL's slide srcSet requires per-entry pixel height, which we don't know for arbitrary-aspect
 * event photos — a single CDN-resized width is correct and keeps bytes down.)
 */
import Lightbox from 'yet-another-react-lightbox';
import Zoom from 'yet-another-react-lightbox/plugins/zoom';
import Counter from 'yet-another-react-lightbox/plugins/counter';
import 'yet-another-react-lightbox/styles.css';
import 'yet-another-react-lightbox/plugins/counter.css';
import { buildCdnImageUrl } from '@/utils/cdnImage';

/** Minimal photo shape (decoupled from the generated EventPhotoResponse). */
export interface PhotoLike {
  id: string;
  displayUrl: string;
  filename?: string | null;
}

interface PhotoLightboxProps {
  photos: PhotoLike[];
  index: number;
  open: boolean;
  onClose: () => void;
}

// Full-screen viewer width — the CDN resizer emits WebP at this width.
const VIEWER_WIDTH = 1600;

export default function PhotoLightbox({ photos, index, open, onClose }: PhotoLightboxProps) {
  const slides = photos.map((photo) => ({
    src: buildCdnImageUrl(photo.displayUrl, { w: VIEWER_WIDTH }) ?? photo.displayUrl,
    alt: photo.filename || 'BATbern event photo',
  }));

  return (
    <Lightbox
      open={open}
      close={onClose}
      index={index}
      slides={slides}
      plugins={[Zoom, Counter]}
      // Loop only makes sense with >1 photo; harmless otherwise.
      carousel={{ finite: photos.length <= 1 }}
      zoom={{ maxZoomPixelRatio: 3, doubleTapDelay: 250 }}
    />
  );
}
