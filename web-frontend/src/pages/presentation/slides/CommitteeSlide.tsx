/**
 * CommitteeSlide
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * AC #9: All active organizers with photo, name, company — immediate render.
 * Stagger animation deferred to Story 10.8b.
 */
import { type JSX } from 'react';

import type { User } from '@/types/user.types';

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
        Das BATbern-Komitee
      </h2>

      {/* Card grid — no stagger animation (10.8a) */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '2rem',
          justifyContent: 'center',
          maxWidth: '1400px',
        }}
      >
        {organizers.map((org) => {
          const fullName = [org.firstName, org.lastName].filter(Boolean).join(' ');
          return (
            <div
              key={org.id}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '0.75rem',
                width: '160px',
                textAlign: 'center',
              }}
            >
              <div
                style={{
                  width: '100px',
                  height: '100px',
                  borderRadius: '50%',
                  overflow: 'hidden',
                  border: '2px solid rgba(255,255,255,0.3)',
                  flexShrink: 0,
                }}
              >
                {org.profilePictureUrl ? (
                  <img
                    src={org.profilePictureUrl}
                    alt={fullName}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                ) : (
                  <div
                    style={{
                      width: '100%',
                      height: '100%',
                      background: 'rgba(79,156,249,0.3)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '2rem',
                      color: '#4f9cf9',
                    }}
                  >
                    {fullName.charAt(0).toUpperCase()}
                  </div>
                )}
              </div>
              <div>
                <p style={{ margin: 0, fontSize: '1rem', fontWeight: 600 }}>{fullName}</p>
                {org.company?.name && (
                  <p
                    style={{
                      margin: '0.15rem 0 0',
                      fontSize: '0.8rem',
                      color: 'rgba(255,255,255,0.6)',
                    }}
                  >
                    {org.company.name}
                  </p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
