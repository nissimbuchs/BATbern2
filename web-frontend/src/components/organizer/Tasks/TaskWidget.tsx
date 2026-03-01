/**
 * TaskWidget Component (Story 5.5 Phase 6 - Modified for routing)
 *
 * AC24: Critical tasks widget for dashboard sidebar
 * Wireframe: docs/wireframes/5.5-content-review-task-system-ux-flow.md
 *
 * Features:
 * - Displays all active tasks (pending, todo, in_progress)
 * - Shows task name, event, due date, assignee
 * - "View All Tasks" button navigates to /organizer/tasks page
 * - Auto-refreshes on task completion
 */

import React, { useState } from 'react';
import { List, ListItem, Typography, Box, Button, Chip, Stack, Skeleton } from '@mui/material';
import { CheckCircle as CheckCircleIcon, OpenInNew as OpenInNewIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { de, enUS } from 'date-fns/locale';
import { taskService, type EventTaskResponse } from '@/services/taskService';
import { TaskCard } from './TaskCard';
import { CustomTaskModal } from './CustomTaskModal';

interface TaskWidgetProps {
  organizerUsername: string;
}

export const TaskWidget: React.FC<TaskWidgetProps> = ({ organizerUsername }) => {
  const { t, i18n } = useTranslation('events');
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const locale = i18n.language === 'de' ? de : enUS;
  const [editingTask, setEditingTask] = useState<EventTaskResponse | null>(null);

  // Fetch critical tasks
  const {
    data: criticalTasks = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['tasks', 'critical', organizerUsername],
    queryFn: () => taskService.getMyTasks(true),
    refetchInterval: 60000, // Auto-refresh every 60 seconds
  });

  // Complete task mutation
  const completeTaskMutation = useMutation({
    mutationFn: ({ taskId, notes }: { taskId: string; notes?: string }) =>
      taskService.completeTask(taskId, { notes }),
    onSuccess: () => {
      // Invalidate critical tasks query to refresh the list
      queryClient.invalidateQueries({ queryKey: ['tasks', 'critical'] });
      queryClient.invalidateQueries({ queryKey: ['tasks', 'my-tasks'] });
    },
  });

  const handleCompleteTask = (taskId: string) => {
    completeTaskMutation.mutate({ taskId });
  };

  const handleViewAllTasks = () => {
    navigate('/organizer/tasks');
  };

  // Show all non-completed tasks (backend already filters)
  const activeTasks = criticalTasks.filter((task) => task.status !== 'completed');

  // Loading state
  if (isLoading) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('tasks.criticalTasks', 'Critical Tasks')}
        </Typography>
        <List>
          {[1, 2, 3].map((index) => (
            <ListItem key={index} data-testid={`skeleton-task-${index}`}>
              <Skeleton variant="rectangular" width="100%" height={80} />
            </ListItem>
          ))}
        </List>
      </Box>
    );
  }

  // Error state
  if (isError) {
    return (
      <Box textAlign="center" py={4}>
        <Typography variant="h6" gutterBottom>
          {t('tasks.criticalTasks', 'Critical Tasks')}
        </Typography>
        <Typography variant="body2" color="error">
          {t('tasks.errorLoadingTasks', 'Failed to load tasks')}
        </Typography>
      </Box>
    );
  }

  // Empty state
  if (activeTasks.length === 0) {
    return (
      <Box textAlign="center" py={4}>
        <Typography variant="h6" gutterBottom>
          {t('tasks.criticalTasks', 'Critical Tasks')}
        </Typography>
        <CheckCircleIcon color="success" sx={{ fontSize: 48, mb: 2 }} />
        <Typography variant="body2" color="text.secondary">
          {t('tasks.noCriticalTasks', 'No critical tasks')}
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          {t('tasks.greatJob', 'Great job!')}
        </Typography>
        <Button
          variant="outlined"
          size="small"
          onClick={handleViewAllTasks}
          sx={{ mt: 2 }}
          endIcon={<OpenInNewIcon />}
        >
          {t('tasks.viewAllTasks', 'View All Tasks')}
        </Button>
      </Box>
    );
  }

  return (
    <Box aria-label={t('tasks.criticalTasksWidget', 'Critical tasks widget')}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('tasks.criticalTasks', 'Critical Tasks')}</Typography>
        <Chip
          label={`${activeTasks.length} ${activeTasks.length === 1 ? t('tasks.task', 'task') : t('tasks.tasks', 'tasks')}`}
          size="small"
          color="primary"
          aria-label={t('tasks.activeAriaLabel', {
            count: activeTasks.length,
            taskLabel: activeTasks.length === 1 ? t('tasks.task') : t('tasks.tasks'),
          })}
        />
      </Stack>

      <List sx={{ maxHeight: 400, overflow: 'auto' }} data-testid="critical-tasks-list">
        {activeTasks.map((task) => (
          <TaskCard
            key={task.id}
            task={task}
            locale={locale}
            onComplete={handleCompleteTask}
            onEdit={(t) => setEditingTask(t)}
            showCompleteButton={true}
            showEventCode={true}
            showTriggerState={false}
            showCompletionInfo={false}
            t={t}
          />
        ))}
      </List>

      {/* View All Tasks Button */}
      <Box mt={2}>
        <Button
          variant="outlined"
          fullWidth
          size="small"
          onClick={handleViewAllTasks}
          endIcon={<OpenInNewIcon />}
        >
          {t('tasks.viewAllTasks', 'View All Tasks')}
        </Button>
      </Box>

      {/* Edit Task Modal */}
      <CustomTaskModal
        open={editingTask !== null}
        onClose={() => setEditingTask(null)}
        eventId={null}
        organizerUsername={organizerUsername}
        existingTask={editingTask ?? undefined}
      />
    </Box>
  );
};
