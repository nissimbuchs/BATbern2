/**
 * OrganizerDisplay Component
 * Reusable component for displaying organizer with profile picture and company logo
 * Used in: About page
 */

import type { User } from '@/types/user.types';
import { Building2 } from 'lucide-react';
import { useCompany } from '@/hooks/useCompany/useCompany';

interface OrganizerDisplayProps {
  organizer: User;
  showBio?: boolean;
  className?: string;
}

/**
 * OrganizerDisplay Component
 *
 * Layout:
 * [Profile Picture] [Organizer Name     ] [Company Logo]
 *                   [Company Name        ]
 *                   [Bio (if showBio)    ]
 */
export const OrganizerDisplay = ({
  organizer,
  showBio = true,
  className = '',
}: OrganizerDisplayProps) => {
  const { data: company } = useCompany(organizer.companyId || '', {
    expand: ['logo'],
  });

  const logoUrl = company?.logo?.url;
  const companyName = company?.displayName || company?.name;

  return (
    <div className={`flex flex-col gap-4 ${className}`}>
      {/* Header with profile and company logo */}
      <div className="flex items-start gap-4">
        {/* Profile Picture */}
        <div className="h-20 w-20 rounded-full bg-zinc-800 flex items-center justify-center overflow-hidden flex-shrink-0">
          {organizer.profilePictureUrl ? (
            <img
              src={organizer.profilePictureUrl}
              alt={`${organizer.firstName} ${organizer.lastName}`}
              className="h-full w-full object-cover"
            />
          ) : (
            <span className="text-2xl font-light text-zinc-300">
              {organizer.firstName[0]}
              {organizer.lastName[0]}
            </span>
          )}
        </div>

        {/* Organizer Info (flex-grow to take available space) */}
        <div className="flex-1 min-w-0 flex flex-col justify-center gap-1">
          <div className="text-lg font-light text-zinc-100">
            {organizer.firstName} {organizer.lastName}
          </div>
          {companyName && (
            <div className="text-sm text-zinc-400 flex items-center gap-1.5">
              <Building2 className="h-3 w-3 flex-shrink-0" />
              <span className="truncate">{companyName}</span>
            </div>
          )}
        </div>

        {/* Company Logo - Right aligned */}
        {companyName && (
          <div
            className="max-h-20 flex items-center justify-center flex-shrink-0 p-1"
            style={{ maxWidth: '120px' }}
          >
            {logoUrl ? (
              <img
                src={logoUrl}
                alt={`${companyName} logo`}
                className="max-h-20 max-w-full object-contain"
              />
            ) : (
              <Building2 className="h-8 w-8 text-zinc-400" />
            )}
          </div>
        )}
      </div>

      {/* Bio */}
      {showBio && organizer.bio && (
        <div className="text-sm text-zinc-400 leading-relaxed">{organizer.bio}</div>
      )}
    </div>
  );
};
