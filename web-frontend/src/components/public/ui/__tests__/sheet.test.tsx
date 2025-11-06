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

    expect(screen.getByText('Close')).toBeInTheDocument();
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
    const { container } = render(
      <Sheet open>
        <SheetContent>
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    // Default side is 'right'
    const content = container.querySelector('[class*="right"]');
    expect(content).toBeInTheDocument();
  });

  test('should_renderSheetFromLeft_when_sideIsLeft', () => {
    const { container } = render(
      <Sheet open>
        <SheetContent side="left">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const content = container.querySelector('[class*="left"]');
    expect(content).toBeInTheDocument();
  });

  test('should_renderSheetFromTop_when_sideIsTop', () => {
    const { container } = render(
      <Sheet open>
        <SheetContent side="top">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const content = container.querySelector('[class*="top"]');
    expect(content).toBeInTheDocument();
  });

  test('should_renderSheetFromBottom_when_sideIsBottom', () => {
    const { container } = render(
      <Sheet open>
        <SheetContent side="bottom">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const content = container.querySelector('[class*="bottom"]');
    expect(content).toBeInTheDocument();
  });

  test('should_applyCustomClassName_when_provided', () => {
    const { container } = render(
      <Sheet open>
        <SheetContent className="custom-sheet">
          <div>Content</div>
        </SheetContent>
      </Sheet>
    );

    const sheetContent = container.querySelector('.custom-sheet');
    expect(sheetContent).toBeInTheDocument();
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
