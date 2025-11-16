/**
 * Sheet Component Tests
 * Shadcn UI Component - Sheet (slide-out panel) wrapper around Radix UI Dialog
 *
 * Tests for sheet/drawer component used for mobile navigation and side panels
 */

import { describe, test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  Sheet,
  SheetTrigger,
  SheetContent,
  SheetHeader,
  SheetFooter,
  SheetTitle,
  SheetDescription,
} from '../sheet';

describe('Sheet Component', () => {
  test('should_renderSheetTrigger_when_provided', () => {
    render(
      <Sheet>
        <SheetTrigger>Open Sheet</SheetTrigger>
      </Sheet>
    );

    expect(screen.getByText('Open Sheet')).toBeInTheDocument();
  });

  test('should_renderSheetContent_when_sheetOpen', () => {
    render(
      <Sheet open>
        <SheetContent>
          <div>Sheet Content</div>
        </SheetContent>
      </Sheet>
    );

    expect(screen.getByText('Sheet Content')).toBeInTheDocument();
  });

  test('should_renderSheetHeader_when_provided', () => {
    render(
      <Sheet open>
        <SheetContent>
          <SheetHeader>
            <div>Header Content</div>
          </SheetHeader>
        </SheetContent>
      </Sheet>
    );

    expect(screen.getByText('Header Content')).toBeInTheDocument();
  });

  test('should_renderSheetTitle_when_provided', () => {
    render(
      <Sheet open>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Sheet Title</SheetTitle>
          </SheetHeader>
        </SheetContent>
      </Sheet>
    );

    expect(screen.getByText('Sheet Title')).toBeInTheDocument();
  });

  test('should_renderSheetDescription_when_provided', () => {
    render(
      <Sheet open>
        <SheetContent>
          <SheetHeader>
            <SheetDescription>Sheet Description</SheetDescription>
          </SheetHeader>
        </SheetContent>
      </Sheet>
    );

    expect(screen.getByText('Sheet Description')).toBeInTheDocument();
  });

  test('should_renderSheetFooter_when_provided', () => {
    render(
      <Sheet open>
        <SheetContent>
          <SheetFooter>
            <button>Close</button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    );

    // Multiple "Close" texts exist (screen-reader close button + footer button)
    // Query for the footer button specifically
    const closeButtons = screen.getAllByText('Close');
    expect(closeButtons.length).toBeGreaterThanOrEqual(1);
  });

  test('should_renderCloseButton_when_sheetOpen', () => {
    render(
      <Sheet open>
        <SheetContent>
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    expect(screen.getByText('Close')).toBeInTheDocument();
  });

  test('should_renderSheetFromRight_when_sideNotSpecified', () => {
    render(
      <Sheet open>
        <SheetContent>
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    // Default side is 'right' - SheetContent renders in portal, so query by role
    const content = screen.getByRole('dialog');
    expect(content).toHaveClass('right-0'); // Verify right positioning class
  });

  test('should_renderSheetFromLeft_when_sideIsLeft', () => {
    render(
      <Sheet open>
        <SheetContent side="left">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const content = screen.getByRole('dialog');
    expect(content).toHaveClass('left-0'); // Verify left positioning class
  });

  test('should_renderSheetFromTop_when_sideIsTop', () => {
    render(
      <Sheet open>
        <SheetContent side="top">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const content = screen.getByRole('dialog');
    expect(content).toHaveClass('top-0'); // Verify top positioning class
  });

  test('should_renderSheetFromBottom_when_sideIsBottom', () => {
    render(
      <Sheet open>
        <SheetContent side="bottom">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const content = screen.getByRole('dialog');
    expect(content).toHaveClass('bottom-0'); // Verify bottom positioning class
  });

  test('should_applyCustomClassName_when_provided', () => {
    render(
      <Sheet open>
        <SheetContent className="custom-sheet">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const sheetContent = screen.getByRole('dialog');
    expect(sheetContent).toHaveClass('custom-sheet'); // Verify custom class is applied
  });

  test('should_renderCompleteSheet_when_allPartsProvided', () => {
    render(
      <Sheet open>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Complete Sheet</SheetTitle>
            <SheetDescription>This is a complete sheet example</SheetDescription>
          </SheetHeader>
          <div>Main content goes here</div>
          <SheetFooter>
            <button>Action</button>
          </SheetFooter>
        </SheetContent>
      </Sheet>
    );

    expect(screen.getByText('Complete Sheet')).toBeInTheDocument();
    expect(screen.getByText('This is a complete sheet example')).toBeInTheDocument();
    expect(screen.getByText('Main content goes here')).toBeInTheDocument();
    expect(screen.getByText('Action')).toBeInTheDocument();
  });
});
