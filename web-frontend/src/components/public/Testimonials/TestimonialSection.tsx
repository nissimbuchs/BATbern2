/**
 * TestimonialSection Component
 * First row: testimonials with infinite marquee animation
 * Second row: partner showcase with logos
 */

import { TestimonialCard } from './TestimonialCard';
import { InfiniteMarquee } from './InfiniteMarquee';
import { PartnerShowcaseCard } from '../Partners';
import { usePublicPartners } from '@/hooks/usePublicPartners';

interface Testimonial {
  id: string;
  name: string;
  quote: string;
  company: string;
  avatar?: string;
}

const testimonials: Testimonial[] = [
  {
    id: '1',
    name: 'Andreas Müller',
    quote:
      'BATbern events are always insightful and well-organized. A must-attend for any architect in the region.',
    company: 'Swisscom AG',
  },
  {
    id: '2',
    name: 'Sarah Schmidt',
    quote:
      'The networking opportunities at BATbern are unmatched. I always leave with new connections and ideas.',
    company: 'Die Schweizerische Post',
  },
  {
    id: '3',
    name: 'Thomas Weber',
    quote:
      'Excellent speakers and cutting-edge topics. BATbern keeps us at the forefront of architectural innovation.',
    company: 'BLS AG',
  },
  {
    id: '4',
    name: 'Lisa Zimmermann',
    quote: 'The perfect blend of tradition and innovation. Every event is a learning experience.',
    company: 'SBB CFF FFS',
  },
  {
    id: '5',
    name: 'Michael Keller',
    quote: 'BATbern has become an essential part of our professional development calendar.',
    company: 'BKW Energie AG',
  },
  {
    id: '6',
    name: 'Nina Hoffmann',
    quote: 'The venue, the content, the people - everything about BATbern is first-class.',
    company: 'Informatik Service Center ISC-EJPD',
  },
  {
    id: '7',
    name: 'Daniel Fischer',
    quote:
      'I appreciate how BATbern brings together architects from different specializations and backgrounds.',
    company: 'Mobiliar Versicherungen',
  },
  {
    id: '8',
    name: 'Julia Meier',
    quote: 'The sessions are always relevant and practical. I can apply what I learn immediately.',
    company: 'PostFinance AG',
  },
  {
    id: '9',
    name: 'Martin Schneider',
    quote: 'BATbern events inspire me to think differently about architecture and design.',
    company: 'Universität Bern',
  },
  {
    id: '10',
    name: 'Anna Koch',
    quote:
      'The atmosphere is professional yet welcoming. Perfect for both learning and networking.',
    company: 'Inselspital Bern',
  },
  {
    id: '11',
    name: 'Stefan Bauer',
    quote:
      'Outstanding organization and speaker selection. BATbern sets the standard for professional events.',
    company: 'Gebäudeversicherung Bern',
  },
  {
    id: '12',
    name: 'Claudia Wagner',
    quote: 'I never miss a BATbern event. The insights and connections are invaluable.',
    company: 'Energie Wasser Bern',
  },
  {
    id: '13',
    name: 'Peter Huber',
    quote: 'BATbern brings the architectural community together in a meaningful way.',
    company: 'Kanton Bern',
  },
  {
    id: '14',
    name: 'Sandra Roth',
    quote: 'The quality of content and networking at BATbern is consistently excellent.',
    company: 'Stadt Bern',
  },
  {
    id: '15',
    name: 'Markus Graf',
    quote: 'Every BATbern event offers fresh perspectives and practical knowledge.',
    company: 'Berner Kantonalbank AG',
  },
  {
    id: '16',
    name: 'Elena Berger',
    quote: 'BATbern is where innovation meets tradition. An essential event for Swiss architects.',
    company: 'Valiant Bank AG',
  },
  {
    id: '17',
    name: 'Christian Lang',
    quote: 'The speaker lineup is always impressive, and the topics are highly relevant.',
    company: 'Migros Aare',
  },
  {
    id: '18',
    name: 'Monika Brunner',
    quote: 'BATbern events are professionally run and incredibly valuable for our practice.',
    company: 'Coop Region Bern',
  },
  {
    id: '19',
    name: 'Robert Steiner',
    quote: 'I look forward to BATbern events all year. They never disappoint.',
    company: 'Emch+Berger AG',
  },
  {
    id: '20',
    name: 'Sabrina Frei',
    quote: 'The connections and knowledge I gain at BATbern are invaluable to my work.',
    company: 'Losinger Marazzi AG',
  },
];

export const TestimonialSection = () => {
  // First row: testimonials
  const firstRow = testimonials.slice(0, 10);

  // Second row: partners
  const { data: partnersData } = usePublicPartners();
  const partners = partnersData?.data || [];

  return (
    <section className="py-16 relative left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] w-screen overflow-hidden">
      <div className="space-y-6">
        {/* First row - testimonials scrolling left */}
        <InfiniteMarquee direction="left" speed="slow">
          {firstRow.map((testimonial) => (
            <TestimonialCard
              key={testimonial.id}
              name={testimonial.name}
              quote={testimonial.quote}
              company={testimonial.company}
              avatar={testimonial.avatar}
            />
          ))}
        </InfiniteMarquee>

        {/* Second row - partner showcase scrolling right */}
        {partners.length > 0 && (
          <InfiniteMarquee direction="right" speed="slow">
            {partners.map((partner) => (
              <PartnerShowcaseCard
                key={partner.id}
                companyName={partner.company?.displayName || partner.companyName}
                logoUrl={partner.company?.logoUrl}
                partnershipLevel={
                  partner.partnershipLevel as
                    | 'STRATEGIC'
                    | 'PLATINUM'
                    | 'GOLD'
                    | 'SILVER'
                    | 'BRONZE'
                }
                partnershipStartDate={partner.partnershipStartDate}
                website={partner.company?.website}
              />
            ))}
          </InfiniteMarquee>
        )}
      </div>
    </section>
  );
};
