/**
 * Label Component Tests
 * Shadcn UI Component - Label wrapper around Radix UI
 *
 * Tests for label component used for form labels
 */

import { describe, test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Label } from '../label';

describe('Label Component', () => {
  test('should_renderLabel_when_provided', () => {
    render(<Label>Test Label</Label>);

    expect(screen.getByText('Test Label')).toBeInTheDocument();
  });

  test('should_renderLabelWithHtmlFor_when_provided', () => {
    render(<Label htmlFor="test-input">Name</Label>);

    const label = screen.getByText('Name');
    expect(label).toHaveAttribute('for', 'test-input');
  });

  test('should_applyCustomClassName_when_provided', () => {
    const { container } = render(
      <Label className="custom-label">Custom Styled Label</Label>
    );

    const label = container.querySelector('.custom-label');
    expect(label).toBeInTheDocument();
    expect(label).toHaveTextContent('Custom Styled Label');
  });

  test('should_renderLabelWithChildren_when_provided', () => {
    render(
      <Label>
        <span>Label with </span>
        <strong>formatted</strong>
        <span> text</span>
      </Label>
    );

    expect(screen.getByText('formatted')).toBeInTheDocument();
  });

  test('should_workWithInput_when_associated', () => {
    render(
      <div>
        <Label htmlFor="email-input">Email Address</Label>
        <input id="email-input" type="email" />
      </div>
    );

    const label = screen.getByText('Email Address');
    const input = document.getElementById('email-input');

    expect(label).toHaveAttribute('for', 'email-input');
    expect(input).toBeInTheDocument();
  });

  test('should_renderEmptyLabel_when_noChildren', () => {
    const { container } = render(<Label />);

    const label = container.querySelector('label');
    expect(label).toBeInTheDocument();
    expect(label).toHaveTextContent('');
  });
});
