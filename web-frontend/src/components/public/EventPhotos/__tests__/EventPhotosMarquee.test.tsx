/**
 * EventPhotosMarquee — clicking a marquee photo opens the (lazy) lightbox at that photo's index.
 *
 * The heavy YARL-backed PhotoLightbox is mocked to a stub that echoes its open/index props, and
 * InfiniteMarquee is stubbed to render its children once (the real one duplicates them for the
 * seamless loop), so the test isolates the open-at-index behaviour.
 */
import { describe, test, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { render } from '@/test/test-utils';
import { EventPhotosMarquee } from '../EventPhotosMarquee';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('@/components/public/Testimonials/InfiniteMarquee', () => ({
  InfiniteMarquee: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('../PhotoLightbox', () => ({
  default: ({ open, index }: { open: boolean; index: number }) =>
    open ? <div data-testid="lightbox" data-index={index} /> : null,
}));

const photos = [
  { id: 'p1', displayUrl: 'https://cdn.batbern.ch/a.jpg', filename: 'a.jpg' },
  { id: 'p2', displayUrl: 'https://cdn.batbern.ch/b.jpg', filename: 'b.jpg' },
  { id: 'p3', displayUrl: 'https://cdn.batbern.ch/c.jpg', filename: 'c.jpg' },
];

describe('EventPhotosMarquee', () => {
  test('renders nothing when there are no photos', () => {
    const { container } = render(<EventPhotosMarquee photos={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  test('renders a button per photo and the lightbox is closed initially', () => {
    render(<EventPhotosMarquee photos={photos} />);
    expect(screen.getAllByTestId('marquee-photo')).toHaveLength(3);
    expect(screen.queryByTestId('lightbox')).not.toBeInTheDocument();
  });

  test('clicking a photo opens the lightbox at that photo index', async () => {
    const user = userEvent.setup();
    render(<EventPhotosMarquee photos={photos} />);

    await user.click(screen.getAllByTestId('marquee-photo')[1]);

    const lightbox = await screen.findByTestId('lightbox');
    expect(lightbox).toHaveAttribute('data-index', '1');
  });
});
