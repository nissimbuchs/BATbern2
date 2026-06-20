/**
 * LogoBadge — renders a logo on a white, rounded "chip" so transparent logos stay legible on
 * dark surfaces (presentation mode, live control, dark public sections).
 *
 * Vanessa GitHub #791: speaker/company logos uploaded with a transparent background (Swisscom,
 * Energie Suisse, Adesso on BATbern59) were unreadable against the dark presentation background.
 *
 * Framework-agnostic on purpose (plain <span>/<img> with inline styles, no MUI) so it can be used
 * both in the MUI-free presentation pages and in MUI surfaces (e.g. LiveControl for feedback #5).
 * Sizing is left to the caller via `imgStyle`; padding/radius default to em units (scale with the
 * inherited font-size) and can be overridden via `style` for vw-based layouts like the slides.
 */
import { type CSSProperties, type JSX } from 'react';

interface LogoBadgeProps {
  src: string;
  alt?: string;
  /** Inline style for the <img> — set height / maxWidth here. */
  imgStyle?: CSSProperties;
  /** Inline style for the white wrapper — override padding / borderRadius / background here. */
  style?: CSSProperties;
  className?: string;
}

export function LogoBadge({
  src,
  alt = '',
  imgStyle,
  style,
  className,
}: LogoBadgeProps): JSX.Element {
  return (
    <span
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#ffffff',
        borderRadius: '0.4em',
        padding: '0.3em 0.5em',
        ...style,
      }}
    >
      <img src={src} alt={alt} style={{ objectFit: 'contain', display: 'block', ...imgStyle }} />
    </span>
  );
}

export default LogoBadge;
