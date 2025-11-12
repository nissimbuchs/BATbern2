/**
 * PartnerShowcaseCard Component
 * Displays partner logo prominently for homepage showcase
 * Shows tier and start date, links to company website
 */

import { Card } from '@/components/public/ui/card';
import { format } from 'date-fns';

interface PartnerShowcaseCardProps {
  companyName: string;
  logoUrl?: string;
  partnershipLevel: 'STRATEGIC' | 'PLATINUM' | 'GOLD' | 'SILVER' | 'BRONZE';
  partnershipStartDate: string;
  website?: string;
}

// Tier emoji and color mapping
const TIER_CONFIG = {
  STRATEGIC: { emoji: '🏆', color: 'text-yellow-400', label: 'Strategic' },
  PLATINUM: { emoji: '💎', color: 'text-gray-300', label: 'Platinum' },
  GOLD: { emoji: '🥇', color: 'text-yellow-400', label: 'Gold' },
  SILVER: { emoji: '🥈', color: 'text-gray-400', label: 'Silver' },
  BRONZE: { emoji: '🥉', color: 'text-orange-600', label: 'Bronze' },
};

export const PartnerShowcaseCard = ({
  companyName,
  logoUrl,
  partnershipLevel,
  partnershipStartDate,
  website,
}: PartnerShowcaseCardProps) => {
  const tierConfig = TIER_CONFIG[partnershipLevel];
  const initials = companyName.substring(0, 2).toUpperCase();
  const formattedDate = format(new Date(partnershipStartDate), 'MMM yyyy');

  const handleClick = () => {
    if (website) {
      window.open(website, '_blank', 'noopener,noreferrer');
    }
  };

  return (
    <Card
      className={`flex-shrink-0 w-80 h-48 p-4 bg-zinc-900 border-zinc-800 hover:border-zinc-700 transition-colors ${
        website ? 'cursor-pointer' : 'cursor-default opacity-70'
      }`}
      onClick={handleClick}
    >
      <div className="flex flex-col items-center justify-center h-full gap-2">
        {/* Logo */}
        <div className="flex-1 w-full max-h-32 flex items-center justify-center">
          {logoUrl ? (
            <img src={logoUrl} alt={companyName} className="max-w-full max-h-32 object-contain" />
          ) : (
            <div className="w-48 h-24 flex items-center justify-center rounded bg-primary">
              <span className="text-4xl font-semibold text-primary-foreground">{initials}</span>
            </div>
          )}
        </div>

        {/* Tier and Date - Horizontal Layout */}
        <div className="flex items-center gap-2 text-sm">
          <span className={tierConfig.color}>
            {tierConfig.emoji} {tierConfig.label}
          </span>
          <span className="text-zinc-400">since {formattedDate}</span>
        </div>
      </div>
    </Card>
  );
};
