/**
 * taskService against the MSW harness (Story 5.5, moved off the apiClient mock in Story 4.9).
 *
 * The real apiClient sends each call; the harness checks method, path and query against
 * `docs/api/event-tasks-api.openapi.yml` and answers with the spec example or a typed override.
 * Status changes, completion and reassignment all go through `PATCH /tasks/{taskId}`: the former
 * verb endpoints (`/complete`, `/reassign`, `/status`) are gone, and no method reaches them.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { taskService, type EventTaskResponse } from './taskService';

const TASK_ID = '3cf5db30-92c0-44d2-bb74-4b0f2b4aa574';
const TEMPLATE_ID = '47540622-0710-4396-a8d4-e0a7d952f86a';

const aTask = (over: Partial<EventTaskResponse> = {}): EventTaskResponse => ({
  id: TASK_ID,
  eventId: '8dd9c9a4-35c1-4adb-b1aa-ef81e7b65fe3',
  eventCode: 'BATbern60',
  templateId: TEMPLATE_ID,
  taskName: 'Wein',
  triggerState: 'agenda_published',
  dueDate: '2026-11-06T00:00:00Z',
  assignedOrganizerUsername: 'carla.muster',
  status: 'todo',
  notes: null,
  completedDate: null,
  completedByUsername: null,
  createdAt: '2026-10-25T09:46:11.349885Z',
  updatedAt: '2026-10-25T09:46:11.350173Z',
  ...over,
});

const problem = (status: number, message: string) =>
  HttpResponse.json(
    { timestamp: '2026-11-09T10:00:00Z', path: '/api/v1/tasks', status, error: 'x', message },
    { status }
  );

/** What a rejected axios call carries. */
const rejection = (promise: Promise<unknown>) =>
  promise.then(
    () => {
      throw new Error('expected the call to reject');
    },
    (e: { response?: { status: number } }) => e
  );

describe('taskService', () => {
  describe('templates', () => {
    it('lists all templates from GET /tasks/templates', async () => {
      const templates = await taskService.listAllTemplates();

      const [call] = api.calls('listAllTemplates');
      expect(call.method).toBe('GET');
      expect(Array.isArray(templates)).toBe(true);
      expect(templates.length).toBeGreaterThan(0);
    });

    it('creates a template with POST and the request body', async () => {
      const request = {
        name: 'Order catering',
        triggerState: 'agenda_published',
        dueDateType: 'relative_to_event',
        dueDateOffsetDays: -7,
        saveAsTemplate: true,
      };

      await taskService.createTemplate(request);

      const [call] = api.calls('createTemplate');
      expect(call.method).toBe('POST');
      expect(call.body).toEqual(request);
    });

    it('updates a template with PUT on its id', async () => {
      const request = {
        name: 'Renamed',
        triggerState: 'topic_selection',
        dueDateType: 'immediate',
      };

      await taskService.updateTemplate(TEMPLATE_ID, request);

      const [call] = api.calls('updateTemplate');
      expect(call.method).toBe('PUT');
      expect(call.params).toEqual({ templateId: TEMPLATE_ID });
      expect(call.body).toEqual(request);
    });

    it('deletes a template and rejects a default template with 403', async () => {
      await taskService.deleteTemplate(TEMPLATE_ID);
      expect(api.calls('deleteTemplate')[0].params).toEqual({ templateId: TEMPLATE_ID });

      api.on('deleteTemplate', () => problem(403, 'Cannot delete a default template'));
      const error = await rejection(taskService.deleteTemplate(TEMPLATE_ID));
      expect(error.response?.status).toBe(403);
    });
  });

  describe('event tasks', () => {
    it('lists the tasks of an event by event code', async () => {
      api.on('listEventTasks', () => [aTask(), aTask({ id: 'b', taskName: 'Namensschilder' })]);

      const tasks = await taskService.listEventTasks('BATbern60');

      expect(api.calls('listEventTasks')[0].params).toEqual({ eventCode: 'BATbern60' });
      expect(tasks.map((t) => t.taskName)).toEqual(['Wein', 'Namensschilder']);
    });

    it('creates an ad-hoc task and tasks from templates', async () => {
      await taskService.createAdHocTask('BATbern60', {
        taskName: 'Budget review',
        triggerState: 'topic_selection',
        assignedOrganizerUsername: 'carla.muster',
      });
      await taskService.createTasksFromTemplates('BATbern60', {
        templates: [{ templateId: TEMPLATE_ID, assignedOrganizerUsername: 'carla.muster' }],
      });

      expect(api.calls('createAdHocTask')[0].body).toMatchObject({ taskName: 'Budget review' });
      expect(api.calls('createTasksFromTemplates')[0].body).toEqual({
        templates: [{ templateId: TEMPLATE_ID, assignedOrganizerUsername: 'carla.muster' }],
      });
    });

    it('reads my tasks and all tasks without a filter parameter', async () => {
      await taskService.getMyTasks();
      await taskService.getAllTasks();

      expect(api.calls('getMyTasks')[0].query.toString()).toBe('');
      expect(api.calls('getAllTasks')).toHaveLength(1);
    });
  });

  describe('updateTask (PATCH /tasks/{taskId})', () => {
    it('completes a task with status completed and notes', async () => {
      api.on('updateTask', ({ body }) =>
        aTask({
          status: 'completed',
          notes: body?.notes ?? null,
          completedDate: '2026-11-09T10:00:00Z',
          completedByUsername: 'carla.muster',
        })
      );

      const result = await taskService.updateTask(TASK_ID, {
        status: 'completed',
        notes: 'Wine ordered',
      });

      const [call] = api.calls('updateTask');
      expect(call.method).toBe('PATCH');
      expect(call.params).toEqual({ taskId: TASK_ID });
      expect(call.body).toEqual({ status: 'completed', notes: 'Wine ordered' });
      expect(result.status).toBe('completed');
      expect(result.completedByUsername).toBe('carla.muster');
    });

    it('moves a task on the board with only the status', async () => {
      await taskService.updateTask(TASK_ID, { status: 'in_progress' });

      expect(api.calls('updateTask')[0].body).toEqual({ status: 'in_progress' });
    });

    it('reassigns and unassigns with assignedOrganizerUsername', async () => {
      await taskService.updateTask(TASK_ID, { assignedOrganizerUsername: 'bob.organizer' });
      await taskService.updateTask(TASK_ID, { assignedOrganizerUsername: '' });

      expect(api.calls('updateTask').map((c) => c.body)).toEqual([
        { assignedOrganizerUsername: 'bob.organizer' },
        { assignedOrganizerUsername: '' },
      ]);
    });

    it('rejects with the 400 EMS answers for an unknown status', async () => {
      api.on('updateTask', () => problem(400, 'Invalid status'));

      const error = await rejection(
        taskService.updateTask(TASK_ID, { status: 'done' as 'completed' })
      );

      expect(error.response?.status).toBe(400);
    });

    it('rejects with 404 for an unknown task', async () => {
      api.on('updateTask', () => problem(404, 'Task not found'));

      const error = await rejection(taskService.updateTask(TASK_ID, { notes: 'x' }));

      expect(error.response?.status).toBe(404);
    });
  });

  describe('deleteTask', () => {
    it('deletes a task with DELETE /tasks/{taskId}', async () => {
      await taskService.deleteTask(TASK_ID);

      const [call] = api.calls('deleteTask');
      expect(call.method).toBe('DELETE');
      expect(call.params).toEqual({ taskId: TASK_ID });
    });
  });
});
