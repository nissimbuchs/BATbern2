/**
 * Tests for PublicFooter component
 */

import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { PublicFooter } from './PublicFooter';

describe('PublicFooter', () => {
  const renderFooter = () => {
    return render(
      <BrowserRouter>
        <I18nextProvider i18n={i18n}>
          <PublicFooter />
        </I18nextProvider>
      </BrowserRouter>
    );
  };

  it('should render footer with branding section', () => {
    renderFooter();

    expect(screen.getByText('BATbern')).toBeInTheDocument();
    // Tagline is translated - check for part of it
    expect(screen.getByText(/innovation meets tradition/i)).toBeInTheDocument();
  });

  it('should render contact email link', () => {
    renderFooter();

    const emailLink = screen.getByText('info@berner-architekten-treffen.ch');
    expect(emailLink).toBeInTheDocument();
    expect(emailLink).toHaveAttribute('href', 'mailto:info@berner-architekten-treffen.ch');
  });

  it('should render quick links section', () => {
    renderFooter();

    // Check for translated text (will be English by default in tests)
    expect(screen.getByText('Quick Links')).toBeInTheDocument();
    expect(screen.getByText('Current Event')).toBeInTheDocument();
    expect(screen.getByText('Past Events')).toBeInTheDocument();
    expect(screen.getByText('About')).toBeInTheDocument();
  });

  it('should render legal links section with copyright', () => {
    renderFooter();

    // Check for translated text
    expect(screen.getByText('Legal')).toBeInTheDocument();
    expect(screen.getByText('Privacy Policy')).toBeInTheDocument();
    expect(screen.getByText('Terms of Service')).toBeInTheDocument();

    // Copyright is now in the legal section with current year
    const currentYear = new Date().getFullYear();
    expect(screen.getByText(new RegExp(`© ${currentYear} Berner Architekten Treffen`))).toBeInTheDocument();
  });

  it('should have correct link hrefs', () => {
    renderFooter();

    const currentEventLink = screen.getByText('Current Event').closest('a');
    const pastEventsLink = screen.getByText('Past Events').closest('a');
    const aboutLink = screen.getByText('About').closest('a');
    const privacyLink = screen.getByText('Privacy Policy').closest('a');
    const termsLink = screen.getByText('Terms of Service').closest('a');

    expect(currentEventLink).toHaveAttribute('href', '/');
    expect(pastEventsLink).toHaveAttribute('href', '/archive');
    expect(aboutLink).toHaveAttribute('href', '/about');
    expect(privacyLink).toHaveAttribute('href', '/privacy');
    expect(termsLink).toHaveAttribute('href', '/terms');
  });

  it('should have proper styling classes for dark theme', () => {
    const { container } = renderFooter();

    const footer = container.querySelector('footer');
    expect(footer).toHaveClass('border-t', 'border-zinc-800', 'bg-zinc-900');
  });

  it('should have responsive grid layout', () => {
    const { container } = renderFooter();

    const grid = container.querySelector('.grid');
    expect(grid).toHaveClass('grid-cols-1', 'md:grid-cols-3');
  });
});
