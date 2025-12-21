/**
 * TaskWidget Component (Story 5.5 Phase 6)
 *
 * AC24: Critical tasks widget for dashboard sidebar
 * Wireframe: docs/wireframes/5.5-content-review-task-system-ux-flow.md
 *
 * Features:
 * - Displays critical tasks (overdue + due soon < 3 days)
 * - Groups tasks by urgency (overdue, due soon)
 * - Shows task name, event, due date, assignee
 * - "View All Tasks" button opens TaskBoardModal
 * - Auto-refreshes on task completion
 */

import React, { useState } from 'react';
import {
  List,
  ListItem,
  ListItemText,
  Typography,
  Box,
  Button,
  Chip,
  Stack,
  Skeleton,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  Error as ErrorIcon,
  Warning as WarningIcon,
  CheckCircle as CheckCircleIcon,
  OpenInNew as OpenInNewIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format, isPast, isWithinInterval, addDays } from 'date-fns';
import { de, enUS, type Locale } from 'date-fns/locale';
import { taskService, type EventTaskResponse } from '@/services/taskService';
import { TaskBoardModal } from './TaskBoardModal';

interface TaskWidgetProps {
  organizerUsername: string;
}

export const TaskWidget: React.FC<TaskWidgetProps> = ({ organizerUsername }) => {
  const { t, i18n } = useTranslation('events');
  const queryClient = useQueryClient();
  const locale = i18n.language === 'de' ? de : enUS;
  const [isTaskBoardOpen, setIsTaskBoardOpen] = useState(false);

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
    setIsTaskBoardOpen(true);
  };

  // Categorize tasks by urgency
  const now = new Date();
  const threeDaysFromNow = addDays(now, 3);

  const overdueTasks = criticalTasks.filter((task) => {
    if (!task.dueDate || task.status === 'completed') return false;
    return isPast(new Date(task.dueDate));
  });

  const dueSoonTasks = criticalTasks.filter((task) => {
    if (!task.dueDate || task.status === 'completed') return false;
    const dueDate = new Date(task.dueDate);
    return (
      !isPast(dueDate) &&
      isWithinInterval(dueDate, {
        start: now,
        end: threeDaysFromNow,
      })
    );
  });

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
  if (overdueTasks.length === 0 && dueSoonTasks.length === 0) {
    return (
      <>
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

        {/* Task Board Modal */}
        <TaskBoardModal
          open={isTaskBoardOpen}
          onClose={() => setIsTaskBoardOpen(false)}
          organizerUsername={organizerUsername}
        />
      </>
    );
  }

  return (
    <Box aria-label="Critical tasks widget">
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('tasks.criticalTasks', 'Critical Tasks')}</Typography>
        <Chip
          label={overdueTasks.length + dueSoonTasks.length}
          size="small"
          color={overdueTasks.length > 0 ? 'error' : 'warning'}
          aria-label={`${overdueTasks.length + dueSoonTasks.length} critical tasks`}
        />
      </Stack>

      <List sx={{ maxHeight: 400, overflow: 'auto' }}>
        {/* Overdue Tasks */}
        {overdueTasks.length > 0 && (
          <Box mb={2}>
            <Typography variant="caption" color="error" fontWeight="bold" sx={{ pl: 2 }}>
              🔴 {t('tasks.overdue', 'OVERDUE')} ({overdueTasks.length})
            </Typography>
            {overdueTasks.map((task) => (
              <TaskItem
                key={task.id}
                task={task}
                isOverdue
                locale={locale}
                onComplete={handleCompleteTask}
                t={t}
              />
            ))}
          </Box>
        )}

        {/* Due Soon Tasks */}
        {dueSoonTasks.length > 0 && (
          <Box>
            <Typography variant="caption" color="warning.main" fontWeight="bold" sx={{ pl: 2 }}>
              🟡 {t('tasks.dueSoon', 'DUE SOON')} ({dueSoonTasks.length})
            </Typography>
            {dueSoonTasks.map((task) => (
              <TaskItem
                key={task.id}
                task={task}
                isOverdue={false}
                locale={locale}
                onComplete={handleCompleteTask}
                t={t}
              />
            ))}
          </Box>
        )}
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

      {/* Task Board Modal */}
      <TaskBoardModal
        open={isTaskBoardOpen}
        onClose={() => setIsTaskBoardOpen(false)}
        organizerUsername={organizerUsername}
      />
    </Box>
  );
};

/**
 * TaskItem Component
 *
 * Individual task item with complete action
 */
interface TaskItemProps {
  task: EventTaskResponse;
  isOverdue: boolean;
  locale: Locale;
  onComplete: (taskId: string) => void;
  t: ReturnType<typeof useTranslation>['t'];
}

const TaskItem: React.FC<TaskItemProps> = ({ task, isOverdue, locale, onComplete, t }) => {
  const formattedDate = task.dueDate
    ? format(new Date(task.dueDate), 'dd MMM yyyy', { locale })
    : t('tasks.noDueDate', 'No due date');

  return (
    <ListItem
      data-testid={`task-item-${task.id}`}
      sx={{
        border: 1,
        borderColor: isOverdue ? 'error.main' : 'warning.main',
        borderRadius: 1,
        mb: 1,
        flexDirection: 'column',
        alignItems: 'stretch',
        bgcolor: isOverdue ? 'error.lighter' : 'warning.lighter',
        '&:hover': {
          bgcolor: isOverdue ? 'error.light' : 'warning.light',
        },
      }}
    >
      {/* Task Header */}
      <Stack
        direction="row"
        spacing={1}
        alignItems="center"
        justifyContent="space-between"
        width="100%"
      >
        <Stack direction="row" spacing={1} alignItems="center" flex={1}>
          {isOverdue ? (
            <ErrorIcon color="error" fontSize="small" />
          ) : (
            <WarningIcon color="warning" fontSize="small" />
          )}
          <Typography variant="subtitle2" fontWeight="bold" noWrap sx={{ flex: 1 }}>
            {task.taskName}
          </Typography>
        </Stack>
        <Tooltip title={t('tasks.markComplete', 'Mark complete')}>
          <IconButton size="small" onClick={() => onComplete(task.id)} aria-label="Complete task">
            <CheckCircleIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>

      {/* Task Details */}
      <ListItemText
        secondaryTypographyProps={{ component: 'div' }}
        secondary={
          <Stack spacing={0.5} mt={1}>
            <Typography variant="caption" color="text.secondary">
              {t('tasks.dueDate', 'Due')}: {formattedDate}
            </Typography>
            {task.assignedOrganizerUsername && (
              <Typography variant="caption" color="text.secondary">
                {t('tasks.assignedTo', 'Assigned to')}: {task.assignedOrganizerUsername}
              </Typography>
            )}
            {task.notes && (
              <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                {task.notes}
              </Typography>
            )}
          </Stack>
        }
      />
    </ListItem>
  );
};
