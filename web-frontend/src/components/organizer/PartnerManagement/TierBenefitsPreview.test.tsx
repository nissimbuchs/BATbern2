import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TierBenefitsPreview } from './TierBenefitsPreview';
import type { PartnershipLevel } from '@/types/partner';

describe('TierBenefitsPreview', () => {
  // AC6: Tier Benefits Preview updates dynamically

  describe('Test 6.1: should_displayBenefits_when_tierSelected', () => {
    it('displays benefits list when tier is selected', () => {
      render(<TierBenefitsPreview tier="GOLD" />);

      // Verify benefits are displayed (text includes checkmark prefix)
      expect(screen.getByText(/Logo placement on website/)).toBeInTheDocument();
      expect(screen.getByText(/Newsletter mentions/)).toBeInTheDocument();
      expect(screen.getByText(/Priority event access/)).toBeInTheDocument();
    });

    it('displays checkmarks for included benefits', () => {
      render(<TierBenefitsPreview tier="GOLD" />);

      // Verify checkmarks are present (using ✓ character)
      const benefits = screen.getAllByText(/✓/);
      expect(benefits.length).toBeGreaterThan(0);
    });
  });

  describe('Test 6.2: should_updateBenefits_when_tierChanged', () => {
    it('updates benefits list when tier prop changes', () => {
      const { rerender } = render(<TierBenefitsPreview tier="BRONZE" />);

      // Verify BRONZE benefits (only Event access)
      expect(screen.getByText(/Event access/)).toBeInTheDocument();
      expect(screen.queryByText(/Logo placement on website/)).not.toBeInTheDocument();

      // Change to PLATINUM tier
      rerender(<TierBenefitsPreview tier="PLATINUM" />);

      // Verify PLATINUM benefits now include logo
      expect(screen.getByText(/Logo placement on website/)).toBeInTheDocument();
      expect(screen.getByText(/Newsletter mentions/)).toBeInTheDocument();
      expect(screen.getByText(/Priority event access/)).toBeInTheDocument();
      expect(screen.getByText(/Quarterly strategic meetings/)).toBeInTheDocument();
    });
  });

  describe('Test 6.3: should_showCorrectBenefits_when_differentTiers', () => {
    it('displays STRATEGIC tier benefits (6 benefits)', () => {
      render(<TierBenefitsPreview tier="STRATEGIC" />);

      expect(screen.getByText(/Logo placement on website/)).toBeInTheDocument();
      expect(screen.getByText(/Newsletter mentions/)).toBeInTheDocument();
      expect(screen.getByText(/Priority event access/)).toBeInTheDocument();
      expect(screen.getByText(/Quarterly strategic meetings/)).toBeInTheDocument();
      expect(screen.getByText(/ROI analytics dashboard/)).toBeInTheDocument();
      expect(screen.getByText(/Dedicated account manager/)).toBeInTheDocument();
    });

    it('displays PLATINUM tier benefits (4 benefits)', () => {
      render(<TierBenefitsPreview tier="PLATINUM" />);

      expect(screen.getByText(/Logo placement on website/)).toBeInTheDocument();
      expect(screen.getByText(/Newsletter mentions/)).toBeInTheDocument();
      expect(screen.getByText(/Priority event access/)).toBeInTheDocument();
      expect(screen.getByText(/Quarterly strategic meetings/)).toBeInTheDocument();
      expect(screen.queryByText(/ROI analytics dashboard/)).not.toBeInTheDocument();
    });

    it('displays GOLD tier benefits (3 benefits)', () => {
      render(<TierBenefitsPreview tier="GOLD" />);

      expect(screen.getByText(/Logo placement on website/)).toBeInTheDocument();
      expect(screen.getByText(/Newsletter mentions/)).toBeInTheDocument();
      expect(screen.getByText(/Priority event access/)).toBeInTheDocument();
      expect(screen.queryByText(/Quarterly strategic meetings/)).not.toBeInTheDocument();
    });

    it('displays SILVER tier benefits (2 benefits)', () => {
      render(<TierBenefitsPreview tier="SILVER" />);

      expect(screen.getByText(/Newsletter mentions/)).toBeInTheDocument();
      expect(screen.getByText(/Event access/)).toBeInTheDocument();
      expect(screen.queryByText(/Logo placement on website/)).not.toBeInTheDocument();
    });

    it('displays BRONZE tier benefits (1 benefit)', () => {
      render(<TierBenefitsPreview tier="BRONZE" />);

      expect(screen.getByText(/Event access/)).toBeInTheDocument();
      expect(screen.queryByText(/Newsletter mentions/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Logo placement on website/)).not.toBeInTheDocument();
    });

    it('displays section title "Partnership Benefits"', () => {
      render(<TierBenefitsPreview tier="GOLD" />);

      expect(screen.getByText(/Partnership Benefits/i)).toBeInTheDocument();
    });
  });
});
