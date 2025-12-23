/**
 * Task Service Tests (Story 5.5 Phase 6)
 *
 * Comprehensive tests for taskService HTTP client
 * Tests all API methods: template CRUD, event task management, critical tasks
 *
 * Coverage:
 * - API request formatting (task templates, event tasks, status updates)
 * - Response handling and error propagation
 * - Type safety and parameter validation
 * - AC19-27 (Task Templates, Event Tasks, Critical Tasks, Completion, Reassignment)
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { taskService } from './taskService';
import apiClient from './api/apiClient';
import type {
  TaskTemplateResponse,
  EventTaskResponse,
  CreateTaskTemplateRequest,
  UpdateTaskTemplateRequest,
  CreateEventTaskRequest,
  CreateTasksFromTemplatesRequest,
  CompleteTaskRequest,
  ReassignTaskRequest,
} from './taskService';

// Mock the apiClient module
vi.mock('./api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('taskService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('listAllTemplates (AC19, AC26)', () => {
    it('should fetch all task templates (default + custom)', async () => {
      const mockTemplates: TaskTemplateResponse[] = [
        {
          id: 'template-1',
          name: 'Send Newsletter',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDateType: 'relative_to_event',
          dueDateOffsetDays: -7,
          isDefault: true,
          createdByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
        {
          id: 'template-2',
          name: 'Order Catering',
          triggerState: 'SLOT_ASSIGNED',
          dueDateType: 'relative_to_event',
          dueDateOffsetDays: -3,
          isDefault: true,
          createdByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
        {
          id: 'template-3',
          name: 'Custom Task',
          triggerState: 'EVENT_PUBLISHED',
          dueDateType: 'immediate',
          dueDateOffsetDays: null,
          isDefault: false,
          createdByUsername: 'john.doe',
          createdAt: '2025-12-20T11:00:00Z',
          updatedAt: '2025-12-20T11:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTemplates });

      const result = await taskService.listAllTemplates();

      expect(apiClient.get).toHaveBeenCalledWith('/tasks/templates');
      expect(result).toEqual(mockTemplates);
      expect(result).toHaveLength(3);
      expect(result.filter((t) => t.isDefault)).toHaveLength(2);
      expect(result.filter((t) => !t.isDefault)).toHaveLength(1);
    });

    it('should return empty array when no templates exist', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await taskService.listAllTemplates();

      expect(result).toEqual([]);
      expect(result).toHaveLength(0);
    });

    it('should propagate authorization errors', async () => {
      const error = new Error('Unauthorized: JWT token required');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(taskService.listAllTemplates()).rejects.toThrow('Unauthorized');
    });

    it('should handle network failures gracefully', async () => {
      const error = new Error('Network error: timeout');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(taskService.listAllTemplates()).rejects.toThrow('Network error');
    });
  });

  describe('createTemplate (AC22, AC26)', () => {
    it('should create custom task template with all fields', async () => {
      const request: CreateTaskTemplateRequest = {
        name: 'Send Welcome Email',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDateType: 'relative_to_event',
        dueDateOffsetDays: -14,
        saveAsTemplate: true,
      };

      const mockResponse: TaskTemplateResponse = {
        id: 'template-new-123',
        name: 'Send Welcome Email',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDateType: 'relative_to_event',
        dueDateOffsetDays: -14,
        isDefault: false,
        createdByUsername: 'john.doe',
        createdAt: '2025-12-20T12:00:00Z',
        updatedAt: '2025-12-20T12:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await taskService.createTemplate(request);

      expect(apiClient.post).toHaveBeenCalledWith('/tasks/templates', request);
      expect(result).toEqual(mockResponse);
      expect(result.isDefault).toBe(false);
      expect(result.createdByUsername).toBe('john.doe');
    });

    it('should create template with immediate due date', async () => {
      const request: CreateTaskTemplateRequest = {
        name: 'Urgent Task',
        triggerState: 'EVENT_PUBLISHED',
        dueDateType: 'immediate',
      };

      const mockResponse: TaskTemplateResponse = {
        id: 'template-new-456',
        name: 'Urgent Task',
        triggerState: 'EVENT_PUBLISHED',
        dueDateType: 'immediate',
        dueDateOffsetDays: null,
        isDefault: false,
        createdByUsername: 'jane.smith',
        createdAt: '2025-12-20T13:00:00Z',
        updatedAt: '2025-12-20T13:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await taskService.createTemplate(request);

      expect(result.dueDateType).toBe('immediate');
      expect(result.dueDateOffsetDays).toBeNull();
    });

    it('should propagate validation errors for missing name', async () => {
      const request: CreateTaskTemplateRequest = {
        name: '',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDateType: 'immediate',
      };

      const error = new Error('Validation failed: name is required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(taskService.createTemplate(request)).rejects.toThrow('Validation failed');
    });

    it('should propagate validation errors for invalid trigger state', async () => {
      const request: CreateTaskTemplateRequest = {
        name: 'Test Task',
        triggerState: 'INVALID_STATE',
        dueDateType: 'immediate',
      };

      const error = new Error('Validation failed: invalid triggerState');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(taskService.createTemplate(request)).rejects.toThrow('Validation failed');
    });

    it('should propagate authorization errors for non-organizers', async () => {
      const request: CreateTaskTemplateRequest = {
        name: 'Test Task',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDateType: 'immediate',
      };

      const error = new Error('Forbidden: ORGANIZER role required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(taskService.createTemplate(request)).rejects.toThrow('Forbidden');
    });
  });

  describe('updateTemplate (AC26)', () => {
    it('should update custom template with all fields', async () => {
      const request: UpdateTaskTemplateRequest = {
        name: 'Updated Task Name',
        triggerState: 'SLOT_ASSIGNED',
        dueDateType: 'relative_to_event',
        dueDateOffsetDays: -5,
      };

      const mockResponse: TaskTemplateResponse = {
        id: 'template-123',
        name: 'Updated Task Name',
        triggerState: 'SLOT_ASSIGNED',
        dueDateType: 'relative_to_event',
        dueDateOffsetDays: -5,
        isDefault: false,
        createdByUsername: 'john.doe',
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T14:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.updateTemplate('template-123', request);

      expect(apiClient.put).toHaveBeenCalledWith('/tasks/templates/template-123', request);
      expect(result).toEqual(mockResponse);
      expect(result.name).toBe('Updated Task Name');
    });

    it('should update template with partial fields', async () => {
      const request: UpdateTaskTemplateRequest = {
        name: 'Partially Updated Task',
      };

      const mockResponse: TaskTemplateResponse = {
        id: 'template-456',
        name: 'Partially Updated Task',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDateType: 'immediate',
        dueDateOffsetDays: null,
        isDefault: false,
        createdByUsername: 'john.doe',
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T14:30:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.updateTemplate('template-456', request);

      expect(result.name).toBe('Partially Updated Task');
    });

    it('should propagate 404 errors for non-existent template', async () => {
      const request: UpdateTaskTemplateRequest = {
        name: 'Updated Name',
      };

      const error = new Error('Template not found: template-999');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(taskService.updateTemplate('template-999', request)).rejects.toThrow(
        'Template not found'
      );
    });

    it('should propagate 403 errors when trying to modify default template', async () => {
      const request: UpdateTaskTemplateRequest = {
        name: 'Cannot Update Default',
      };

      const error = new Error('Forbidden: Cannot modify default templates');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(taskService.updateTemplate('template-default-1', request)).rejects.toThrow(
        'Forbidden'
      );
    });
  });

  describe('deleteTemplate (AC26)', () => {
    it('should delete custom template', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined });

      await taskService.deleteTemplate('template-123');

      expect(apiClient.delete).toHaveBeenCalledWith('/tasks/templates/template-123');
    });

    it('should propagate 404 errors for non-existent template', async () => {
      const error = new Error('Template not found: template-999');
      vi.mocked(apiClient.delete).mockRejectedValue(error);

      await expect(taskService.deleteTemplate('template-999')).rejects.toThrow(
        'Template not found'
      );
    });

    it('should propagate 403 errors when trying to delete default template', async () => {
      const error = new Error('Forbidden: Cannot delete default templates');
      vi.mocked(apiClient.delete).mockRejectedValue(error);

      await expect(taskService.deleteTemplate('template-default-1')).rejects.toThrow('Forbidden');
    });
  });

  describe('listEventTasks (AC24)', () => {
    it('should fetch all tasks for an event', async () => {
      const mockTasks: EventTaskResponse[] = [
        {
          id: 'task-1',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-1',
          taskName: 'Send Newsletter',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDate: '2025-12-25T00:00:00Z',
          assignedOrganizerUsername: 'john.doe',
          status: 'todo',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
        {
          id: 'task-2',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-2',
          taskName: 'Order Catering',
          triggerState: 'SLOT_ASSIGNED',
          dueDate: '2025-12-28T00:00:00Z',
          assignedOrganizerUsername: 'jane.smith',
          status: 'pending',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTasks });

      const result = await taskService.listEventTasks('BATbern56');

      expect(apiClient.get).toHaveBeenCalledWith('/events/BATbern56/tasks');
      expect(result).toEqual(mockTasks);
      expect(result).toHaveLength(2);
    });

    it('should return empty array when event has no tasks', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await taskService.listEventTasks('BATbern99');

      expect(result).toEqual([]);
    });

    it('should propagate 404 errors for non-existent event', async () => {
      const error = new Error('Event not found: BATbern999');
      vi.mocked(apiClient.get).mockRejectedValue(error);

      await expect(taskService.listEventTasks('BATbern999')).rejects.toThrow('Event not found');
    });
  });

  describe('createAdHocTask (AC22)', () => {
    it('should create ad-hoc task with all fields', async () => {
      const request: CreateEventTaskRequest = {
        taskName: 'Book Venue',
        triggerState: 'EVENT_PUBLISHED',
        dueDate: '2025-12-30T00:00:00Z',
        assignedOrganizerUsername: 'john.doe',
        notes: 'Contact venue manager ASAP',
      };

      const mockResponse: EventTaskResponse = {
        id: 'task-new-123',
        eventId: 'event-456',
        eventCode: 'BATbern57',
        templateId: null,
        taskName: 'Book Venue',
        triggerState: 'EVENT_PUBLISHED',
        dueDate: '2025-12-30T00:00:00Z',
        assignedOrganizerUsername: 'john.doe',
        status: 'todo',
        notes: 'Contact venue manager ASAP',
        completedDate: null,
        completedByUsername: null,
        createdAt: '2025-12-20T15:00:00Z',
        updatedAt: '2025-12-20T15:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await taskService.createAdHocTask('BATbern57', request);

      expect(apiClient.post).toHaveBeenCalledWith('/events/BATbern57/tasks', request);
      expect(result).toEqual(mockResponse);
      expect(result.templateId).toBeNull();
      expect(result.status).toBe('todo');
    });

    it('should create task with minimal required fields', async () => {
      const request: CreateEventTaskRequest = {
        taskName: 'Quick Task',
        triggerState: 'EVENT_PUBLISHED',
      };

      const mockResponse: EventTaskResponse = {
        id: 'task-new-456',
        eventId: 'event-789',
        eventCode: 'BATbern58',
        templateId: null,
        taskName: 'Quick Task',
        triggerState: 'EVENT_PUBLISHED',
        dueDate: null,
        assignedOrganizerUsername: null,
        status: 'todo',
        notes: null,
        completedDate: null,
        completedByUsername: null,
        createdAt: '2025-12-20T16:00:00Z',
        updatedAt: '2025-12-20T16:00:00Z',
      };

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await taskService.createAdHocTask('BATbern58', request);

      expect(result.dueDate).toBeNull();
      expect(result.assignedOrganizerUsername).toBeNull();
    });

    it('should propagate validation errors', async () => {
      const request: CreateEventTaskRequest = {
        taskName: '',
        triggerState: 'EVENT_PUBLISHED',
      };

      const error = new Error('Validation failed: taskName is required');
      vi.mocked(apiClient.post).mockRejectedValue(error);

      await expect(taskService.createAdHocTask('BATbern57', request)).rejects.toThrow(
        'Validation failed'
      );
    });
  });

  describe('createTasksFromTemplates (AC21)', () => {
    it('should create multiple tasks from templates with assignees', async () => {
      const request: CreateTasksFromTemplatesRequest = {
        templates: [
          {
            templateId: 'template-1',
            assignedOrganizerUsername: 'john.doe',
          },
          {
            templateId: 'template-2',
            assignedOrganizerUsername: 'jane.smith',
          },
        ],
      };

      const mockResponse: EventTaskResponse[] = [
        {
          id: 'task-batch-1',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-1',
          taskName: 'Send Newsletter',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDate: '2025-12-25T00:00:00Z',
          assignedOrganizerUsername: 'john.doe',
          status: 'pending',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T17:00:00Z',
          updatedAt: '2025-12-20T17:00:00Z',
        },
        {
          id: 'task-batch-2',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-2',
          taskName: 'Order Catering',
          triggerState: 'SLOT_ASSIGNED',
          dueDate: '2025-12-28T00:00:00Z',
          assignedOrganizerUsername: 'jane.smith',
          status: 'pending',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T17:00:00Z',
          updatedAt: '2025-12-20T17:00:00Z',
        },
      ];

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await taskService.createTasksFromTemplates('BATbern56', request);

      expect(apiClient.post).toHaveBeenCalledWith(
        '/events/BATbern56/tasks/from-templates',
        request
      );
      expect(result).toEqual(mockResponse);
      expect(result).toHaveLength(2);
    });

    it('should create tasks without assignees', async () => {
      const request: CreateTasksFromTemplatesRequest = {
        templates: [
          {
            templateId: 'template-1',
          },
        ],
      };

      const mockResponse: EventTaskResponse[] = [
        {
          id: 'task-batch-3',
          eventId: 'event-456',
          eventCode: 'BATbern57',
          templateId: 'template-1',
          taskName: 'Send Newsletter',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDate: null,
          assignedOrganizerUsername: null,
          status: 'pending',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T18:00:00Z',
          updatedAt: '2025-12-20T18:00:00Z',
        },
      ];

      vi.mocked(apiClient.post).mockResolvedValue({ data: mockResponse });

      const result = await taskService.createTasksFromTemplates('BATbern57', request);

      expect(result[0].assignedOrganizerUsername).toBeNull();
    });
  });

  describe('getMyTasks (AC24)', () => {
    it('should fetch all tasks assigned to current user', async () => {
      const mockTasks: EventTaskResponse[] = [
        {
          id: 'task-1',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-1',
          taskName: 'Send Newsletter',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDate: '2025-12-25T00:00:00Z',
          assignedOrganizerUsername: 'john.doe',
          status: 'todo',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTasks });

      const result = await taskService.getMyTasks(false);

      expect(apiClient.get).toHaveBeenCalledWith('/tasks/my-tasks', {
        params: { critical: false },
      });
      expect(result).toEqual(mockTasks);
    });

    it('should fetch critical tasks only when critical=true', async () => {
      const mockCriticalTasks: EventTaskResponse[] = [
        {
          id: 'task-critical-1',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-1',
          taskName: 'Urgent Task',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDate: '2025-12-21T00:00:00Z',
          assignedOrganizerUsername: 'john.doe',
          status: 'todo',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCriticalTasks });

      const result = await taskService.getMyTasks(true);

      expect(apiClient.get).toHaveBeenCalledWith('/tasks/my-tasks', {
        params: { critical: true },
      });
      expect(result).toEqual(mockCriticalTasks);
    });
  });

  describe('getAllTasks', () => {
    it('should fetch all tasks for all organizers', async () => {
      const mockTasks: EventTaskResponse[] = [
        {
          id: 'task-1',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-1',
          taskName: 'Task 1',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDate: '2025-12-25T00:00:00Z',
          assignedOrganizerUsername: 'john.doe',
          status: 'todo',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
        {
          id: 'task-2',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-2',
          taskName: 'Task 2',
          triggerState: 'SLOT_ASSIGNED',
          dueDate: '2025-12-26T00:00:00Z',
          assignedOrganizerUsername: 'jane.smith',
          status: 'in_progress',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockTasks });

      const result = await taskService.getAllTasks(false);

      expect(apiClient.get).toHaveBeenCalledWith('/tasks/all-tasks', {
        params: { critical: false },
      });
      expect(result).toEqual(mockTasks);
      expect(result).toHaveLength(2);
    });

    it('should fetch critical tasks for all organizers when critical=true', async () => {
      const mockCriticalTasks: EventTaskResponse[] = [
        {
          id: 'task-critical-1',
          eventId: 'event-123',
          eventCode: 'BATbern56',
          templateId: 'template-1',
          taskName: 'Urgent Task',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDate: '2025-12-21T00:00:00Z',
          assignedOrganizerUsername: 'john.doe',
          status: 'todo',
          notes: null,
          completedDate: null,
          completedByUsername: null,
          createdAt: '2025-12-20T10:00:00Z',
          updatedAt: '2025-12-20T10:00:00Z',
        },
      ];

      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCriticalTasks });

      const result = await taskService.getAllTasks(true);

      expect(apiClient.get).toHaveBeenCalledWith('/tasks/all-tasks', {
        params: { critical: true },
      });
      expect(result).toEqual(mockCriticalTasks);
    });
  });

  describe('completeTask (AC25)', () => {
    it('should complete task with notes', async () => {
      const request: CompleteTaskRequest = {
        notes: 'Newsletter sent successfully to 150 recipients',
      };

      const mockResponse: EventTaskResponse = {
        id: 'task-1',
        eventId: 'event-123',
        eventCode: 'BATbern56',
        templateId: 'template-1',
        taskName: 'Send Newsletter',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDate: '2025-12-25T00:00:00Z',
        assignedOrganizerUsername: 'john.doe',
        status: 'completed',
        notes: 'Newsletter sent successfully to 150 recipients',
        completedDate: '2025-12-20T19:00:00Z',
        completedByUsername: 'john.doe',
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T19:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.completeTask('task-1', request);

      expect(apiClient.put).toHaveBeenCalledWith('/tasks/task-1/complete', request);
      expect(result).toEqual(mockResponse);
      expect(result.status).toBe('completed');
      expect(result.completedDate).toBeDefined();
      expect(result.completedByUsername).toBe('john.doe');
    });

    it('should complete task without notes', async () => {
      const request: CompleteTaskRequest = {};

      const mockResponse: EventTaskResponse = {
        id: 'task-2',
        eventId: 'event-123',
        eventCode: 'BATbern56',
        templateId: 'template-2',
        taskName: 'Order Catering',
        triggerState: 'SLOT_ASSIGNED',
        dueDate: '2025-12-28T00:00:00Z',
        assignedOrganizerUsername: 'jane.smith',
        status: 'completed',
        notes: null,
        completedDate: '2025-12-20T20:00:00Z',
        completedByUsername: 'jane.smith',
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T20:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.completeTask('task-2', request);

      expect(result.status).toBe('completed');
      expect(result.notes).toBeNull();
    });

    it('should propagate 404 errors for non-existent task', async () => {
      const request: CompleteTaskRequest = {};

      const error = new Error('Task not found: task-999');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(taskService.completeTask('task-999', request)).rejects.toThrow('Task not found');
    });
  });

  describe('reassignTask (AC27)', () => {
    it('should reassign task to different organizer', async () => {
      const request: ReassignTaskRequest = {
        newOrganizerUsername: 'alice.wonder',
      };

      const mockResponse: EventTaskResponse = {
        id: 'task-1',
        eventId: 'event-123',
        eventCode: 'BATbern56',
        templateId: 'template-1',
        taskName: 'Send Newsletter',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDate: '2025-12-25T00:00:00Z',
        assignedOrganizerUsername: 'alice.wonder',
        status: 'todo',
        notes: null,
        completedDate: null,
        completedByUsername: null,
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T21:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.reassignTask('task-1', request);

      expect(apiClient.put).toHaveBeenCalledWith('/tasks/task-1/reassign', request);
      expect(result).toEqual(mockResponse);
      expect(result.assignedOrganizerUsername).toBe('alice.wonder');
    });

    it('should propagate 400 errors for non-existent user', async () => {
      const request: ReassignTaskRequest = {
        newOrganizerUsername: 'nonexistent.user',
      };

      const error = new Error('User not found: nonexistent.user');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(taskService.reassignTask('task-1', request)).rejects.toThrow('User not found');
    });

    it('should propagate 404 errors for non-existent task', async () => {
      const request: ReassignTaskRequest = {
        newOrganizerUsername: 'alice.wonder',
      };

      const error = new Error('Task not found: task-999');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(taskService.reassignTask('task-999', request)).rejects.toThrow('Task not found');
    });
  });

  describe('updateTaskStatus (drag-and-drop)', () => {
    it('should update task status from pending to todo', async () => {
      const mockResponse: EventTaskResponse = {
        id: 'task-1',
        eventId: 'event-123',
        eventCode: 'BATbern56',
        templateId: 'template-1',
        taskName: 'Send Newsletter',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDate: '2025-12-25T00:00:00Z',
        assignedOrganizerUsername: 'john.doe',
        status: 'todo',
        notes: null,
        completedDate: null,
        completedByUsername: null,
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T22:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.updateTaskStatus('task-1', 'todo');

      expect(apiClient.put).toHaveBeenCalledWith('/tasks/task-1/status', {
        status: 'todo',
      });
      expect(result.status).toBe('todo');
    });

    it('should update task status from todo to in_progress', async () => {
      const mockResponse: EventTaskResponse = {
        id: 'task-1',
        eventId: 'event-123',
        eventCode: 'BATbern56',
        templateId: 'template-1',
        taskName: 'Send Newsletter',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDate: '2025-12-25T00:00:00Z',
        assignedOrganizerUsername: 'john.doe',
        status: 'in_progress',
        notes: null,
        completedDate: null,
        completedByUsername: null,
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T22:30:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.updateTaskStatus('task-1', 'in_progress');

      expect(result.status).toBe('in_progress');
    });

    it('should update task status from in_progress to completed', async () => {
      const mockResponse: EventTaskResponse = {
        id: 'task-1',
        eventId: 'event-123',
        eventCode: 'BATbern56',
        templateId: 'template-1',
        taskName: 'Send Newsletter',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDate: '2025-12-25T00:00:00Z',
        assignedOrganizerUsername: 'john.doe',
        status: 'completed',
        notes: null,
        completedDate: '2025-12-20T23:00:00Z',
        completedByUsername: 'john.doe',
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-20T23:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.updateTaskStatus('task-1', 'completed');

      expect(result.status).toBe('completed');
      expect(result.completedDate).toBeDefined();
    });

    it('should reopen completed task to todo', async () => {
      const mockResponse: EventTaskResponse = {
        id: 'task-1',
        eventId: 'event-123',
        eventCode: 'BATbern56',
        templateId: 'template-1',
        taskName: 'Send Newsletter',
        triggerState: 'SPEAKER_CONFIRMED',
        dueDate: '2025-12-25T00:00:00Z',
        assignedOrganizerUsername: 'john.doe',
        status: 'todo',
        notes: null,
        completedDate: null,
        completedByUsername: null,
        createdAt: '2025-12-20T10:00:00Z',
        updatedAt: '2025-12-21T00:00:00Z',
      };

      vi.mocked(apiClient.put).mockResolvedValue({ data: mockResponse });

      const result = await taskService.updateTaskStatus('task-1', 'todo');

      expect(result.status).toBe('todo');
      expect(result.completedDate).toBeNull();
    });

    it('should propagate 400 errors for invalid status transitions', async () => {
      const error = new Error('Invalid status transition');
      vi.mocked(apiClient.put).mockRejectedValue(error);

      await expect(taskService.updateTaskStatus('task-1', 'completed')).rejects.toThrow(
        'Invalid status'
      );
    });
  });

  describe('Error Handling & Edge Cases (AC28-37)', () => {
    describe('AC36: Task Auto-Creation Idempotency', () => {
      it('should handle duplicate task creation attempts gracefully', async () => {
        const request: CreateTasksFromTemplatesRequest = {
          templates: [
            {
              templateId: 'template-1',
              assignedOrganizerUsername: 'john.doe',
            },
          ],
        };

        const error = new Error('Duplicate task: Task already exists for this template and event');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(taskService.createTasksFromTemplates('BATbern56', request)).rejects.toThrow(
          'Duplicate task'
        );
      });

      it('should handle unique constraint violations', async () => {
        const request: CreateEventTaskRequest = {
          taskName: 'Duplicate Task',
          triggerState: 'EVENT_PUBLISHED',
        };

        const error = new Error('Unique constraint violation: task already exists');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(taskService.createAdHocTask('BATbern56', request)).rejects.toThrow(
          'Unique constraint'
        );
      });
    });

    describe('Additional Error Scenarios', () => {
      it('should handle network timeout errors', async () => {
        const error = new Error('Request timeout after 30000ms');
        vi.mocked(apiClient.get).mockRejectedValue(error);

        await expect(taskService.listAllTemplates()).rejects.toThrow('Request timeout');
      });

      it('should handle service unavailable errors (503)', async () => {
        const error = new Error('Service temporarily unavailable');
        vi.mocked(apiClient.get).mockRejectedValue(error);

        await expect(taskService.getMyTasks()).rejects.toThrow('Service temporarily unavailable');
      });

      it('should handle internal server errors (500)', async () => {
        const request: CreateTaskTemplateRequest = {
          name: 'Test Task',
          triggerState: 'SPEAKER_CONFIRMED',
          dueDateType: 'immediate',
        };

        const error = new Error('Internal server error');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(taskService.createTemplate(request)).rejects.toThrow('Internal server error');
      });

      it('should handle rate limiting errors (429)', async () => {
        const error = new Error('Too many requests. Please try again later.');
        vi.mocked(apiClient.get).mockRejectedValue(error);

        await expect(taskService.getAllTasks()).rejects.toThrow('Too many requests');
      });

      it('should handle database transaction failures', async () => {
        const request: CreateEventTaskRequest = {
          taskName: 'Test Task',
          triggerState: 'EVENT_PUBLISHED',
        };

        const error = new Error('Transaction rolled back due to database error');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(taskService.createAdHocTask('BATbern56', request)).rejects.toThrow(
          'Transaction rolled back'
        );
      });

      it('should handle concurrent modification conflicts', async () => {
        const request: CompleteTaskRequest = {
          notes: 'Completed',
        };

        const error = new Error('Optimistic locking failure: task was modified by another user');
        vi.mocked(apiClient.put).mockRejectedValue(error);

        await expect(taskService.completeTask('task-1', request)).rejects.toThrow(
          'Optimistic locking'
        );
      });

      it('should handle invalid workflow state transitions', async () => {
        const error = new Error(
          'Invalid workflow state: cannot transition from COMPLETED to PENDING'
        );
        vi.mocked(apiClient.put).mockRejectedValue(error);

        await expect(taskService.updateTaskStatus('task-1', 'pending')).rejects.toThrow(
          'Invalid workflow state'
        );
      });

      it('should handle orphaned template references', async () => {
        const request: CreateTasksFromTemplatesRequest = {
          templates: [
            {
              templateId: 'deleted-template-123',
              assignedOrganizerUsername: 'john.doe',
            },
          ],
        };

        const error = new Error('Template not found: deleted-template-123');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(taskService.createTasksFromTemplates('BATbern56', request)).rejects.toThrow(
          'Template not found'
        );
      });

      it('should handle invalid event code format', async () => {
        const error = new Error('Invalid event code format: must match pattern BATbern[0-9]+');
        vi.mocked(apiClient.get).mockRejectedValue(error);

        await expect(taskService.listEventTasks('invalid-code')).rejects.toThrow(
          'Invalid event code format'
        );
      });

      it('should handle malformed request data', async () => {
        const request: CreateTaskTemplateRequest = {
          name: '',
          triggerState: '',
          dueDateType: 'invalid',
        };

        const error = new Error('Validation failed: Invalid request data');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(taskService.createTemplate(request)).rejects.toThrow('Validation failed');
      });

      it('should handle malformed response data for listAllTemplates', async () => {
        vi.mocked(apiClient.get).mockResolvedValue({ data: null });

        const result = await taskService.listAllTemplates();

        expect(result).toBeNull();
      });

      it('should handle malformed response data for getMyTasks', async () => {
        vi.mocked(apiClient.get).mockResolvedValue({ data: null });

        const result = await taskService.getMyTasks();

        expect(result).toBeNull();
      });

      it('should handle invalid due date calculations', async () => {
        const request: CreateEventTaskRequest = {
          taskName: 'Invalid Date Task',
          triggerState: 'EVENT_PUBLISHED',
          dueDate: '2025-13-45T99:99:99Z', // Invalid date
        };

        const error = new Error('Invalid date format: must be ISO-8601');
        vi.mocked(apiClient.post).mockRejectedValue(error);

        await expect(taskService.createAdHocTask('BATbern56', request)).rejects.toThrow(
          'Invalid date format'
        );
      });

      it('should handle permission errors for template modification', async () => {
        const request: UpdateTaskTemplateRequest = {
          name: 'Updated Name',
        };

        const error = new Error('Forbidden: Cannot modify templates created by other users');
        vi.mocked(apiClient.put).mockRejectedValue(error);

        await expect(taskService.updateTemplate('template-123', request)).rejects.toThrow(
          'Cannot modify templates created by other users'
        );
      });

      it('should handle task reassignment to non-existent organizer', async () => {
        const request: ReassignTaskRequest = {
          newOrganizerUsername: 'nonexistent.user',
        };

        const error = new Error('Organizer not found: nonexistent.user');
        vi.mocked(apiClient.put).mockRejectedValue(error);

        await expect(taskService.reassignTask('task-1', request)).rejects.toThrow(
          'Organizer not found'
        );
      });

      it('should handle task completion with invalid notes length', async () => {
        const request: CompleteTaskRequest = {
          notes: 'A'.repeat(10000), // Exceeds max length
        };

        const error = new Error('Validation failed: notes exceeds maximum length of 2000');
        vi.mocked(apiClient.put).mockRejectedValue(error);

        await expect(taskService.completeTask('task-1', request)).rejects.toThrow(
          'notes exceeds maximum length'
        );
      });

      it('should handle database connection pool exhaustion', async () => {
        const error = new Error('Database connection pool exhausted. Please try again later.');
        vi.mocked(apiClient.get).mockRejectedValue(error);

        await expect(taskService.listEventTasks('BATbern56')).rejects.toThrow(
          'Database connection pool exhausted'
        );
      });

      it('should handle cross-event task assignment errors', async () => {
        const request: ReassignTaskRequest = {
          newOrganizerUsername: 'organizer.from.other.event',
        };

        const error = new Error('Invalid assignment: organizer not assigned to this event');
        vi.mocked(apiClient.put).mockRejectedValue(error);

        await expect(taskService.reassignTask('task-1', request)).rejects.toThrow(
          'organizer not assigned to this event'
        );
      });
    });

    describe('Edge Cases for Critical Tasks', () => {
      it('should filter critical tasks correctly', async () => {
        const mockCriticalTasks: EventTaskResponse[] = [
          {
            id: 'task-overdue',
            eventId: 'event-123',
            eventCode: 'BATbern56',
            templateId: 'template-1',
            taskName: 'Overdue Task',
            triggerState: 'SPEAKER_CONFIRMED',
            dueDate: '2025-12-19T00:00:00Z', // Past date
            assignedOrganizerUsername: 'john.doe',
            status: 'todo',
            notes: null,
            completedDate: null,
            completedByUsername: null,
            createdAt: '2025-12-20T10:00:00Z',
            updatedAt: '2025-12-20T10:00:00Z',
          },
          {
            id: 'task-due-soon',
            eventId: 'event-123',
            eventCode: 'BATbern56',
            templateId: 'template-2',
            taskName: 'Due Soon Task',
            triggerState: 'SLOT_ASSIGNED',
            dueDate: '2025-12-22T00:00:00Z', // Within 3 days
            assignedOrganizerUsername: 'john.doe',
            status: 'in_progress',
            notes: null,
            completedDate: null,
            completedByUsername: null,
            createdAt: '2025-12-20T10:00:00Z',
            updatedAt: '2025-12-20T10:00:00Z',
          },
        ];

        vi.mocked(apiClient.get).mockResolvedValue({ data: mockCriticalTasks });

        const result = await taskService.getMyTasks(true);

        expect(result).toHaveLength(2);
        expect(result[0].taskName).toBe('Overdue Task');
        expect(result[1].taskName).toBe('Due Soon Task');
      });

      it('should return empty array when no critical tasks exist', async () => {
        vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

        const result = await taskService.getMyTasks(true);

        expect(result).toEqual([]);
        expect(result).toHaveLength(0);
      });

      it('should handle tasks with null due dates in critical filter', async () => {
        const mockTasks: EventTaskResponse[] = [
          {
            id: 'task-no-due-date',
            eventId: 'event-123',
            eventCode: 'BATbern56',
            templateId: 'template-1',
            taskName: 'No Due Date Task',
            triggerState: 'SPEAKER_CONFIRMED',
            dueDate: null,
            assignedOrganizerUsername: 'john.doe',
            status: 'todo',
            notes: null,
            completedDate: null,
            completedByUsername: null,
            createdAt: '2025-12-20T10:00:00Z',
            updatedAt: '2025-12-20T10:00:00Z',
          },
        ];

        vi.mocked(apiClient.get).mockResolvedValue({ data: mockTasks });

        const result = await taskService.getMyTasks(true);

        // Tasks with null due dates should be excluded from critical filter (backend logic)
        expect(result).toEqual(mockTasks);
      });
    });
  });

  describe('Service Singleton', () => {
    it('should export a singleton instance', () => {
      expect(taskService).toBeDefined();
      expect(taskService).toBeInstanceOf(Object);
      expect(typeof taskService.listAllTemplates).toBe('function');
      expect(typeof taskService.createTemplate).toBe('function');
      expect(typeof taskService.updateTemplate).toBe('function');
      expect(typeof taskService.deleteTemplate).toBe('function');
      expect(typeof taskService.listEventTasks).toBe('function');
      expect(typeof taskService.createAdHocTask).toBe('function');
      expect(typeof taskService.createTasksFromTemplates).toBe('function');
      expect(typeof taskService.getMyTasks).toBe('function');
      expect(typeof taskService.getAllTasks).toBe('function');
      expect(typeof taskService.completeTask).toBe('function');
      expect(typeof taskService.reassignTask).toBe('function');
      expect(typeof taskService.updateTaskStatus).toBe('function');
    });

    it('should maintain state across multiple calls', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      // Call twice to ensure singleton behavior
      await taskService.listAllTemplates();
      await taskService.listAllTemplates();

      expect(apiClient.get).toHaveBeenCalledTimes(2);
    });
  });
});
