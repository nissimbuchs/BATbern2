/**
 * CommitteeSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 * Story 10.8b: Staggered fly-in per organizer card (AC #10)
 *
 * AC #9: All active organizers with photo, name, company logo, email — no bio.
 * AC #10: Each card animates: y: 30→0, opacity: 0→1, delay = index × 0.12s
 *
 * Uses OrganizerDisplay (same component as public /about page) with showBio={false}.
 */
import { type JSX } from 'react';
import { motion } from 'framer-motion';
import type { User } from '@/types/user.types';
import { OrganizerDisplay } from '@/components/public/About/OrganizerDisplay';

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
        height: '100vh',
        padding: '3rem',
        color: '#ffffff',
      }}
    >
      <h2
        style={{
          fontSize: '3rem',
          fontWeight: 700,
          marginBottom: '3rem',
          color: '#4f9cf9',
        }}
      >
        BATbern OK
      </h2>

      {/* 2-column card grid — staggered fly-in animation (AC #10) */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(400px, 560px))',
          gap: '1.5rem',
          maxWidth: '1200px',
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
              borderRadius: '12px',
              padding: '1.25rem 1.5rem',
            }}
          >
            <OrganizerDisplay organizer={org} showBio={false} />
          </motion.div>
        ))}
      </div>
    </div>
  );
}
