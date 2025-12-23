/**
 * Task Service (Story 5.5 Phase 6)
 *
 * HTTP client for Task Management APIs
 * Features:
 * - List/create/update/delete task templates
 * - List/create/complete/reassign event tasks
 * - Get critical tasks (overdue + due soon)
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';

// API base paths
const TASKS_API_PATH = '/tasks';
const EVENTS_API_PATH = '/events';

/**
 * Task Template Response DTO (matches backend TaskTemplateResponse.java)
 */
export interface TaskTemplateResponse {
  id: string;
  name: string;
  triggerState: string;
  dueDateType: string;
  dueDateOffsetDays: number | null;
  isDefault: boolean;
  createdByUsername: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Event Task Response DTO (matches backend EventTaskResponse.java)
 */
export interface EventTaskResponse {
  id: string;
  eventId: string;
  eventCode: string | null;
  templateId: string | null;
  taskName: string;
  triggerState: string;
  dueDate: string | null;
  assignedOrganizerUsername: string | null;
  status: 'pending' | 'todo' | 'in_progress' | 'completed';
  notes: string | null;
  completedDate: string | null;
  completedByUsername: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Create Task Template Request DTO (matches backend CreateTaskTemplateRequest.java)
 */
export interface CreateTaskTemplateRequest {
  name: string;
  triggerState: string;
  dueDateType: string;
  dueDateOffsetDays?: number;
  saveAsTemplate?: boolean;
}

/**
 * Update Task Template Request DTO (matches backend UpdateTaskTemplateRequest.java)
 */
export interface UpdateTaskTemplateRequest {
  name?: string;
  triggerState?: string;
  dueDateType?: string;
  dueDateOffsetDays?: number;
}

/**
 * Create Event Task Request DTO (matches backend CreateEventTaskRequest.java)
 */
export interface CreateEventTaskRequest {
  taskName: string;
  triggerState: string;
  dueDate?: string;
  assignedOrganizerUsername?: string;
  notes?: string;
}

/**
 * Create Tasks from Templates Request DTO (matches backend CreateTasksFromTemplatesRequest.java)
 */
export interface CreateTasksFromTemplatesRequest {
  templates: TemplateConfig[];
}

export interface TemplateConfig {
  templateId: string;
  assignedOrganizerUsername?: string;
}

/**
 * Complete Task Request DTO (matches backend CompleteTaskRequest.java)
 */
export interface CompleteTaskRequest {
  notes?: string;
}

/**
 * Reassign Task Request DTO (matches backend ReassignTaskRequest.java)
 */
export interface ReassignTaskRequest {
  newOrganizerUsername: string;
}

/**
 * Update Task Status Request DTO (matches backend UpdateTaskStatusRequest.java)
 */
export interface UpdateTaskStatusRequest {
  status: 'pending' | 'todo' | 'in_progress' | 'completed';
}

/**
 * Task Service
 *
 * Handles all HTTP requests for task management
 */
class TaskService {
  // ========== Task Template APIs ==========

  /**
   * List all task templates (AC19, AC26)
   *
   * Retrieves all task templates (default + custom)
   *
   * @returns List of all task templates
   * @throws Error if unauthorized (401, 403)
   */
  async listAllTemplates(): Promise<TaskTemplateResponse[]> {
    const response = await apiClient.get<TaskTemplateResponse[]>(`${TASKS_API_PATH}/templates`);

    return response.data;
  }

  /**
   * Create custom task template (AC22, AC26)
   *
   * Creates a new reusable task template
   *
   * @param request Template creation data
   * @returns Created template details
   * @throws Error if validation fails (400), unauthorized (401, 403)
   */
  async createTemplate(request: CreateTaskTemplateRequest): Promise<TaskTemplateResponse> {
    const response = await apiClient.post<TaskTemplateResponse>(
      `${TASKS_API_PATH}/templates`,
      request
    );

    return response.data;
  }

  /**
   * Update task template (AC26)
   *
   * Updates an existing custom template (cannot update default templates)
   *
   * @param templateId Template UUID
   * @param request Template update data
   * @returns Updated template details
   * @throws Error if template not found (404), cannot modify default (403), unauthorized (401)
   */
  async updateTemplate(
    templateId: string,
    request: UpdateTaskTemplateRequest
  ): Promise<TaskTemplateResponse> {
    const response = await apiClient.put<TaskTemplateResponse>(
      `${TASKS_API_PATH}/templates/${templateId}`,
      request
    );

    return response.data;
  }

  /**
   * Delete task template (AC26)
   *
   * Deletes a custom template (cannot delete default templates)
   *
   * @param templateId Template UUID
   * @returns void (204 No Content)
   * @throws Error if template not found (404), cannot delete default (403), unauthorized (401)
   */
  async deleteTemplate(templateId: string): Promise<void> {
    await apiClient.delete(`${TASKS_API_PATH}/templates/${templateId}`);
  }

  // ========== Event Task APIs ==========

  /**
   * List event tasks (AC24)
   *
   * Retrieves all tasks for an event
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @returns List of event tasks
   * @throws Error if event not found (404), unauthorized (401, 403)
   */
  async listEventTasks(eventCode: string): Promise<EventTaskResponse[]> {
    const response = await apiClient.get<EventTaskResponse[]>(
      `${EVENTS_API_PATH}/${eventCode}/tasks`
    );

    return response.data;
  }

  /**
   * Create ad-hoc event task (AC22)
   *
   * Creates a one-off task not from a template
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param request Task creation data
   * @returns Created task details
   * @throws Error if validation fails (400), event not found (404), unauthorized (401, 403)
   */
  async createAdHocTask(
    eventCode: string,
    request: CreateEventTaskRequest
  ): Promise<EventTaskResponse> {
    const response = await apiClient.post<EventTaskResponse>(
      `${EVENTS_API_PATH}/${eventCode}/tasks`,
      request
    );

    return response.data;
  }

  /**
   * Create tasks from templates (AC21)
   *
   * Creates multiple tasks for an event from selected templates with assignees
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param request Template configurations (templateId + assignee)
   * @returns List of created tasks
   * @throws Error if validation fails (400), event not found (404), unauthorized (401, 403)
   */
  async createTasksFromTemplates(
    eventCode: string,
    request: CreateTasksFromTemplatesRequest
  ): Promise<EventTaskResponse[]> {
    const response = await apiClient.post<EventTaskResponse[]>(
      `${EVENTS_API_PATH}/${eventCode}/tasks/from-templates`,
      request
    );

    return response.data;
  }

  /**
   * Get my tasks (AC24)
   *
   * Retrieves tasks assigned to current organizer
   *
   * @param critical If true, returns only critical tasks (overdue + due soon)
   * @returns List of assigned tasks
   * @throws Error if unauthorized (401, 403)
   */
  async getMyTasks(critical: boolean = false): Promise<EventTaskResponse[]> {
    const response = await apiClient.get<EventTaskResponse[]>(`${TASKS_API_PATH}/my-tasks`, {
      params: { critical },
    });

    return response.data;
  }

  /**
   * Get all tasks (for all organizers)
   * Story 5.5: Task dashboard with "All Tasks" filter
   *
   * @param critical If true, returns only critical tasks (overdue + due soon)
   * @returns List of all tasks
   * @throws Error if unauthorized (401, 403)
   */
  async getAllTasks(critical: boolean = false): Promise<EventTaskResponse[]> {
    const response = await apiClient.get<EventTaskResponse[]>(`${TASKS_API_PATH}/all-tasks`, {
      params: { critical },
    });

    return response.data;
  }

  /**
   * Complete task (AC25)
   *
   * Marks a task as completed with optional notes
   *
   * @param taskId Task UUID
   * @param request Completion data (optional notes)
   * @returns Updated task details
   * @throws Error if task not found (404), unauthorized (401, 403)
   */
  async completeTask(taskId: string, request: CompleteTaskRequest): Promise<EventTaskResponse> {
    const response = await apiClient.put<EventTaskResponse>(
      `${TASKS_API_PATH}/${taskId}/complete`,
      request
    );

    return response.data;
  }

  /**
   * Reassign task (AC27)
   *
   * Changes task assignment to a different organizer
   *
   * @param taskId Task UUID
   * @param request Reassignment data (new organizer username)
   * @returns Updated task details
   * @throws Error if task not found (404), user not found (400), unauthorized (401, 403)
   */
  async reassignTask(taskId: string, request: ReassignTaskRequest): Promise<EventTaskResponse> {
    const response = await apiClient.put<EventTaskResponse>(
      `${TASKS_API_PATH}/${taskId}/reassign`,
      request
    );

    return response.data;
  }

  /**
   * Update task status (drag-and-drop)
   *
   * Changes task status via drag-and-drop transitions:
   * - pending → todo (manual activation)
   * - pending → completed (skip todo phase)
   * - todo → completed (normal completion)
   * - todo → pending (revert to pending)
   * - completed → todo (reopen task)
   * - completed → pending (reopen and revert)
   *
   * @param taskId Task UUID
   * @param status New status
   * @returns Updated task details
   * @throws Error if task not found (404), invalid status (400), unauthorized (401, 403)
   */
  async updateTaskStatus(taskId: string, status: 'pending' | 'todo' | 'in_progress' | 'completed'): Promise<EventTaskResponse> {
    const response = await apiClient.put<EventTaskResponse>(
      `${TASKS_API_PATH}/${taskId}/status`,
      { status }
    );

    return response.data;
  }
}

// Export singleton instance
export const taskService = new TaskService();

// Export class for testing
export default TaskService;
