import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LivePreview } from './LivePreview';
import * as usePublishingHook from '@/hooks/usePublishing/usePublishing';

// Mock usePublishing hook
vi.mock('@/hooks/usePublishing/usePublishing');

const mockUsePublishing = {
  preview: {
    eventCode: 'BATbern142',
    phase: 'SPEAKERS',
    mode: 'PROGRESSIVE',
    previewUrl: 'https://preview.batbern.ch/events/BATbern142?mode=preview',
    content: {
      topic: { title: 'BATbern 2025', date: '2025-05-15', venue: 'Kornhausforum' },
      speakers: [{ displayName: 'John Doe', companyName: 'Acme Corp' }],
    },
    validation: {
      isValid: true,
      errors: [],
    },
  },
  fetchPreview: vi.fn(),
  isLoadingPreview: false,
  previewError: null,
};

describe('LivePreview', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(usePublishingHook.usePublishing).mockReturnValue(mockUsePublishing);
  });

  describe('Device Toggle', () => {
    it('should render device toggle buttons', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.getByRole('button', { name: /desktop/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /mobile/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /print/i })).toBeInTheDocument();
    });

    it('should select desktop device by default', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const desktopButton = screen.getByRole('button', { name: /desktop/i });
      expect(desktopButton).toHaveAttribute('aria-pressed', 'true');
    });

    it('should switch to mobile view when mobile button clicked', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const mobileButton = screen.getByRole('button', { name: /mobile/i });
      fireEvent.click(mobileButton);

      await waitFor(() => {
        expect(mobileButton).toHaveAttribute('aria-pressed', 'true');
        const iframe = screen.getByTestId('preview-iframe');
        expect(iframe).toHaveClass(/mobile/i);
      });
    });

    it('should switch to print view when print button clicked', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const printButton = screen.getByRole('button', { name: /print/i });
      fireEvent.click(printButton);

      await waitFor(() => {
        expect(printButton).toHaveAttribute('aria-pressed', 'true');
        const iframe = screen.getByTestId('preview-iframe');
        expect(iframe).toHaveClass(/print/i);
      });
    });
  });

  describe('Preview Iframe', () => {
    it('should render iframe with preview URL', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveAttribute(
        'src',
        'https://preview.batbern.ch/events/BATbern142?mode=preview'
      );
    });

    it('should update iframe src when phase changes', async () => {
      const { rerender } = render(
        <LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />
      );

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveAttribute(
        'src',
        'https://preview.batbern.ch/events/BATbern142?mode=preview'
      );

      // Update preview with new phase
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: {
          ...mockUsePublishing.preview,
          phase: 'AGENDA',
          previewUrl: 'https://preview.batbern.ch/events/BATbern142?mode=preview&phase=agenda',
        },
      });

      rerender(<LivePreview eventCode="BATbern142" phase="agenda" mode="progressive" />);

      await waitFor(() => {
        expect(iframe).toHaveAttribute(
          'src',
          'https://preview.batbern.ch/events/BATbern142?mode=preview&phase=agenda'
        );
      });
    });

    it('should apply desktop dimensions to iframe by default', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveStyle({ width: '100%', height: '800px' });
    });

    it('should apply mobile dimensions when mobile device selected', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const mobileButton = screen.getByRole('button', { name: /mobile/i });
      fireEvent.click(mobileButton);

      await waitFor(() => {
        const iframe = screen.getByTestId('preview-iframe');
        expect(iframe).toHaveStyle({ width: '375px', height: '667px' });
      });
    });

    it('should apply print dimensions when print device selected', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const printButton = screen.getByRole('button', { name: /print/i });
      fireEvent.click(printButton);

      await waitFor(() => {
        const iframe = screen.getByTestId('preview-iframe');
        expect(iframe).toHaveStyle({ width: '210mm', height: '297mm' });
      });
    });
  });

  describe('Refresh Button', () => {
    it('should render refresh button', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.getByRole('button', { name: /refresh preview/i })).toBeInTheDocument();
    });

    it('should reload iframe when refresh button clicked', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const refreshButton = screen.getByRole('button', { name: /refresh preview/i });
      const iframe = screen.getByTestId('preview-iframe') as HTMLIFrameElement;

      // Mock iframe contentWindow.location.reload
      const mockReload = vi.fn();
      Object.defineProperty(iframe, 'contentWindow', {
        value: { location: { reload: mockReload } },
        writable: true,
      });

      fireEvent.click(refreshButton);

      await waitFor(() => {
        expect(mockReload).toHaveBeenCalled();
      });
    });

    it('should show loading state when refreshing', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        isLoadingPreview: true,
      });

      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.getByText(/refreshing preview/i)).toBeInTheDocument();
    });
  });

  describe('Auto-refresh on Content Changes', () => {
    it('should auto-refresh when preview content changes', async () => {
      const { rerender } = render(
        <LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />
      );

      const iframe = screen.getByTestId('preview-iframe') as HTMLIFrameElement;
      const initialSrc = iframe.src;

      // Update preview with new content and different preview URL
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: {
          ...mockUsePublishing.preview,
          previewUrl: 'https://preview.batbern.ch/events/BATbern142?mode=preview&updated=true',
          content: {
            ...mockUsePublishing.preview.content,
            speakers: [
              { displayName: 'John Doe', companyName: 'Acme Corp' },
              { displayName: 'Jane Smith', companyName: 'Tech Inc' },
            ],
          },
        },
      });

      rerender(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      await waitFor(() => {
        const updatedIframe = screen.getByTestId('preview-iframe') as HTMLIFrameElement;
        expect(updatedIframe.src).not.toBe(initialSrc);
        expect(updatedIframe.src).toContain('updated=true');
      });
    });
  });

  describe('Loading State', () => {
    it('should show loading spinner when preview is loading', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: null,
        isLoadingPreview: true,
      });

      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.getByTestId('preview-loading-spinner')).toBeInTheDocument();
    });

    it('should hide iframe when loading', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: null,
        isLoadingPreview: true,
      });

      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.queryByTestId('preview-iframe')).not.toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should show error message when preview fails to load', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: null,
        previewError: new Error('Failed to load preview'),
      });

      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.getByText(/failed to load preview/i)).toBeInTheDocument();
    });

    it('should show retry button on error', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: null,
        previewError: new Error('Failed to load preview'),
      });

      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
    });

    it('should retry loading when retry button clicked', async () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: null,
        previewError: new Error('Failed to load preview'),
      });

      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const retryButton = screen.getByRole('button', { name: /retry/i });
      fireEvent.click(retryButton);

      await waitFor(() => {
        expect(mockUsePublishing.fetchPreview).toHaveBeenCalledWith('speakers', 'progressive');
      });
    });
  });

  describe('Preview URL Generation', () => {
    it('should include mode parameter in preview URL', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveAttribute('src', expect.stringContaining('mode=preview'));
    });

    it('should use draft mode in preview URL when mode is draft', () => {
      vi.mocked(usePublishingHook.usePublishing).mockReturnValue({
        ...mockUsePublishing,
        preview: {
          ...mockUsePublishing.preview,
          mode: 'DRAFT',
          previewUrl: 'https://preview.batbern.ch/events/BATbern142?mode=draft',
        },
      });

      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="draft" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveAttribute('src', expect.stringContaining('mode=draft'));
    });
  });

  describe('Accessibility', () => {
    it('should have accessible label for iframe', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveAttribute('title', 'Event preview');
    });

    it('should have accessible labels for device toggle buttons', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      expect(screen.getByLabelText(/desktop preview/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/mobile preview/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/print preview/i)).toBeInTheDocument();
    });

    it('should announce device changes to screen readers', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" mode="progressive" />);

      const mobileButton = screen.getByRole('button', { name: /mobile/i });
      fireEvent.click(mobileButton);

      await waitFor(() => {
        const announcement = screen.getByRole('status', { hidden: true });
        expect(announcement).toHaveTextContent(/switched to mobile preview/i);
      });
    });
  });
});
