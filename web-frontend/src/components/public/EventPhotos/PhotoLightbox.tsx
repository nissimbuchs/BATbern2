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
 * Images: the lightbox serves the RAW origin (full resolution). The CDN resize Lambda 503s for
 * high-megapixel sources above ~1200px wide (verified on BATbern58 — w=1600 → 503, w=512 thumbnail
 * → 200), so requesting a large resized variant here would show broken images. Full-res also gives
 * the best zoom quality, and this is an explicit one-at-a-time view (not a perf-critical list), so
 * the larger bytes are acceptable. The marquee thumbnails stay CDN-resized (w=512, reliable).
 * TODO(infra): once the image-resize Lambda handles large sources at viewer widths, switch back to
 * a resized src to cut bytes.
 */
import Lightbox from 'yet-another-react-lightbox';
import Zoom from 'yet-another-react-lightbox/plugins/zoom';
import Counter from 'yet-another-react-lightbox/plugins/counter';
import 'yet-another-react-lightbox/styles.css';
import 'yet-another-react-lightbox/plugins/counter.css';

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

export default function PhotoLightbox({ photos, index, open, onClose }: PhotoLightboxProps) {
  const slides = photos.map((photo) => ({
    src: photo.displayUrl, // raw origin — see file header (resize Lambda 503s at viewer widths)
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
