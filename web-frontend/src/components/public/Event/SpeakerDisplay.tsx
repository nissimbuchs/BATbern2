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
      logo: 'h-12 w-12',
      name: 'text-sm',
      company: 'text-xs',
      initials: 'text-base',
    },
    medium: {
      avatar: 'h-16 w-16',
      logo: 'h-16 w-16',
      name: 'text-base',
      company: 'text-sm',
      initials: 'text-xl',
    },
    large: {
      avatar: 'h-20 w-20',
      logo: 'h-20 w-20',
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

      {/* Company Logo - Right aligned, same size as profile picture */}
      {speaker.company && (
        <div className={`${sizes.logo} rounded flex items-center justify-center flex-shrink-0 p-1`}>
          {logoUrl ? (
            <img
              src={logoUrl}
              alt={`${speaker.company} logo`}
              className="h-full w-full object-contain"
            />
          ) : (
            <Building2 className="h-8 w-8 text-zinc-400" />
          )}
        </div>
      )}
    </div>
  );
};
