/**
 * InfiniteMarquee Component
 * Creates an infinite scrolling marquee effect
 */

import { ReactNode } from 'react';

interface InfiniteMarqueeProps {
  children: ReactNode;
  direction?: 'left' | 'right';
  speed?: 'slow' | 'normal' | 'fast';
}

export const InfiniteMarquee = ({
  children,
  direction = 'left',
  speed = 'slow',
}: InfiniteMarqueeProps) => {
  const speedDuration = {
    slow: '60s',
    normal: '40s',
    fast: '20s',
  };

  const animationDirection = direction === 'left' ? 'scroll-left' : 'scroll-right';

  return (
    <div className="relative overflow-hidden">
      <div
        className="inline-flex w-max"
        style={{
          animation: `${animationDirection} ${speedDuration[speed]} linear infinite`,
        }}
      >
        {/* First set of children */}
        <div className="flex gap-4 shrink-0">{children}</div>
        {/* Duplicate for seamless loop */}
        <div className="flex gap-4 shrink-0 ml-4">{children}</div>
      </div>

      <style>{`
        @keyframes scroll-left {
          0% {
            transform: translateX(0);
          }
          100% {
            transform: translateX(-50%);
          }
        }

        @keyframes scroll-right {
          0% {
            transform: translateX(-50%);
          }
          100% {
            transform: translateX(0);
          }
        }
      `}</style>
    </div>
  );
};
