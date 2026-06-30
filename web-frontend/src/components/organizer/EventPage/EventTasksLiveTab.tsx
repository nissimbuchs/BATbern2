/**
 * EventTasksLiveTab (Epic 14, Story 14.F.2) — the "Tasks" sub-tab of Details.
 *
 * Lifts the form-coupled `EventTasksTab` template checklist into a LIVE surface
 * for an existing event: toggling a template creates/deletes its task instance
 * immediately, the assignee persists on change, and custom tasks add/edit/delete
 * live via the (already live) `CustomTaskModal`. No form-save round-trip; reuses
 * the existing `taskService` (recompose, not rewrite; NFR9).
 *
 * Destructive guardrail: unchecking a template whose task carries state
 * (notes / in-progress / completed) asks for confirmation first.
 */

import React, { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { taskService, type EventTaskResponse } from '@/services/taskService';
import type { Event, EventDetailUI, EventUI } from '@/types/event.types';
import { EventTasksTab } from '@/components/organizer/Tasks/EventTasksTab';
import { CustomTaskModal } from '@/components/organizer/Tasks/CustomTaskModal';

interface EventTasksLiveTabProps {
  event: Event | EventDetailUI;
  eventCode: string;
}

export const EventTasksLiveTab: React.FC<EventTasksLiveTabProps> = ({ event, eventCode }) => {
  const { t } = useTranslation('events');
  const queryClient = useQueryClient();
  const ev = event as EventUI;
  // CustomTaskModal / EventTasksTab take the event CODE as their `eventId` prop
  // (mirrors EventForm, which passes `event?.eventCode`).
  const eventId = eventCode || null;
  const organizerUsername = ev.organizerUsername ?? '';

  // Query under the ['tasks', …] prefix so CustomTaskModal's broad ['tasks']
  // invalidation (on add/edit) also refreshes this list.
  const {
    data: tasks = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['tasks', 'event', eventCode],
    queryFn: () => taskService.listEventTasks(eventCode),
    enabled: !!eventCode,
  });

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<{ taskId: string; name: string } | null>(null);
  const [editingTask, setEditingTask] = useState<EventTaskResponse | null>(null);

  const selectedTemplates = useMemo(
    () => tasks.filter((x) => x.templateId).map((x) => x.templateId as string),
    [tasks]
  );
  const templateAssignees = useMemo(
    () =>
      Object.fromEntries(
        tasks
          .filter((x) => x.templateId)
          .map((x) => [x.templateId as string, x.assignedOrganizerUsername ?? ''])
      ),
    [tasks]
  );
  const customTasks = useMemo(() => tasks.filter((x) => x.templateId == null), [tasks]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['tasks'] });
    queryClient.invalidateQueries({ queryKey: ['eventTasks', eventCode] });
  };

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      invalidate();
    } catch (e) {
      setError(e instanceof Error ? e.message : t('errors.saveFailed', 'Could not save.'));
    } finally {
      setBusy(false);
    }
  };

  const handleTemplateToggle = (templateId: string, checked: boolean) => {
    if (busy) return;
    if (checked) {
      void run(() =>
        taskService.createTasksFromTemplates(eventCode, {
          templates: [
            { templateId, assignedOrganizerUsername: templateAssignees[templateId] || undefined },
          ],
        })
      );
      return;
    }
    const task = tasks.find((x) => x.templateId === templateId);
    if (!task) return;
    const carriesState =
      !!task.notes || task.status === 'completed' || task.status === 'in_progress';
    if (carriesState) {
      setConfirmDelete({ taskId: task.id, name: task.taskName });
    } else {
      void run(() => taskService.deleteTask(task.id));
    }
  };

  const handleAssigneeChange = (templateId: string, assignee: string) => {
    const task = tasks.find((x) => x.templateId === templateId);
    if (!task) return;
    void run(() =>
      taskService.updateTask(task.id, { assignedOrganizerUsername: assignee || undefined })
    );
  };

  const handleDeleteCustom = (taskId: string) => {
    const task = tasks.find((x) => x.id === taskId);
    setConfirmDelete({ taskId, name: task?.taskName ?? '' });
  };

  const doConfirmedDelete = () => {
    if (!confirmDelete) return;
    const { taskId } = confirmDelete;
    setConfirmDelete(null);
    void run(() => taskService.deleteTask(taskId));
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" py={6}>
        <CircularProgress />
      </Box>
    );
  }
  if (isError) {
    return (
      <Alert severity="error">
        {t('tasks.errors.loadTemplatesFailed', 'Could not load tasks.')}
      </Alert>
    );
  }

  return (
    <Box data-testid="event-tasks-live-tab">
      {error && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          onClose={() => setError(null)}
          data-testid="tasks-live-error"
        >
          {error}
        </Alert>
      )}

      <EventTasksTab
        eventId={eventId}
        organizerUsername={organizerUsername}
        selectedTemplates={selectedTemplates}
        templateAssignees={templateAssignees}
        customTasks={customTasks}
        disabledTemplates={[]}
        onTemplateToggle={handleTemplateToggle}
        onAssigneeChange={handleAssigneeChange}
        onAddCustomTask={() => {
          /* EventTasksTab opens its own (live) CustomTaskModal */
        }}
        onEditCustomTask={(task) => setEditingTask(task)}
        onDeleteCustomTask={handleDeleteCustom}
      />

      {editingTask && (
        <CustomTaskModal
          open
          onClose={() => setEditingTask(null)}
          eventId={eventId}
          organizerUsername={organizerUsername}
          existingTask={editingTask}
        />
      )}

      <Dialog open={!!confirmDelete} onClose={() => setConfirmDelete(null)}>
        <DialogTitle>{t('tasks.deleteConfirmTitle', 'Delete this task?')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('tasks.deleteConfirmBody', {
              name: confirmDelete?.name ?? '',
              defaultValue: `Delete "${confirmDelete?.name ?? ''}"? This can't be undone.`,
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDelete(null)}>
            {t('common:actions.cancel', 'Cancel')}
          </Button>
          <Button color="error" onClick={doConfirmedDelete} data-testid="tasks-confirm-delete">
            {t('common.confirm', 'Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default EventTasksLiveTab;
