import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '../../../i18n/config';
import { ReleaseNotesInfoBox } from './ReleaseNotesInfoBox';

vi.mock('@/contexts/useEnvironment', () => ({
  useEnvironment: vi.fn(() => 'production'),
}));

import { useEnvironment } from '@/contexts/useEnvironment';

const mockUseEnvironment = vi.mocked(useEnvironment);

describe('ReleaseNotesInfoBox', () => {
  beforeEach(() => {
    mockUseEnvironment.mockReturnValue('production');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should return null in production environment', () => {
    mockUseEnvironment.mockReturnValue('production');
    const { container } = render(<ReleaseNotesInfoBox />);
    expect(container.innerHTML).toBe('');
  });

  it('should fetch and display release notes in development environment', async () => {
    mockUseEnvironment.mockReturnValue('development');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        text: () => Promise.resolve('New feature released!'),
      })
    );

    render(<ReleaseNotesInfoBox />);

    await waitFor(() => {
      expect(screen.getByText('New feature released!')).toBeInTheDocument();
    });
    expect(fetch).toHaveBeenCalledWith('/release-notes.txt');
  });

  it('should fetch and display release notes in staging environment', async () => {
    mockUseEnvironment.mockReturnValue('staging');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        text: () => Promise.resolve('Staging notes here'),
      })
    );

    render(<ReleaseNotesInfoBox />);

    await waitFor(() => {
      expect(screen.getByText('Staging notes here')).toBeInTheDocument();
    });
  });

  it('should return null when fetch fails with network error', async () => {
    mockUseEnvironment.mockReturnValue('development');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')));

    const { container } = render(<ReleaseNotesInfoBox />);

    await waitFor(() => {
      expect(fetch).toHaveBeenCalled();
    });
    expect(container.innerHTML).toBe('');
  });

  it('should return null when fetch returns non-ok status', async () => {
    mockUseEnvironment.mockReturnValue('development');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
      })
    );

    const { container } = render(<ReleaseNotesInfoBox />);

    await waitFor(() => {
      expect(fetch).toHaveBeenCalled();
    });
    expect(container.innerHTML).toBe('');
  });

  it('should return null when notes content is empty or whitespace', async () => {
    mockUseEnvironment.mockReturnValue('development');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        text: () => Promise.resolve('   \n  '),
      })
    );

    const { container } = render(<ReleaseNotesInfoBox />);

    await waitFor(() => {
      expect(fetch).toHaveBeenCalled();
    });
    expect(container.innerHTML).toBe('');
  });

  it('should dismiss notes when close button is clicked', async () => {
    mockUseEnvironment.mockReturnValue('development');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        text: () => Promise.resolve('Dismissable note'),
      })
    );

    render(<ReleaseNotesInfoBox />);

    await waitFor(() => {
      expect(screen.getByText('Dismissable note')).toBeInTheDocument();
    });

    const closeButton = screen.getByRole('button');
    fireEvent.click(closeButton);

    await waitFor(() => {
      expect(screen.queryByText('Dismissable note')).not.toBeInTheDocument();
    });
  });
});
