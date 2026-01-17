/**
 * SpeakerDisplay Component
 * Reusable component for displaying speaker with profile picture and company logo
 * Used in: SpeakerGrid, SessionCards, EventProgram
 */

import type { SessionSpeaker } from '@/types/event.types';
import { Building2 } from 'lucide-react';
import { useCompany } from '@/hooks/useCompany/useCompany';

interface SpeakerDisplayProps {
  speaker: SessionSpeaker;
  size?: 'small' | 'medium' | 'large';
  showProfilePicture?: boolean;
  className?: string;
}

/**
 * SpeakerDisplay Component
 *
 * Layout:
 * [Profile Picture] [Speaker Name     ] [Company Logo]
 *                   [Company Name      ]
 *
 * - Profile picture: Left-aligned, 80x80 (medium), 56x56 (small)
 * - Speaker info: Middle section with name and company
 * - Company logo: Right-aligned, same size as profile picture
 */
export const SpeakerDisplay = ({
  speaker,
  size = 'medium',
  showProfilePicture = true,
  className = '',
}: SpeakerDisplayProps) => {
  const { data: company } = useCompany(speaker.company || '', {
    expand: ['logo'],
  });

  const logoUrl = company?.logo?.url;

  // Size mappings
  const sizeClasses = {
    small: {
      avatar: 'h-12 w-12',
      logoContainer: 'max-h-12 max-w-24', // Limit logo to 2x height (96px)
      logoImage: 'max-h-12 max-w-full',
      name: 'text-sm',
      company: 'text-xs',
      initials: 'text-base',
    },
    medium: {
      avatar: 'h-16 w-16',
      logoContainer: 'max-h-16 max-w-32', // Limit logo to 2x height (128px)
      logoImage: 'max-h-16 max-w-full',
      name: 'text-base',
      company: 'text-sm',
      initials: 'text-xl',
    },
    large: {
      avatar: 'h-20 w-20',
      logoContainer: 'max-h-20 max-w-40', // Limit logo to 2x height (160px)
      logoImage: 'max-h-20 max-w-full',
      name: 'text-lg',
      company: 'text-base',
      initials: 'text-2xl',
    },
  };

  const sizes = sizeClasses[size];

  return (
    <div className={`flex items-center gap-3 ${className}`}>
      {/* Profile Picture */}
      {showProfilePicture && (
        <div
          className={`${sizes.avatar} rounded-full bg-zinc-800 flex items-center justify-center overflow-hidden flex-shrink-0`}
        >
          {speaker.profilePictureUrl ? (
            <img
              src={speaker.profilePictureUrl}
              alt={`${speaker.firstName} ${speaker.lastName}`}
              className="h-full w-full object-cover"
              width={80}
              height={80}
              loading="lazy"
            />
          ) : (
            <span className={`${sizes.initials} font-light text-zinc-300`}>
              {speaker.firstName[0]}
              {speaker.lastName[0]}
            </span>
          )}
        </div>
      )}

      {/* Speaker Info (flex-grow to take available space) */}
      <div className="flex-1 min-w-0 flex flex-col justify-center">
        <div className={`${sizes.name} font-light text-zinc-100`}>
          {speaker.firstName} {speaker.lastName}
        </div>
        {speaker.company && (
          <div className={`${sizes.company} text-zinc-400 flex items-center gap-1.5`}>
            <Building2 className="h-3 w-3 flex-shrink-0" />
            <span className="truncate">{speaker.company}</span>
          </div>
        )}
      </div>

      {/* Company Logo - Right aligned, max height with flexible width */}
      {speaker.company && (
        <div
          className={`${sizes.logoContainer} flex items-center justify-center flex-shrink-0 p-1`}
        >
          {logoUrl ? (
            <img
              src={logoUrl}
              alt={`${speaker.company} logo`}
              className={`${sizes.logoImage} object-contain`}
              width={128}
              height={64}
              loading="lazy"
            />
          ) : (
            <Building2 className="h-8 w-8 text-zinc-400" />
          )}
        </div>
      )}
    </div>
  );
};
