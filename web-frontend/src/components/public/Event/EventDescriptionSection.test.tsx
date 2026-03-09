import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EventDescriptionSection } from './EventDescriptionSection';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => (key === 'description.heading' ? 'About This Event' : key),
  }),
}));

describe('EventDescriptionSection', () => {
  it('renders description text when description is a non-empty string', () => {
    render(<EventDescriptionSection description="Join us for great talks." />);
    expect(screen.getByText('Join us for great talks.')).toBeInTheDocument();
    expect(screen.getByText('About This Event')).toBeInTheDocument();
  });

  it('renders nothing when description is null', () => {
    const { container } = render(<EventDescriptionSection description={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when description is empty string', () => {
    const { container } = render(<EventDescriptionSection description="" />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when description is whitespace only', () => {
    const { container } = render(<EventDescriptionSection description="   " />);
    expect(container.firstChild).toBeNull();
  });
});
