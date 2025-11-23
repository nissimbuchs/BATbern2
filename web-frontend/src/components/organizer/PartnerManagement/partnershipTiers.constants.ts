/**
 * Partnership tier levels with their display properties
 */
export const PARTNERSHIP_TIERS = {
  STRATEGIC: {
    emoji: '🏆',
    label: 'Strategic',
    description: 'Highest tier partnership with full benefits',
  },
  PLATINUM: {
    emoji: '💎',
    label: 'Platinum',
    description: 'Premium partnership with advanced benefits',
  },
  GOLD: {
    emoji: '🥇',
    label: 'Gold',
    description: 'High-value partnership with extensive benefits',
  },
  SILVER: { emoji: '🥈', label: 'Silver', description: 'Standard partnership with core benefits' },
  BRONZE: {
    emoji: '🥉',
    label: 'Bronze',
    description: 'Entry-level partnership with basic benefits',
  },
} as const;

export type PartnershipLevel = keyof typeof PARTNERSHIP_TIERS;
