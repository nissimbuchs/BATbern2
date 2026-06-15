/**
 * EventParticipantsTab Component Tests
 *
 * TDD Tests for event participants tab container component
 * RED Phase: Tests written first
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import EventParticipantsTab from './EventParticipantsTab';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { eventApiClient } from '@/services/eventApiClient';
import { enrollStakeholders } from '@/services/api/eventRegistrationService';

// Mock EventParticipantList component
vi.mock('./EventParticipantList', () => ({
  default: ({ eventCode }: { eventCode: string }) => (
    <div data-testid="participant-list">List for {eventCode}</div>
  ),
}));

// Enrol organizers & partners moved here from the Overview (Epic 14 FR25).
vi.mock('@/services/api/eventRegistrationService', () => ({
  enrollStakeholders: vi.fn().mockResolvedValue({ enrolled: 3, skipped: 1 }),
}));

// Mock translation
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

// Mock the event API client so we can intercept the export calls.
vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    exportParticipantsXlsx: vi.fn(),
    exportParticipantsDocx: vi.fn(),
  },
}));

const mockEvent = {
  eventCode: 'BAT-2024-01',
  eventNumber: 123,
  title: 'Test Event',
  subtitle: 'Test Subtitle',
  date: '2024-06-15T18:00:00Z',
  registrationDeadline: '2024-06-10T23:59:59Z',
  venueName: 'Test Venue',
  venueAddress: 'Test Address',
  venueCapacity: 200,
  organizerUsername: 'admin',
  currentWorkflowState: 'DRAFT' as const,
  workflowHistory: [],
  registrationCount: 0,
  attendanceCount: 0,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
};

describe('EventParticipantsTab Component', () => {
  describe('Rendering', () => {
    it('should render the participants list', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      expect(screen.getByTestId('participant-list')).toBeInTheDocument();
    });

    it('should pass event code to participant list', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      expect(screen.getByText('List for BAT-2024-01')).toBeInTheDocument();
    });

    it('should render tab title', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      expect(screen.getByText('eventPage.participantsTab.title')).toBeInTheDocument();
    });

    it('should render participant count badge', () => {
      const eventWithParticipants = {
        ...mockEvent,
        confirmedCount: 42,
        waitlistCount: 0,
      };

      renderWithProviders(<EventParticipantsTab event={eventWithParticipants} />);

      expect(screen.getByText('42')).toBeInTheDocument();
    });

    it('should_stackHeaderColumn_atXs_andRow_atMd', () => {
      // The header Stack is direction={{ xs: 'column', md: 'row' }} so the count
      // block and export buttons stack vertically on phones. MUI compiles this to
      // @media (min-width:0px) { flex-direction:column } and
      // @media (min-width:900px) { flex-direction:row }; jsdom never evaluates the
      // media queries, so inspect the injected emotion stylesheet directly.
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      // Walk up from the title to the outermost MuiStack ancestor (the header row).
      const title = screen.getByText('eventPage.participantsTab.title');
      const stacks: HTMLElement[] = [];
      let node = title.parentElement;
      while (node) {
        if (node.classList.contains('MuiStack-root')) stacks.push(node);
        node = node.parentElement;
      }
      const headerStack = stacks[stacks.length - 1];
      expect(headerStack).toBeTruthy();
      const cssClass = Array.from(headerStack.classList).find((c) => c.startsWith('css-'));
      expect(cssClass).toBeTruthy();

      let css = '';
      document.querySelectorAll('style').forEach((styleEl) => {
        const text = styleEl.textContent ?? '';
        if (cssClass && text.includes(`.${cssClass}`)) css += text + '\n';
      });

      // xs base (min-width:0px) -> flex-direction:column
      expect(
        new RegExp(
          `@media\\s*\\(min-width:\\s*0px\\)\\s*\\{[^}]*flex-direction:\\s*column[^}]*\\}`
        ).test(css)
      ).toBe(true);
      // md+ (min-width:900px) -> flex-direction:row
      expect(
        new RegExp(
          `@media\\s*\\(min-width:\\s*900px\\)\\s*\\{[^}]*flex-direction:\\s*row[^}]*\\}`
        ).test(css)
      ).toBe(true);
    });
  });

  describe('Event Data', () => {
    it('should handle event without registration count', () => {
      const eventNoCount = {
        ...mockEvent,
        registrationCount: undefined,
      };

      renderWithProviders(<EventParticipantsTab event={eventNoCount as any} />);

      expect(screen.getByText('0')).toBeInTheDocument();
    });

    it('should use event code from event prop', () => {
      const customEvent = {
        ...mockEvent,
        eventCode: 'CUSTOM-2024',
      };

      renderWithProviders(<EventParticipantsTab event={customEvent} />);

      expect(screen.getByText('List for CUSTOM-2024')).toBeInTheDocument();
    });
  });

  describe('Enrol & waitlist relocation (Epic 14 FR25 / FR28)', () => {
    it('renders the Enrol organizers & partners action in the header', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      expect(screen.getByTestId('enroll-stakeholders-button')).toBeInTheDocument();
    });

    it('calls enrollStakeholders with the event code when clicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      await user.click(screen.getByTestId('enroll-stakeholders-button'));
      await waitFor(() => expect(enrollStakeholders).toHaveBeenCalledWith('BAT-2024-01'));
    });

    it('no longer renders the separate waitlist accordion (folded into the list filter)', () => {
      const eventWithCapacity = {
        ...mockEvent,
        registrationCapacity: 180,
        confirmedCount: 128,
        waitlistCount: 12,
      };
      renderWithProviders(<EventParticipantsTab event={eventWithCapacity} />);

      // The old WaitlistSection accordion header is gone; the list (which now owns
      // the Waitlisted filter) is still rendered.
      expect(
        screen.queryByText('eventPage.participantsTab.waitlistSection')
      ).not.toBeInTheDocument();
      expect(screen.getByTestId('participant-list')).toBeInTheDocument();
    });
  });

  describe('Layout', () => {
    it('should render in a container box', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      const container = screen.getByTestId('participant-list').parentElement;
      expect(container).toBeInTheDocument();
    });

    it('should have proper spacing', () => {
      const { container } = renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      // Component should have proper MUI Box structure
      const boxes = container.querySelectorAll('.MuiBox-root');
      expect(boxes.length).toBeGreaterThan(0);
    });
  });

  describe('XLSX export button (auto-participant-email-aliases-excel-export)', () => {
    const SENTINEL_URL = 'blob:http://localhost/sentinel';

    /**
     * Patch document.createElement('a') + URL.createObjectURL only AFTER render
     * so we don't interfere with React's own DOM construction.
     */
    function installAnchorSpies(): {
      mockAnchor: { href: string; download: string; click: ReturnType<typeof vi.fn> };
      restore: () => void;
    } {
      const mockAnchor = {
        href: '',
        download: '',
        click: vi.fn(),
      };
      const realCreateElement = document.createElement.bind(document);
      const createElementSpy = vi
        .spyOn(document, 'createElement')
        .mockImplementation((tagName: string) => {
          if (tagName === 'a') {
            return mockAnchor as unknown as HTMLAnchorElement;
          }
          return realCreateElement(tagName);
        });
      const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue(SENTINEL_URL);
      const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
      // The mock anchor is not a real DOM Node — short-circuit appendChild/removeChild
      // for it so the download flow doesn't throw when the component attaches the link.
      const appendChildSpy = vi
        .spyOn(document.body, 'appendChild')
        .mockImplementation((node) => node as Node);
      const removeChildSpy = vi
        .spyOn(document.body, 'removeChild')
        .mockImplementation((node) => node as Node);

      return {
        mockAnchor,
        restore: () => {
          createElementSpy.mockRestore();
          createObjectURLSpy.mockRestore();
          revokeObjectURLSpy.mockRestore();
          appendChildSpy.mockRestore();
          removeChildSpy.mockRestore();
        },
      };
    }

    beforeEach(() => {
      vi.mocked(eventApiClient.exportParticipantsXlsx).mockReset();
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('should_renderExportButton_when_componentMounts', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);

      const button = screen.getByTestId('participants-export-xlsx');
      expect(button).toBeInTheDocument();
      expect(button).toHaveTextContent('event.participants.exportNameBadges');
      expect(button).not.toBeDisabled();
    });

    it('should_invokeExportService_when_buttonClicked', async () => {
      const user = userEvent.setup();
      vi.mocked(eventApiClient.exportParticipantsXlsx).mockResolvedValue(
        new Blob(['xlsx-bytes'], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        })
      );

      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      const { restore } = installAnchorSpies();

      try {
        await user.click(screen.getByTestId('participants-export-xlsx'));

        await waitFor(() => {
          expect(eventApiClient.exportParticipantsXlsx).toHaveBeenCalledWith('BAT-2024-01');
        });
      } finally {
        restore();
      }
    });

    it('should_triggerDownloadWithExpectedFilename_when_exportSucceeds', async () => {
      const user = userEvent.setup();
      vi.mocked(eventApiClient.exportParticipantsXlsx).mockResolvedValue(
        new Blob(['xlsx-bytes'], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        })
      );

      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      const { mockAnchor, restore } = installAnchorSpies();

      try {
        await user.click(screen.getByTestId('participants-export-xlsx'));

        await waitFor(() => {
          expect(mockAnchor.click).toHaveBeenCalled();
        });
        expect(URL.createObjectURL).toHaveBeenCalled();
        expect(mockAnchor.href).toBe(SENTINEL_URL);
        expect(mockAnchor.download).toBe('BAT-2024-01-namensschilder.xlsx');
      } finally {
        restore();
      }
    });

    it('should_reEnableButton_when_exportFails', async () => {
      const user = userEvent.setup();
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      vi.mocked(eventApiClient.exportParticipantsXlsx).mockRejectedValue(new Error('Server Error'));

      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      const { mockAnchor, restore } = installAnchorSpies();

      try {
        const button = screen.getByTestId('participants-export-xlsx');
        await user.click(button);

        // Wait for the rejected promise to settle and finally{} to run.
        await waitFor(() => {
          expect(button).not.toBeDisabled();
        });

        // The error message renders an Alert in the UI; no unhandled exception.
        expect(await screen.findByText('Server Error')).toBeInTheDocument();
        expect(mockAnchor.click).not.toHaveBeenCalled();
      } finally {
        restore();
        consoleErrorSpy.mockRestore();
      }
    });
  });

  describe('DOCX export button (Avery L4784 name-badge printable sheet)', () => {
    const SENTINEL_URL = 'blob:http://localhost/sentinel-docx';

    function installAnchorSpies(): {
      mockAnchor: { href: string; download: string; click: ReturnType<typeof vi.fn> };
      restore: () => void;
    } {
      const mockAnchor = { href: '', download: '', click: vi.fn() };
      const realCreateElement = document.createElement.bind(document);
      const createElementSpy = vi
        .spyOn(document, 'createElement')
        .mockImplementation((tagName: string) => {
          if (tagName === 'a') {
            return mockAnchor as unknown as HTMLAnchorElement;
          }
          return realCreateElement(tagName);
        });
      const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue(SENTINEL_URL);
      const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
      const appendChildSpy = vi
        .spyOn(document.body, 'appendChild')
        .mockImplementation((node) => node as Node);
      const removeChildSpy = vi
        .spyOn(document.body, 'removeChild')
        .mockImplementation((node) => node as Node);
      return {
        mockAnchor,
        restore: () => {
          createElementSpy.mockRestore();
          createObjectURLSpy.mockRestore();
          revokeObjectURLSpy.mockRestore();
          appendChildSpy.mockRestore();
          removeChildSpy.mockRestore();
        },
      };
    }

    beforeEach(() => {
      vi.mocked(eventApiClient.exportParticipantsDocx).mockReset();
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('should_renderDocxExportButton_when_componentMounts', () => {
      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      const button = screen.getByTestId('participants-export-docx');
      expect(button).toBeInTheDocument();
      expect(button).toHaveTextContent('event.participants.exportNameBadgesDocx');
      expect(button).not.toBeDisabled();
    });

    it('should_invokeDocxExportService_when_buttonClicked', async () => {
      const user = userEvent.setup();
      vi.mocked(eventApiClient.exportParticipantsDocx).mockResolvedValue(
        new Blob(['docx-bytes'], {
          type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        })
      );

      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      const { restore } = installAnchorSpies();

      try {
        await user.click(screen.getByTestId('participants-export-docx'));
        await waitFor(() => {
          expect(eventApiClient.exportParticipantsDocx).toHaveBeenCalledWith('BAT-2024-01');
        });
      } finally {
        restore();
      }
    });

    it('should_triggerDocxDownloadWithExpectedFilename_when_exportSucceeds', async () => {
      const user = userEvent.setup();
      vi.mocked(eventApiClient.exportParticipantsDocx).mockResolvedValue(
        new Blob(['docx-bytes'], {
          type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        })
      );

      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      const { mockAnchor, restore } = installAnchorSpies();

      try {
        await user.click(screen.getByTestId('participants-export-docx'));
        await waitFor(() => {
          expect(mockAnchor.click).toHaveBeenCalled();
        });
        expect(URL.createObjectURL).toHaveBeenCalled();
        expect(mockAnchor.href).toBe(SENTINEL_URL);
        expect(mockAnchor.download).toBe('BAT-2024-01-namensschilder.docx');
      } finally {
        restore();
      }
    });

    it('should_reEnableDocxButton_when_exportFails', async () => {
      const user = userEvent.setup();
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      vi.mocked(eventApiClient.exportParticipantsDocx).mockRejectedValue(new Error('Word boom'));

      renderWithProviders(<EventParticipantsTab event={mockEvent} />);
      const { mockAnchor, restore } = installAnchorSpies();

      try {
        const button = screen.getByTestId('participants-export-docx');
        await user.click(button);

        await waitFor(() => {
          expect(button).not.toBeDisabled();
        });
        expect(await screen.findByText('Word boom')).toBeInTheDocument();
        expect(mockAnchor.click).not.toHaveBeenCalled();
      } finally {
        restore();
        consoleErrorSpy.mockRestore();
      }
    });
  });
});
