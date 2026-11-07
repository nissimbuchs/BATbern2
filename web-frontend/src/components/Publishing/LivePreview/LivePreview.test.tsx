import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LivePreview } from './LivePreview';
import * as usePublishingHook from '@/hooks/usePublishing/usePublishing';

// Story 17.1: the publish-preview chain (usePublishing.fetchPreview → GET /publish/{phase}/preview,
// a path that exists nowhere) is gone. The live preview frames the public event page directly.
// The hook stays mocked so a test can prove LivePreview no longer reaches for it.
vi.mock('@/hooks/usePublishing/usePublishing');

const expectedPreviewUrl = (eventCode: string, phase: string) =>
  `/events/${eventCode}?preview=true&phase=${phase}`;

describe('LivePreview', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Device Toggle', () => {
    it('should render device toggle buttons', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      expect(screen.getByRole('button', { name: /desktop/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /mobile/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /print/i })).toBeInTheDocument();
    });

    it('should select desktop device by default', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const desktopButton = screen.getByRole('button', { name: /desktop/i });
      expect(desktopButton).toHaveAttribute('aria-pressed', 'true');
    });

    it('should switch to mobile view when mobile button clicked', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const mobileButton = screen.getByRole('button', { name: /mobile/i });
      fireEvent.click(mobileButton);

      await waitFor(() => {
        expect(mobileButton).toHaveAttribute('aria-pressed', 'true');
        const iframe = screen.getByTestId('preview-iframe');
        expect(iframe).toHaveClass(/mobile/i);
      });
    });

    it('should switch to print view when print button clicked', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const printButton = screen.getByRole('button', { name: /print/i });
      fireEvent.click(printButton);

      await waitFor(() => {
        expect(printButton).toHaveAttribute('aria-pressed', 'true');
        const iframe = screen.getByTestId('preview-iframe');
        expect(iframe).toHaveClass(/print/i);
      });
    });

    it('should_hideDeviceToggleLabelText_atXs', () => {
      // The textual label inside each device ToggleButton is icon-only on phones:
      // display { xs: 'none', md: 'inline' } -> compiled to @media (min-width:0px)
      // { display:none } and @media (min-width:900px) { display:inline }. jsdom does
      // not evaluate media queries, so inspect the injected emotion stylesheet.
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const labelSpan = screen.getByText('Desktop');
      const cssClass = Array.from(labelSpan.classList).find((c) => c.startsWith('css-'));
      expect(cssClass).toBeTruthy();

      let css = '';
      document.querySelectorAll('style').forEach((styleEl) => {
        const text = styleEl.textContent ?? '';
        if (cssClass && text.includes(`.${cssClass}`)) css += text + '\n';
      });

      expect(
        new RegExp(`@media\\s*\\(min-width:\\s*0px\\)\\s*\\{[^}]*display:\\s*none[^}]*\\}`).test(
          css
        )
      ).toBe(true);
    });
  });

  describe('Preview Iframe', () => {
    it('should render iframe with the public event page preview URL', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveAttribute('src', expectedPreviewUrl('BATbern142', 'speakers'));
    });

    it('should not use the removed publish-preview chain', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      expect(usePublishingHook.usePublishing).not.toHaveBeenCalled();
    });

    it('should update iframe src when phase changes', () => {
      const { rerender } = render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      expect(screen.getByTestId('preview-iframe')).toHaveAttribute(
        'src',
        expectedPreviewUrl('BATbern142', 'speakers')
      );

      rerender(<LivePreview eventCode="BATbern142" phase="agenda" />);

      expect(screen.getByTestId('preview-iframe')).toHaveAttribute(
        'src',
        expectedPreviewUrl('BATbern142', 'agenda')
      );
    });

    it('should apply desktop dimensions to iframe by default', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveStyle({ width: '100%', height: '800px' });
    });

    it('should apply mobile dimensions when mobile device selected', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const mobileButton = screen.getByRole('button', { name: /mobile/i });
      fireEvent.click(mobileButton);

      await waitFor(() => {
        const iframe = screen.getByTestId('preview-iframe');
        expect(iframe).toHaveStyle({ width: '375px', height: '667px' });
      });
    });

    it('should apply print dimensions when print device selected', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const printButton = screen.getByRole('button', { name: /print/i });
      fireEvent.click(printButton);

      await waitFor(() => {
        const iframe = screen.getByTestId('preview-iframe');
        // Asserts the INLINE style rather than toHaveStyle(): jsdom 30 normalises physical
        // units to pixels in getComputedStyle, so toHaveStyle({ width: '210mm' }) now sees
        // "793.701px" (210 * 96/25.4) and fails. jsdom 29 returned "210mm" verbatim. The
        // component is correct either way — it sets A4 — so assert what it sets, which is
        // stable across jsdom versions. The px-based sibling tests above are unaffected.
        expect(iframe.style.width).toBe('210mm');
        expect(iframe.style.height).toBe('297mm');
      });
    });
  });

  describe('Refresh Button', () => {
    it('should render refresh button', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      expect(screen.getByRole('button', { name: /refresh preview/i })).toBeInTheDocument();
    });

    it('should reload iframe when refresh button clicked', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

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

    it('should show loading state when refreshing', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      fireEvent.click(screen.getByRole('button', { name: /refresh preview/i }));

      expect(await screen.findByTestId('preview-loading-spinner')).toBeInTheDocument();
      expect(screen.getByText(/refreshing preview/i)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have accessible label for iframe', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const iframe = screen.getByTestId('preview-iframe');
      expect(iframe).toHaveAttribute('title', 'Event preview');
    });

    it('should have accessible labels for device toggle buttons', () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      expect(screen.getByLabelText(/desktop preview/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/mobile preview/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/print preview/i)).toBeInTheDocument();
    });

    it('should announce device changes to screen readers', async () => {
      render(<LivePreview eventCode="BATbern142" phase="speakers" />);

      const mobileButton = screen.getByRole('button', { name: /mobile/i });
      fireEvent.click(mobileButton);

      await waitFor(() => {
        const announcement = screen.getByRole('status', { hidden: true });
        expect(announcement).toHaveTextContent(/switched to mobile preview/i);
      });
    });
  });
});
