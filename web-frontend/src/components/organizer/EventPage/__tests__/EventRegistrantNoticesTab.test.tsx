/**
 * EventRegistrantNoticesTab Tests (Story 7.3 hardening)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EventRegistrantNoticesTab } from '../EventRegistrantNoticesTab';

vi.mock('@/hooks/useEmailTemplates', () => ({
  useEmailTemplates: vi.fn(),
}));
vi.mock('@/hooks/useRegistrantNotice/useRegistrantNotice', () => ({
  usePreviewRegistrantNotice: vi.fn(),
  useSendRegistrantNotice: vi.fn(),
}));
vi.mock('@/hooks/useNewsletter/useNewsletter', () => ({
  useSendStatus: vi.fn(),
}));
vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: () => ({ isMobile: false }),
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts?.defaultValue as string) ?? key,
  }),
}));

import { useEmailTemplates } from '@/hooks/useEmailTemplates';
import {
  usePreviewRegistrantNotice,
  useSendRegistrantNotice,
} from '@/hooks/useRegistrantNotice/useRegistrantNotice';
import { useSendStatus } from '@/hooks/useNewsletter/useNewsletter';

const previewMutate = vi.fn();
const sendMutate = vi.fn();

function mockTemplates(list: Array<{ templateKey: string; locale: string; category: string }>) {
  vi.mocked(useEmailTemplates).mockReturnValue({
    data: list,
    isLoading: false,
  } as ReturnType<typeof useEmailTemplates>);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockTemplates([{ templateKey: 'slides-online', locale: 'de', category: 'REGISTRANT_NOTICE' }]);
  vi.mocked(usePreviewRegistrantNotice).mockReturnValue({
    mutate: previewMutate,
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof usePreviewRegistrantNotice>);
  vi.mocked(useSendRegistrantNotice).mockReturnValue({
    mutate: sendMutate,
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useSendRegistrantNotice>);
  // No active send by default → no status alert.
  vi.mocked(useSendStatus).mockReturnValue({
    data: undefined,
  } as ReturnType<typeof useSendStatus>);
});

function renderTab() {
  return render(<EventRegistrantNoticesTab eventCode="BATbern73" eventTitle="DevOps Event" />);
}

describe('EventRegistrantNoticesTab', () => {
  it('renders the REGISTRANT_NOTICE template selector + send/preview buttons', () => {
    renderTab();
    expect(screen.getByTestId('rn-template-select')).toBeInTheDocument();
    expect(screen.getByTestId('rn-preview-button')).toBeInTheDocument();
    expect(screen.getByTestId('rn-send-button')).toBeInTheDocument();
  });

  it('shows a hint when no registrant-notice template exists for the language', () => {
    mockTemplates([]); // none for de
    renderTab();
    expect(screen.getByTestId('rn-no-templates')).toBeInTheDocument();
  });

  it('previews the selected template and shows the recipient count', async () => {
    previewMutate.mockImplementation((_vars, opts) =>
      opts.onSuccess({ subject: 'S', htmlPreview: '<p>hi</p>', recipientCount: 42 })
    );
    renderTab();
    fireEvent.click(screen.getByTestId('rn-preview-button'));
    await waitFor(() => expect(previewMutate).toHaveBeenCalled());
    expect(previewMutate.mock.calls[0][0]).toEqual({ templateKey: 'slides-online', locale: 'de' });
    expect(screen.getByTestId('rn-recipient-count')).toHaveTextContent('42');
    expect(screen.getByTestId('rn-preview-iframe')).toBeInTheDocument();
  });

  it('opens the confirm dialog and sends to registrants', async () => {
    // Send without previewing first → the tab fetches the recipient count before confirming.
    previewMutate.mockImplementation((_vars, opts) =>
      opts.onSuccess({ subject: 'S', htmlPreview: '<p>hi</p>', recipientCount: 174 })
    );
    sendMutate.mockImplementation((_templateKey, opts) =>
      opts.onSuccess({ sendId: 's1', status: 'PENDING', recipientCount: 174 })
    );
    // Status poll resolves to a terminal COMPLETED result.
    vi.mocked(useSendStatus).mockReturnValue({
      data: {
        id: 's1',
        status: 'COMPLETED',
        sentCount: 174,
        failedCount: 0,
        totalCount: 174,
        percentComplete: 100,
      },
    } as ReturnType<typeof useSendStatus>);
    renderTab();
    fireEvent.click(screen.getByTestId('rn-send-button'));
    await waitFor(() => expect(screen.getByTestId('rn-confirm-send')).toBeInTheDocument());
    // count was resolved before the dialog opened
    expect(screen.getByTestId('rn-recipient-count')).toHaveTextContent('174');
    fireEvent.click(screen.getByTestId('rn-confirm-send'));
    await waitFor(() =>
      expect(sendMutate).toHaveBeenCalledWith('slides-online', expect.anything())
    );
    expect(screen.getByTestId('rn-send-success')).toBeInTheDocument();
  });
});
