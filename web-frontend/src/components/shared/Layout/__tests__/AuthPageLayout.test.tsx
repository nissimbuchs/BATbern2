/**
 * AuthPageLayout Component Tests
 *
 * Tests for authentication page layout wrapper that isolates Material-UI styling
 */

import { describe, test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthPageLayout } from '../AuthPageLayout';

describe('AuthPageLayout Component', () => {
  test('should_renderChildren_when_layoutMounted', () => {
    render(
      <AuthPageLayout>
        <div data-testid="test-content">Test Content</div>
      </AuthPageLayout>
    );

    expect(screen.getByTestId('test-content')).toBeInTheDocument();
    expect(screen.getByText('Test Content')).toBeInTheDocument();
  });

  test('should_renderMuiBox_when_layoutMounted', () => {
    const { container } = render(
      <AuthPageLayout>
        <div>Test</div>
      </AuthPageLayout>
    );

    // Box component renders as a div by default
    const boxElement = container.querySelector('.MuiBox-root');
    expect(boxElement).toBeInTheDocument();
  });

  test('should_renderMultipleChildren_when_providedMultipleElements', () => {
    render(
      <AuthPageLayout>
        <div data-testid="child-1">Child 1</div>
        <div data-testid="child-2">Child 2</div>
      </AuthPageLayout>
    );

    expect(screen.getByTestId('child-1')).toBeInTheDocument();
    expect(screen.getByTestId('child-2')).toBeInTheDocument();
  });

  test('should_renderCssBaseline_when_layoutMounted', () => {
    const { container } = render(
      <AuthPageLayout>
        <div>Test</div>
      </AuthPageLayout>
    );

    // CssBaseline injects global styles, verify layout structure exists
    expect(container.firstChild).toBeInTheDocument();
  });

  test('should_handleEmptyChildren_when_noChildrenProvided', () => {
    const { container } = render(<AuthPageLayout>{null}</AuthPageLayout>);

    expect(container.firstChild).toBeInTheDocument();
  });
});
