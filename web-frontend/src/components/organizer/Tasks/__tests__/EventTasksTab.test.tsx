/**
 * EventTasksTab tests (#1223 items 5 and 6).
 *
 * The template checklist of an event: the due offset of each template reads "N days before/after
 * the event", and the intro says when ticked tasks are created (on save in the event form, at once
 * in the live Details → Tasks view). Real i18next, real taskService and organizer directory on the
 * MSW harness.
 */
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api } from '@/test/msw/api';
import { renderWithAuth } from '@/test/auth';
import type { TaskTemplateResponse } from '@/services/taskService';
import { EventTasksTab } from '../EventTasksTab';

const template = (over: Partial<TaskTemplateResponse>): TaskTemplateResponse => ({
  id: '0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4',
  name: 'Mailing: Save the date',
  isDefault: true,
  triggerState: 'created',
  dueDateType: 'relative_to_event',
  dueDateOffsetDays: -90,
  activationLeadDays: null,
  createdByUsername: null,
  createdAt: '2026-11-01T08:00:00Z',
  updatedAt: '2026-11-01T08:00:00Z',
  ...over,
});

const SAVE_THE_DATE = template({});
const SLIDES_ONLINE = template({
  id: '1c2d3e4f-5a6b-4c7d-9e8f-0a1b2c3d4e5f',
  name: 'Newsletter: Slides Are Online',
  dueDateOffsetDays: 1,
});
const BRIEFING = template({
  id: '2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a',
  name: 'Event day briefing',
  dueDateOffsetDays: 0,
});
const CUSTOM = template({
  id: '3e4f5a6b-7c8d-4e9f-8a1b-2c3d4e5f6a7b',
  name: 'Thank the venue',
  isDefault: false,
  dueDateOffsetDays: 3,
});

const NO_OFFSET = template({
  id: '4f5a6b7c-8d9e-4f0a-9b1c-2d3e4f5a6b7c',
  name: 'Room setup',
  dueDateOffsetDays: null,
});

const renderTab = (createsOnToggle?: boolean) =>
  renderWithAuth(
    <EventTasksTab
      eventId="BATbern61"
      organizerUsername="anna.keller"
      selectedTemplates={[]}
      templateAssignees={{}}
      customTasks={[]}
      onTemplateToggle={vi.fn()}
      onAssigneeChange={vi.fn()}
      onAddCustomTask={vi.fn()}
      onEditCustomTask={vi.fn()}
      onDeleteCustomTask={vi.fn()}
      createsOnToggle={createsOnToggle}
    />
  );

/** Hovers the info icon of a template row and returns the tooltip it opens. */
const tooltipOf = async (templateId: string) => {
  const user = userEvent.setup();
  const row = await screen.findByTestId(`task-template-${templateId}`);
  await user.hover(within(row).getByTestId('InfoIcon'));
  return screen.findByRole('tooltip');
};

describe('EventTasksTab', () => {
  beforeEach(() => {
    api.on('listAllTemplates', () => [SAVE_THE_DATE, SLIDES_ONLINE, BRIEFING, CUSTOM, NO_OFFSET]);
  });

  describe('due offset (#1223 item 5)', () => {
    it.each([
      [SAVE_THE_DATE, '90 days before the event'],
      [SLIDES_ONLINE, '1 day after the event'],
      [BRIEFING, 'On the event day'],
      [CUSTOM, '3 days after the event'],
      // the backend treats a missing offset as 0 (EventTaskService.calculateDueDate)
      [NO_OFFSET, 'On the event day'],
    ])('describes the offset of template %#', async (tmpl, text) => {
      renderTab();

      const tooltip = await tooltipOf(tmpl.id);

      expect(tooltip).toHaveTextContent(text);
      expect(tooltip).not.toHaveTextContent(/-\d/);
    });
  });

  describe('when ticked tasks are created (#1223 item 6)', () => {
    it('says they are created on save, in the event form', async () => {
      renderTab();

      expect(
        await screen.findByText(/will be created when you save the event/)
      ).toBeInTheDocument();
      expect(screen.queryByText(/creates its task right away/)).not.toBeInTheDocument();
    });

    it('says ticking creates the task at once, in the live view', async () => {
      renderTab(true);

      expect(
        await screen.findByText(/Ticking one creates its task right away/)
      ).toBeInTheDocument();
      expect(screen.queryByText(/when you save the event/)).not.toBeInTheDocument();
    });
  });
});
