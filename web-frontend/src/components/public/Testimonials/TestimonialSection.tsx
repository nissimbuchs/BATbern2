/**
 * TestimonialSection Component
 *
 * First row: real recent event photos (shown only when there are enough; no fake fallback).
 * Second row: the partner showcase marquee, with curated thank-you notes (Story 7.7) intermingled
 * — every other card a real attendee thank-you (quote + first name + company logo), drawn from a
 * global, organizer-curated, random pool. Degrades gracefully to partners-only when no notes are
 * featured.
 */

import { useTranslation } from 'react-i18next';
import { TestimonialCard } from './TestimonialCard';
import { InfiniteMarquee } from './InfiniteMarquee';
import { ClickablePhotoMarquee } from '@/components/public/EventPhotos/ClickablePhotoMarquee';
import { PartnerShowcaseCard } from '../Partners';
import { usePublicPartners } from '@/hooks/usePublicPartners';
import { useRecentEventPhotos } from '@/hooks/useRecentEventPhotos';
import { useFeaturedThanks } from '@/hooks/useFeaturedThanks/useFeaturedThanks';

interface TestimonialSectionProps {
  /** When true, suppresses the photo row (e.g. when the caller already shows event photos) */
  skipPhotoRow?: boolean;
}

type PartnerEntry = NonNullable<ReturnType<typeof usePublicPartners>['data']>['data'][number];
type ThanksEntry = NonNullable<ReturnType<typeof useFeaturedThanks>['data']>[number];

type MarqueeItem =
  | { kind: 'partner'; key: string; partner: PartnerEntry }
  | { kind: 'thanks'; key: string; thanks: ThanksEntry };

/**
 * Interleave partners and thank-you cards so every other card is a thank-you. The longer list's
 * remainder is appended — so partners-only (no notes featured) degrades to a plain partner row.
 */
function interleave(partners: PartnerEntry[], thanks: ThanksEntry[]): MarqueeItem[] {
  const out: MarqueeItem[] = [];
  const max = Math.max(partners.length, thanks.length);
  for (let i = 0; i < max; i++) {
    if (i < partners.length) {
      out.push({ kind: 'partner', key: `p-${partners[i].companyName}-${i}`, partner: partners[i] });
    }
    if (i < thanks.length) {
      out.push({ kind: 'thanks', key: `t-${thanks[i].eventCode}-${i}`, thanks: thanks[i] });
    }
  }
  return out;
}

export const TestimonialSection = ({ skipPhotoRow = false }: TestimonialSectionProps) => {
  const { t } = useTranslation('events');

  // First row: real event photos (no fake-testimonial fallback — Story 7.7 retired it).
  const { data: recentPhotos } = useRecentEventPhotos(20, 5);
  const hasEnoughPhotos = (recentPhotos?.length ?? 0) >= 3;

  // Second row: partners intermingled with curated thank-you notes.
  const { data: partnersData } = usePublicPartners();
  const partners = partnersData?.data || [];
  const { data: featuredThanks } = useFeaturedThanks();
  const items = interleave(partners, featuredThanks ?? []);

  return (
    <section className="py-16 relative left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] w-screen overflow-hidden">
      <div className="space-y-6">
        {/* First row: real event photos — suppressed when the caller already shows event photos */}
        {!skipPhotoRow && hasEnoughPhotos && (
          <ClickablePhotoMarquee photos={recentPhotos!} direction="left" />
        )}

        {/* Second row: partner showcase + intermingled curated thank-you notes, scrolling right */}
        {items.length > 0 && (
          <InfiniteMarquee direction="right" speed="slow">
            {items.map((item) =>
              item.kind === 'partner' ? (
                <PartnerShowcaseCard
                  key={item.key}
                  companyName={item.partner.company?.displayName || item.partner.companyName}
                  logoUrl={item.partner.company?.logoUrl}
                  partnershipLevel={
                    item.partner.partnershipLevel as
                      | 'STRATEGIC'
                      | 'PLATINUM'
                      | 'GOLD'
                      | 'SILVER'
                      | 'BRONZE'
                  }
                  partnershipStartDate={item.partner.partnershipStartDate}
                  website={item.partner.company?.website}
                />
              ) : (
                <TestimonialCard
                  key={item.key}
                  name={item.thanks.thankedByFirstName || ''}
                  quote={item.thanks.note || ''}
                  company={item.thanks.thankedByCompanyName || undefined}
                  companyLogoUrl={item.thanks.thankedByCompanyLogoUrl || undefined}
                  badge={t('thanks.marquee.badge')}
                />
              )
            )}
          </InfiniteMarquee>
        )}
      </div>
    </section>
  );
};
