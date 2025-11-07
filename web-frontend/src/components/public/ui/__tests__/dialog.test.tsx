/**
 * Dialog Component Tests
 * Shadcn UI Component - Dialog wrapper around Radix UI
 *
 * Tests for modal dialog component used throughout the public interface
 */

import { describe, test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
  DialogDescription,
} from '../dialog';

describe('Dialog Component', () => {
  test('should_renderDialogTrigger_when_provided', () => {
    render(
      <Dialog>
        <DialogTrigger>Open Dialog</DialogTrigger>
      </Dialog>
    );

    expect(screen.getByText('Open Dialog')).toBeInTheDocument();
  });

  test('should_renderDialogContent_when_dialogOpen', () => {
    render(
      <Dialog open>
        <DialogContent>
          <div>Dialog Content</div>
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText('Dialog Content')).toBeInTheDocument();
  });

  test('should_renderDialogHeader_when_provided', () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogHeader>
            <div>Header Content</div>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText('Header Content')).toBeInTheDocument();
  });

  test('should_renderDialogTitle_when_provided', () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Dialog Title</DialogTitle>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText('Dialog Title')).toBeInTheDocument();
  });

  test('should_renderDialogDescription_when_provided', () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogHeader>
            <DialogDescription>Dialog Description</DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText('Dialog Description')).toBeInTheDocument();
  });

  test('should_renderDialogFooter_when_provided', () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogFooter>
            <button>Cancel</button>
            <button>Confirm</button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByText('Confirm')).toBeInTheDocument();
  });

  test('should_renderCloseButton_when_dialogOpen', () => {
    render(
      <Dialog open>
        <DialogContent>
          <div>Content</div>
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText('Close')).toBeInTheDocument();
  });

  test('should_applyCustomClassName_when_provided', () => {
    render(
      <Dialog open>
        <DialogContent className="custom-dialog">
          <div>Content</div>
        </DialogContent>
      </Dialog>
    );

    // DialogContent is rendered in a Portal to document.body, not the container
    const dialogContent = document.querySelector('.custom-dialog');
    expect(dialogContent).toBeInTheDocument();
  });

  test('should_renderCompleteDialog_when_allPartsProvided', () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Complete Dialog</DialogTitle>
            <DialogDescription>This is a complete dialog example</DialogDescription>
          </DialogHeader>
          <div>Main content goes here</div>
          <DialogFooter>
            <button>Action</button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );

    expect(screen.getByText('Complete Dialog')).toBeInTheDocument();
    expect(screen.getByText('This is a complete dialog example')).toBeInTheDocument();
    expect(screen.getByText('Main content goes here')).toBeInTheDocument();
    expect(screen.getByText('Action')).toBeInTheDocument();
  });
});
