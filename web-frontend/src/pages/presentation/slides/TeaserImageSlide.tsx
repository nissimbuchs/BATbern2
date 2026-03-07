/**
 * TeaserImageSlide
 * Story 10.22: Event Teaser Images for Moderator Presentation Page
 *
 * AC5: Full-screen slide displaying a teaser image between topic-reveal and agenda-preview.
 * Image fills the viewport with object-fit: cover (letterbox/pillarbox for non-16:9).
 */
import { type JSX } from 'react';

interface TeaserImageSlideProps {
  imageUrl: string;
}

export function TeaserImageSlide({ imageUrl }: TeaserImageSlideProps): JSX.Element {
  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#000',
      }}
    >
      <img
        src={imageUrl}
        alt=""
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'contain',
        }}
      />
    </div>
  );
}
