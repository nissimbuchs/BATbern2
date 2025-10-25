/**
 * ReleaseNotesInfoBox Component Tests
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReleaseNotesInfoBox } from './ReleaseNotesInfoBox';
import * as useEnvironmentModule from '@/contexts/useEnvironment';

// Mock the useEnvironment hook
vi.mock('@/contexts/useEnvironment', () => ({
  useEnvironment: vi.fn(),
}));

describe('ReleaseNotesInfoBox', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    // Setup fetch mock
    global.fetch = mockFetch;
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Environment-based rendering', () => {
    it('should render in development environment with notes', async () => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('development');
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => 'Test release notes',
      });

      render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(screen.getByText('Release Notes')).toBeInTheDocument();
        expect(screen.getByText('Test release notes')).toBeInTheDocument();
      });
    });

    it('should render in staging environment with notes', async () => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('staging');
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => 'Staging notes',
      });

      render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(screen.getByText('Release Notes')).toBeInTheDocument();
        expect(screen.getByText('Staging notes')).toBeInTheDocument();
      });
    });

    it('should not render in production environment', async () => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('production');
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => 'Production notes',
      });

      const { container } = render(<ReleaseNotesInfoBox />);

      // Wait a bit to ensure nothing is rendered
      await waitFor(() => {
        expect(container.firstChild).toBeNull();
      });

      // Verify fetch was not called in production
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });

  describe('Content handling', () => {
    beforeEach(() => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('development');
    });

    it('should handle multiline release notes', async () => {
      const multilineNotes = `Latest Updates:
- Feature A added
- Bug B fixed
- Performance improved`;

      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => multilineNotes,
      });

      render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(screen.getByText(/Latest Updates:/)).toBeInTheDocument();
        expect(screen.getByText(/Feature A added/)).toBeInTheDocument();
        expect(screen.getByText(/Bug B fixed/)).toBeInTheDocument();
      });
    });

    it('should not render when notes file is empty', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => '   ',
      });

      const { container } = render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(container.firstChild).toBeNull();
      });
    });

    it('should not render when notes file does not exist (404)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
      });

      const { container } = render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(container.firstChild).toBeNull();
      });
    });

    it('should trim whitespace from notes', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => '  \n\n  Test notes with whitespace  \n\n  ',
      });

      render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(screen.getByText('Test notes with whitespace')).toBeInTheDocument();
      });
    });
  });

  describe('Error handling', () => {
    beforeEach(() => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('development');
    });

    it('should handle fetch errors gracefully', async () => {
      const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      mockFetch.mockRejectedValueOnce(new Error('Network error'));

      const { container } = render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(container.firstChild).toBeNull();
        expect(consoleWarnSpy).toHaveBeenCalledWith(
          '[ReleaseNotesInfoBox] Failed to fetch release notes:',
          expect.any(Error)
        );
      });

      consoleWarnSpy.mockRestore();
    });

    it('should handle non-ok response gracefully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
      });

      const { container } = render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        expect(container.firstChild).toBeNull();
      });
    });
  });

  describe('User interactions', () => {
    beforeEach(() => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('development');
    });

    it('should dismiss the info box when close button is clicked', async () => {
      const user = userEvent.setup();
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => 'Test notes',
      });

      render(<ReleaseNotesInfoBox />);

      // Wait for notes to appear
      await waitFor(() => {
        expect(screen.getByText('Release Notes')).toBeInTheDocument();
      });

      // Click close button
      const closeButton = screen.getByRole('button', { name: /close/i });
      await user.click(closeButton);

      // Wait for dismissal
      await waitFor(() => {
        expect(screen.queryByText('Release Notes')).not.toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    beforeEach(() => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('development');
    });

    it('should have accessible close button', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => 'Test notes',
      });

      render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        const closeButton = screen.getByRole('button', { name: /close/i });
        expect(closeButton).toBeInTheDocument();
        expect(closeButton).toHaveAttribute('aria-label', 'close');
      });
    });

    it('should use semantic HTML structure', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => 'Test notes',
      });

      const { container } = render(<ReleaseNotesInfoBox />);

      await waitFor(() => {
        // Alert component should have role="alert"
        const alert = container.querySelector('[role="alert"]');
        expect(alert).toBeInTheDocument();
      });
    });
  });

  describe('Loading state', () => {
    beforeEach(() => {
      vi.mocked(useEnvironmentModule.useEnvironment).mockReturnValue('development');
    });

    it('should not render anything while loading', () => {
      // Create a promise that never resolves to keep it in loading state
      const neverResolve = new Promise(() => {});
      mockFetch.mockReturnValueOnce(neverResolve as any);

      const { container } = render(<ReleaseNotesInfoBox />);

      // Should not render anything while loading
      expect(container.firstChild).toBeNull();
    });
  });
});
