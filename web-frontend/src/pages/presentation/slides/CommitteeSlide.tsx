/**
 * CommitteeSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: Staggered fly-in per organizer card (AC #10)
 *
 * AC #9: All active organizers with photo, name, company logo, email — no bio.
 * AC #10: Each card animates: y: 30→0, opacity: 0→1, delay = index × 0.12s
 *
 * Uses a presentation-specific vw-scaled card instead of OrganizerDisplay
 * (which uses fixed Tailwind px classes unsuitable for 4K projection).
 */
import { type JSX } from 'react';
import { motion } from 'framer-motion';
import type { User } from '@/types/user.types';
import { useCompany } from '@/hooks/useCompany/useCompany';

interface CommitteeSlideProps {
  organizers: User[];
}

export function CommitteeSlide({ organizers }: CommitteeSlideProps): JSX.Element {
  return (
    <div
      style={{
        position: 'relative',
        zIndex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100%',
        padding: '2.5vw',
        color: '#ffffff',
      }}
    >
      <h2
        style={{
          fontSize: '4.5vw',
          fontWeight: 700,
          marginBottom: '3vw',
          color: '#4f9cf9',
        }}
      >
        BATbern OK
      </h2>

      {/* 2-column card grid — staggered fly-in animation (AC #10) */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(30vw, 43vw))',
          gap: '1.8vw',
          maxWidth: '90vw',
          justifyContent: 'center',
          width: '100%',
        }}
      >
        {organizers.map((org, index) => (
          <motion.div
            key={org.id}
            initial={{ y: 30, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ delay: index * 0.12, duration: 0.4 }}
            style={{
              background: 'rgba(255,255,255,0.06)',
              border: '1px solid rgba(255,255,255,0.12)',
              borderRadius: '0.9vw',
              padding: '1.5vw 1.8vw',
            }}
          >
            <OrganizerCard organizer={org} />
          </motion.div>
        ))}
      </div>
    </div>
  );
}

/** vw-scaled organizer card for the presentation canvas. */
function OrganizerCard({ organizer }: { organizer: User }): JSX.Element {
  const { data: company } = useCompany(organizer.companyId || '', { expand: ['logo'] });
  const logoUrl = company?.logo?.url;
  const companyName = company?.displayName || company?.name;
  const initials = `${organizer.firstName?.[0] ?? ''}${organizer.lastName?.[0] ?? ''}`;

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '1.8vw' }}>
      {/* Profile photo */}
      <div
        style={{
          width: '7vw',
          height: '7vw',
          borderRadius: '50%',
          overflow: 'hidden',
          flexShrink: 0,
          background: 'rgba(255,255,255,0.15)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {organizer.profilePictureUrl ? (
          <img
            src={organizer.profilePictureUrl}
            alt={`${organizer.firstName} ${organizer.lastName}`}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <span style={{ fontSize: '2.8vw', color: '#ffffff', fontWeight: 300 }}>{initials}</span>
        )}
      </div>

      {/* Name + company */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{ fontSize: '2.3vw', fontWeight: 600, color: '#ffffff', marginBottom: '0.4vw' }}
        >
          {organizer.firstName} {organizer.lastName}
        </div>
        {companyName && <div style={{ fontSize: '1.7vw', color: '#ffffff' }}>{companyName}</div>}
      </div>

      {/* Company logo */}
      {logoUrl && (
        <img
          src={logoUrl}
          alt={companyName ?? ''}
          style={{ maxHeight: '4vw', maxWidth: '8.5vw', objectFit: 'contain', flexShrink: 0 }}
        />
      )}
    </div>
  );
}
