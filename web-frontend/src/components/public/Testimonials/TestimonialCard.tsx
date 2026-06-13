/**
 * TestimonialCard Component
 *
 * A curated thank-you note (Story 7.7) for the partner marquee: a small 🙏 marker, the note text
 * (given most of the card height), and the author name + company logo on one bottom row. Sized to
 * match PartnerShowcaseCard (w-80 h-48) so it intermingles cleanly. Tailwind-only (public bundle).
 */

import { Card } from '@/components/public/ui/card';
import { buildCdnImageUrl } from '@/utils/cdnImage';

interface TestimonialCardProps {
  name: string;
  quote: string;
  company?: string;
  /** Story 7.7: company logo CloudFront URL — rendered to the right of the author when present. */
  companyLogoUrl?: string;
  /** Story 7.7: accessible label for the 🙏 marker (e.g. localized "Thank you"); not shown as text. */
  badge?: string;
}

export const TestimonialCard = ({
  name,
  quote,
  company,
  companyLogoUrl,
  badge,
}: TestimonialCardProps) => {
  return (
    <Card className="flex-shrink-0 w-80 h-48 p-6 bg-zinc-700 border-zinc-600 hover:border-zinc-500 transition-colors">
      <div className="flex h-full flex-col">
        {/* Note — 🙏 to the left, text filling the rest; gets the bulk of the card height. */}
        <div className="flex flex-1 min-h-0 gap-2">
          {badge && (
            <span
              className="text-lg leading-none flex-shrink-0"
              role="img"
              aria-label={badge}
              title={badge}
            >
              🙏
            </span>
          )}
          <p className="overflow-hidden text-sm text-zinc-300 italic line-clamp-5">"{quote}"</p>
        </div>

        {/* Author (left) + company logo/name (right) on one row. */}
        <div className="mt-3 flex items-center justify-between gap-3">
          <p className="text-sm font-medium text-zinc-100 truncate">{name}</p>
          {companyLogoUrl ? (
            <img
              src={buildCdnImageUrl(companyLogoUrl, { h: 64, fit: 'inside' }) ?? companyLogoUrl}
              alt={company ?? ''}
              loading="lazy"
              className="max-h-6 max-w-[7rem] flex-shrink-0 object-contain"
            />
          ) : company ? (
            <p className="text-xs text-zinc-400 truncate">{company}</p>
          ) : null}
        </div>
      </div>
    </Card>
  );
};
