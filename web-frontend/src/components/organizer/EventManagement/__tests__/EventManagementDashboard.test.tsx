/**
 * EventManagementDashboard (Story 2.5.3, AC1 + AC2)
 *
 * The real tree runs against the MSW harness (17.0c2): `useEvents`, `useNotifications`, the
 * notification WebSocket hook and client, the persisted `eventStore`, the children (EventSearch,
 * EventList/EventCard, EventPagination, QuickActions, TaskWidget, TeamActivityFeed, EventForm)
 * and en i18n. `GET /events`, `GET /notifications` and `GET /tasks/my-tasks` are checked against
 * the specs and answered with typed bodies. Auth comes from the real `AuthContext` (`renderWithAuth`).
 *
 * The only mocks are the third-party STOMP/SockJS packages (a network boundary, plan Decision 3):
 * jsdom's hostname is `localhost`, so our `notificationWebSocketClient` really connects; the fake
 * `Client` records its config so a test can play the server (connect, push a message).
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, delay } from 'msw';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { renderWithAuth, signedInAs } from '@/test/auth';
import { anEventTask, daysFromNow, futureEvent, type Event } from '@/test/fixtures';
import { useEventStore } from '@/stores/eventStore';
import { EventManagementDashboard } from '../EventManagementDashboard';

// --- Boundary: the third-party STOMP/SockJS packages -------------------------------------------

interface FakeStompConfig {
  onConnect?: () => void;
  webSocketFactory?: () => unknown;
}

const stomp = vi.hoisted(() => ({
  configs: [] as FakeStompConfig[],
  subscriptions: [] as Array<{ topic: string; callback: (message: { body: string }) => void }>,
  activations: 0,
}));

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    constructor(config: FakeStompConfig) {
      stomp.configs.push(config);
    }
    activate() {
      stomp.activations += 1;
    }
    deactivate() {}
    subscribe(topic: string, callback: (message: { body: string }) => void) {
      stomp.subscriptions.push({ topic, callback });
      return { unsubscribe: () => {} };
    }
  },
}));

vi.mock('sockjs-client', () => ({ default: vi.fn() }));

// --- Data ----------------------------------------------------------------------------------------

type EventList = ResponseBody<'listEvents', 200>;
type Notifications = ResponseBody<'listNotifications', 200>;
type MyTasks = ResponseBody<'getMyTasks', 200>;
type Task = MyTasks[number];
type NotificationRow = NonNullable<Notifications['data']>[number];

const ORGANIZER = 'anna.keller'; // the username of signedInAs('organizer')

const bb61 = futureEvent(40);
const bb62 = futureEvent(200, {
  eventCode: 'BATbern62',
  eventNumber: 62,
  title: 'Datensouveränität: Cloud in der Schweiz',
  workflowState: 'CREATED',
});

const eventPage = (data: Event[]): EventList => ({
  data,
  pagination: {
    page: 1,
    limit: 20,
    totalItems: data.length,
    totalPages: data.length === 0 ? 0 : 1,
    hasNext: false,
    hasPrev: false,
  },
});

const aTask = (overrides: Partial<Task> = {}): Task => ({
  ...anEventTask(),
  id: 'b7a1c2d3-0000-4000-8000-000000000001',
  eventId: 'e1a2b3c4-0000-4000-8000-000000000061',
  eventCode: 'BATbern61',
  taskName: 'Overdue speaker materials',
  triggerState: 'SPEAKER_CONFIRMATION',
  dueDate: daysFromNow(-2),
  assignedOrganizerUsername: ORGANIZER,
  status: 'todo',
  notes: '3 speakers have not submitted materials',
  createdAt: daysFromNow(-30),
  updatedAt: daysFromNow(-30),
  ...overrides,
});

const aNotification = (overrides: Partial<NotificationRow> = {}): NotificationRow => ({
  id: 'c1d2e3f4-0000-4000-8000-000000000001',
  recipientUsername: ORGANIZER,
  eventCode: 'BATbern61',
  notificationType: 'TASK_ASSIGNED',
  channel: 'IN_APP',
  priority: 'NORMAL',
  subject: 'Lukas Meier assigned you a task',
  body: 'Assigned speaker outreach for BATbern61',
  status: 'UNREAD',
  sentAt: daysFromNow(-1),
  readAt: null,
  failedAt: null,
  failureReason: null,
  metadata: null,
  createdAt: daysFromNow(-1),
  updatedAt: daysFromNow(-1),
  ...overrides,
});

const notificationPage = (data: NotificationRow[]): Notifications => ({
  data,
  pagination: { page: 1, limit: 10, totalItems: data.length, totalPages: 1 },
});

const renderDashboard = () =>
  renderWithAuth(<EventManagementDashboard />, { auth: signedInAs('organizer') });

/** The dashboard has rendered past the loader and the event list has its data. */
const findLoadedDashboard = async () => {
  const container = await screen.findByTestId('dashboard-container');
  await waitFor(() => expect(screen.queryByTestId('skeleton-card-1')).not.toBeInTheDocument());
  return container;
};

describe('EventManagementDashboard Component', () => {
  beforeEach(() => {
    localStorage.clear();
    useEventStore.setState({
      filters: { includeArchived: false },
      pagination: { page: 1, limit: 20 },
      selectedEventCode: undefined,
      isCreateModalOpen: false,
      isEditModalOpen: false,
    });
    stomp.configs.length = 0;
    stomp.subscriptions.length = 0;
    stomp.activations = 0;

    api.on('listEvents', () => eventPage([bb61, bb62]));
    api.on('listNotifications', () => notificationPage([aNotification()]));
    api.on('getMyTasks', () => [aTask()]);
  });

  describe('Initial Render', () => {
    it('should_renderDashboardTitle_when_componentMounts', async () => {
      renderDashboard();

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Event Management' })
      ).toBeInTheDocument();
    });

    it('should_displayLoadingState_when_dataFetching', async () => {
      api.on('listEvents', async () => {
        await delay('infinite');
        return eventPage([]);
      });
      api.on('listNotifications', async () => {
        await delay('infinite');
        return notificationPage([]);
      });

      renderDashboard();

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      expect(screen.queryByTestId('dashboard-container')).not.toBeInTheDocument();
      // Both requests are in flight: the loader waits for them, not for nothing.
      await waitFor(() => expect(api.calls('listEvents')).toHaveLength(1));
      await waitFor(() => expect(api.calls('listNotifications')).toHaveLength(1));
    });

    it('should_displayErrorMessage_when_eventsLoadFails', async () => {
      api.on('listEvents', () =>
        HttpResponse.json(
          { status: 500, error: 'Internal Server Error', message: 'Database unavailable' },
          { status: 500 }
        )
      );

      renderDashboard();

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent(
        'Failed to load events: Server Error: Please try again later'
      );
      expect(screen.queryByTestId('dashboard-container')).not.toBeInTheDocument();
    });
  });

  describe('Active Events Pipeline (AC1)', () => {
    it('should_displayActiveEventsSection_when_eventsLoaded', async () => {
      renderDashboard();
      await findLoadedDashboard();

      expect(screen.getByRole('heading', { name: 'Active Events' })).toBeInTheDocument();
      expect(screen.getByText('2/2 events')).toBeInTheDocument();
    });

    it('should_displayEventCards_when_eventsExist', async () => {
      renderDashboard();

      expect(await screen.findByText(bb61.title)).toBeInTheDocument();
      expect(screen.getByText(bb62.title)).toBeInTheDocument();
      expect(screen.getAllByTestId('event-title').map((title) => title.textContent)).toEqual([
        bb61.title,
        bb62.title,
      ]);

      // The dashboard asks for page 1 of 20, without archived events, with registration counts.
      const query = api.calls('listEvents')[0].query;
      expect(query.get('page')).toBe('1');
      expect(query.get('limit')).toBe('20');
      expect(query.get('includeArchived')).toBe('false');
      expect(query.get('include')).toBe('registrations');
    });

    it('should_displayEmptyState_when_noEvents', async () => {
      api.on('listEvents', () => eventPage([]));

      renderDashboard();

      expect(await screen.findByText('No events found')).toBeInTheDocument();
      expect(screen.getByTestId('event-list-empty')).toBeInTheDocument();
      expect(screen.queryByTestId('event-list-container')).not.toBeInTheDocument();
      // Positive control: the rest of the dashboard rendered.
      expect(screen.getByRole('heading', { name: 'Active Events' })).toBeInTheDocument();
    });
  });

  describe('Critical Tasks Section (AC1)', () => {
    it('should_displayCriticalTasksSection_when_tasksExist', async () => {
      renderDashboard();

      const section = await screen.findByTestId('critical-tasks-section');
      expect(within(section).getByText('My open tasks')).toBeInTheDocument();
      await waitFor(() => expect(api.calls('getMyTasks')).toHaveLength(1));
    });

    it('should_displayTaskCount_when_tasksLoaded', async () => {
      // Only the open task counts: completed, cancelled and not-yet-due pending tasks are hidden.
      api.on('getMyTasks', () => [
        aTask(),
        aTask({
          id: 'b7a1c2d3-0000-4000-8000-000000000002',
          taskName: 'Book the venue',
          status: 'completed',
        }),
        aTask({
          id: 'b7a1c2d3-0000-4000-8000-000000000003',
          taskName: 'Order the wine',
          status: 'cancelled',
        }),
        aTask({
          id: 'b7a1c2d3-0000-4000-8000-000000000004',
          taskName: 'Send the final newsletter',
          status: 'pending',
          dueDate: daysFromNow(20),
        }),
      ]);

      renderDashboard();

      const section = await screen.findByTestId('critical-tasks-section');
      expect(await within(section).findByText('1 task')).toBeInTheDocument();
      expect(within(section).getByText('Overdue speaker materials')).toBeInTheDocument();
      expect(within(section).queryByText('Book the venue')).not.toBeInTheDocument();
      expect(within(section).queryByText('Order the wine')).not.toBeInTheDocument();
      expect(within(section).queryByText('Send the final newsletter')).not.toBeInTheDocument();
    });

    it('should_displayTaskPriority_when_criticalTask', async () => {
      renderDashboard();

      const section = await screen.findByTestId('critical-tasks-section');
      expect(await within(section).findByText('Overdue speaker materials')).toBeInTheDocument();
      expect(within(section).getByTestId('critical-tasks-list')).toBeInTheDocument();
    });
  });

  describe('Team Activity Feed (AC1)', () => {
    it('should_displayTeamActivitySection_when_activityExists', async () => {
      renderDashboard();

      const section = await screen.findByTestId('team-activity-section');
      expect(await within(section).findByText('Notifications')).toBeInTheDocument();
      expect(within(section).getByTestId('team-activity-feed')).toBeInTheDocument();
    });

    it('should_displayActivityFeed_when_activitiesLoaded', async () => {
      renderDashboard();

      const section = await screen.findByTestId('team-activity-section');
      expect(
        await within(section).findByText('Lukas Meier assigned you a task')
      ).toBeInTheDocument();
      expect(
        within(section).getByText('Assigned speaker outreach for BATbern61')
      ).toBeInTheDocument();

      // The signed-in organizer's first ten unread notifications.
      const query = api.calls('listNotifications')[0].query;
      expect(query.get('username')).toBe(ORGANIZER);
      expect(query.get('status')).toBe('UNREAD');
      expect(query.get('page')).toBe('1');
      expect(query.get('limit')).toBe('10');
    });

    it('should_refetchNotifications_when_realTimeNotificationArrives', async () => {
      renderDashboard();
      const section = await screen.findByTestId('team-activity-section');
      await within(section).findByText('Lukas Meier assigned you a task');
      expect(api.calls('listNotifications')).toHaveLength(1);

      // Our client connected for the signed-in organizer through the STOMP package.
      await waitFor(() => expect(stomp.activations).toBe(1));
      api.on('listNotifications', () =>
        notificationPage([
          aNotification({
            id: 'c1d2e3f4-0000-4000-8000-000000000002',
            subject: 'Sara Brunner accepted the invitation',
            body: 'Speaker accepted for BATbern61',
            notificationType: 'SPEAKER_ACCEPTED',
          }),
          aNotification(),
        ])
      );

      // The server confirms the connection, then pushes a notification on the user's topic.
      act(() => stomp.configs[0].onConnect?.());
      await waitFor(() => expect(stomp.subscriptions).toHaveLength(1));
      expect(stomp.subscriptions[0].topic).toBe(`/topic/notifications/${ORGANIZER}`);
      act(() =>
        stomp.subscriptions[0].callback({ body: JSON.stringify(aNotification({ id: 'x' })) })
      );

      expect(
        await within(section).findByText('Sara Brunner accepted the invitation')
      ).toBeInTheDocument();
      expect(api.calls('listNotifications')).toHaveLength(2);
    });
  });

  describe('Quick Actions Sidebar (AC1)', () => {
    it('should_displayNewEventButton_when_dashboardLoaded', async () => {
      const user = userEvent.setup();
      renderDashboard();
      await findLoadedDashboard();

      const newEventButton = screen.getByRole('button', { name: 'New Event' });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      await user.click(newEventButton);

      expect(useEventStore.getState().isCreateModalOpen).toBe(true);
      expect(await screen.findByRole('dialog')).toBeInTheDocument();
    });
  });

  describe('Responsive Layout', () => {
    it('should_useGridLayout_when_desktopView', async () => {
      renderDashboard();

      const container = await findLoadedDashboard();
      expect(container.querySelector('.MuiGrid-root')).toBeInTheDocument();
    });

    it('should_displayAllSections_when_rendered', async () => {
      renderDashboard();
      await findLoadedDashboard();

      expect(screen.getByRole('heading', { name: 'Active Events' })).toBeInTheDocument();
      expect(screen.getByTestId('quick-actions')).toBeInTheDocument();
      expect(
        await within(screen.getByTestId('critical-tasks-section')).findByText('My open tasks')
      ).toBeInTheDocument();
      expect(
        await within(screen.getByTestId('team-activity-section')).findByText('Notifications')
      ).toBeInTheDocument();
    });
  });

  describe('Internationalization (AC22)', () => {
    it('should_useTranslationKeys_when_renderingText', async () => {
      renderDashboard();
      await findLoadedDashboard();

      // en values of events:dashboard.*; a missing key would render the raw key instead.
      expect(
        screen.getByRole('heading', { level: 1, name: 'Event Management' })
      ).toBeInTheDocument();
      expect(screen.getByText('Quick Actions')).toBeInTheDocument();
      expect(screen.queryByText(/dashboard\.\w+/)).not.toBeInTheDocument();
    });
  });
});
