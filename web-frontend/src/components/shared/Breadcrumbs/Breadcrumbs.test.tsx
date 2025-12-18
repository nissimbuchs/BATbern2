/**
 * Breadcrumbs Component Tests
 *
 * Tests for the reusable Breadcrumbs component
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { Breadcrumbs } from './Breadcrumbs';
import type { BreadcrumbItem } from './Breadcrumbs';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const renderBreadcrumbs = (items: BreadcrumbItem[]) => {
  return render(
    <BrowserRouter>
      <Breadcrumbs items={items} />
    </BrowserRouter>
  );
};

describe('Breadcrumbs', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it('should render breadcrumb items', () => {
    const items: BreadcrumbItem[] = [
      { label: 'Home', path: '/home' },
      { label: 'Events', path: '/events' },
      { label: 'Detail' },
    ];

    renderBreadcrumbs(items);

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.getByText('Events')).toBeInTheDocument();
    expect(screen.getByText('Detail')).toBeInTheDocument();
  });

  it('should render home icon on first item', () => {
    const items: BreadcrumbItem[] = [{ label: 'Home', path: '/home' }, { label: 'Current' }];

    const { container } = renderBreadcrumbs(items);

    // Check for HomeIcon SVG
    const homeIcon = container.querySelector('svg[data-testid="HomeIcon"]');
    expect(homeIcon).toBeInTheDocument();
  });

  it('should navigate when clicking a link', async () => {
    const user = userEvent.setup();
    const items: BreadcrumbItem[] = [
      { label: 'Home', path: '/home' },
      { label: 'Events', path: '/events' },
      { label: 'Current' },
    ];

    renderBreadcrumbs(items);

    const eventsLink = screen.getByText('Events');
    await user.click(eventsLink);

    expect(mockNavigate).toHaveBeenCalledWith('/events');
  });

  it('should not make last item clickable', () => {
    const items: BreadcrumbItem[] = [{ label: 'Home', path: '/home' }, { label: 'Current' }];

    renderBreadcrumbs(items);

    const currentText = screen.getByText('Current');
    expect(currentText.tagName).toBe('P'); // Should be Typography, not Link
  });

  it('should not make items without path clickable', () => {
    const items: BreadcrumbItem[] = [{ label: 'Home', path: '/home' }, { label: 'Not Clickable' }];

    renderBreadcrumbs(items);

    const notClickableText = screen.getByText('Not Clickable');
    expect(notClickableText.tagName).toBe('P'); // Should be Typography, not Link
  });

  it('should apply custom margin bottom', () => {
    const items: BreadcrumbItem[] = [{ label: 'Home' }];

    const { container } = render(
      <BrowserRouter>
        <Breadcrumbs items={items} marginBottom={5} />
      </BrowserRouter>
    );

    const breadcrumbs = container.querySelector('nav');
    expect(breadcrumbs).toHaveStyle({ marginBottom: expect.any(String) });
  });

  it('should apply custom aria-label', () => {
    const items: BreadcrumbItem[] = [{ label: 'Home' }];

    const { container } = render(
      <BrowserRouter>
        <Breadcrumbs items={items} ariaLabel="custom-breadcrumb" />
      </BrowserRouter>
    );

    const breadcrumbs = container.querySelector('nav');
    expect(breadcrumbs).toHaveAttribute('aria-label', 'custom-breadcrumb');
  });
});
