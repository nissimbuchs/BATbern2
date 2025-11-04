/**
 * PublicLayout Component Tests
 * Story 4.1.2: Public Layout & Navigation
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { PublicLayout } from './PublicLayout';

// Mock child component
const TestChild = () => <div>Test Content</div>;

describe('PublicLayout', () => {
  it('should render children correctly', () => {
    render(
      <BrowserRouter>
        <PublicLayout>
          <TestChild />
        </PublicLayout>
      </BrowserRouter>
    );

    expect(screen.getByText('Test Content')).toBeInTheDocument();
  });

  it('should apply dark theme classes', () => {
    const { container } = render(
      <BrowserRouter>
        <PublicLayout>
          <TestChild />
        </PublicLayout>
      </BrowserRouter>
    );

    const layoutDiv = container.firstChild as HTMLElement;
    expect(layoutDiv).toHaveClass('min-h-screen');
    expect(layoutDiv).toHaveClass('w-full');
    expect(layoutDiv).toHaveClass('flex');
    expect(layoutDiv).toHaveClass('flex-col');

    // Dark theme is applied to html element via useEffect, not to the layout div
    expect(document.documentElement).toHaveClass('dark');
  });

  it('should render navigation and footer', () => {
    render(
      <BrowserRouter>
        <PublicLayout>
          <TestChild />
        </PublicLayout>
      </BrowserRouter>
    );

    // Check for navigation (BATbern logo text appears in both header and footer)
    const batbernElements = screen.getAllByText('BATbern');
    expect(batbernElements.length).toBeGreaterThan(0);

    // Check for footer copyright text (also appears in footer tagline)
    const footerText = screen.getAllByText(/Berner Architekten Treffen/i);
    expect(footerText.length).toBeGreaterThan(0);
  });
});
