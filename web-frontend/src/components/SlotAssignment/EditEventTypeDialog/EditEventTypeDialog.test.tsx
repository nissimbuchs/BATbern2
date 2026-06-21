/**
 * Story 15.2 — EditEventTypeDialog tests.
 *
 * Covers: pre-fill from the resolved config (GET), copy-on-edit save (PUT) with the full
 * knob payload, and the non-blocking assignment-desync warning.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';
import { render, screen, waitFor } from '@/test/test-utils';
import { EditEventTypeDialog } from './EditEventTypeDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, def?: string) => def || key }),
}));

vi.mock('@/services/timetableService/timetableService', () => ({
  timetableService: {
    getAgendaConfig: vi.fn(),
    updateAgendaConfig: vi.fn(),
  },
}));

import { timetableService } from '@/services/timetableService/timetableService';

const mockGet = vi.mocked(timetableService.getAgendaConfig);
const mockUpdate = vi.mocked(timetableService.updateAgendaConfig);

const TEMPLATE_CONFIG = {
  source: 'TEMPLATE' as const,
  minSlots: 6,
  maxSlots: 8,
  slotDuration: 45,
  theoreticalSlotsAM: false,
  breakSlots: 1,
  lunchSlots: 0,
  defaultCapacity: 200,
  moderationStartDuration: 5,
  moderationEndDuration: 5,
  breakDuration: 20,
  lunchDuration: 60,
  aperitifSlots: 1,
  aperitifDuration: 90,
  aperitifPosition: 'end' as const,
  typicalStartTime: '13:00',
  typicalEndTime: '19:00',
};

describe('EditEventTypeDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({ ...TEMPLATE_CONFIG });
    mockUpdate.mockResolvedValue({ ...TEMPLATE_CONFIG, source: 'EVENT_OVERRIDE' });
  });

  it('pre-fills fields from the resolved config when opened', async () => {
    render(<EditEventTypeDialog eventCode="BATbern90" open onClose={vi.fn()} />);

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('BATbern90');
    });
    const aperitifDuration = await screen.findByTestId('field-aperitifDuration');
    await waitFor(() => expect(aperitifDuration).toHaveValue(90));
    expect(screen.getByTestId('field-maxSlots')).toHaveValue(8);
  });

  it('saves the per-event override with the full knob payload', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<EditEventTypeDialog eventCode="BATbern90" open onClose={onClose} />);

    const breakField = await screen.findByTestId('field-breakSlots');
    await waitFor(() => expect(breakField).toHaveValue(1));
    await user.clear(breakField);
    await user.type(breakField, '2');

    await user.click(screen.getByTestId('edit-event-type-save'));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledTimes(1);
    });
    const [eventCode, payload] = mockUpdate.mock.calls[0];
    expect(eventCode).toBe('BATbern90');
    expect(payload.breakSlots).toBe(2);
    expect(payload.aperitifPosition).toBe('end');
    expect(payload.aperitifDuration).toBe(90);
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('shows a non-blocking assignment warning when speakers are already assigned', async () => {
    render(<EditEventTypeDialog eventCode="BATbern90" open onClose={vi.fn()} hasAssignments />);

    expect(await screen.findByTestId('edit-event-type-assignment-warning')).toBeInTheDocument();
    // Save is still enabled (warn, don't block) once the config has loaded
    await waitFor(() => expect(screen.getByTestId('edit-event-type-save')).toBeEnabled());
  });
});
