/**
 * CriticalTasksList Component
 *
 * Story 2.5.3 - Task 8b (GREEN Phase)
 * AC: 1 (Event Dashboard Display)
 * Wireframe: docs/wireframes/story-1.16-event-management-dashboard.md v1.0
 *
 * Displays critical tasks with priority indicators and inline actions
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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  Warning as WarningIcon,
  Error as ErrorIcon,
  Event as EventIcon,
  Person as PersonIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { format } from 'date-fns';
import { de, enUS } from 'date-fns/locale';
import type { CriticalTask } from '@/types/event.types';

interface CriticalTasksListProps {
  tasks: CriticalTask[];
  isLoading?: boolean;
  onAction?: (taskId: string, actionId: string) => void;
}

export const CriticalTasksList: React.FC<CriticalTasksListProps> = ({
  tasks,
  isLoading = false,
  onAction,
}) => {
  const { t, i18n } = useTranslation('events');
  const [confirmationDialog, setConfirmationDialog] = useState<{
    taskId: string;
    actionId: string;
  } | null>(null);

  const locale = i18n.language === 'de' ? de : enUS;

  // Sort tasks by priority (critical first)
  const sortedTasks = [...tasks].sort((a, b) => {
    if (a.priority === 'critical' && b.priority !== 'critical') return -1;
    if (a.priority !== 'critical' && b.priority === 'critical') return 1;
    return 0;
  });

  const handleAction = (taskId: string, actionId: string, requiresConfirmation: boolean) => {
    if (requiresConfirmation) {
      setConfirmationDialog({ taskId, actionId });
    } else {
      onAction?.(taskId, actionId);
    }
  };

  const handleConfirm = () => {
    if (confirmationDialog) {
      onAction?.(confirmationDialog.taskId, confirmationDialog.actionId);
      setConfirmationDialog(null);
    }
  };

  const handleCancelConfirmation = () => {
    setConfirmationDialog(null);
  };

  // Loading state
  if (isLoading) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          {t('dashboard.criticalTasks')}
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

  // Empty state
  if (tasks.length === 0) {
    return (
      <Box textAlign="center" py={4}>
        <Typography variant="h6" gutterBottom>
          {t('dashboard.criticalTasks')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('dashboard.noCriticalTasks')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('dashboard.greatJob')}
        </Typography>
      </Box>
    );
  }

  return (
    <Box aria-label="Critical tasks list">
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">{t('dashboard.criticalTasks')}</Typography>
        <Chip
          label={t('dashboard.taskCount', { count: tasks.length })}
          size="small"
          aria-label={`${tasks.length} critical tasks`}
        />
      </Stack>

      <List>
        {sortedTasks.map((task) => {
          const formattedDate = format(new Date(task.dueDate), 'dd MMM yyyy', { locale });
          const isCritical = task.priority === 'critical';

          return (
            <ListItem
              key={task.id}
              data-testid={`task-item-${task.id}`}
              className={isCritical ? 'critical-task' : ''}
              sx={{
                border: 1,
                borderColor: isCritical ? 'error.main' : 'warning.main',
                borderRadius: 1,
                mb: 2,
                flexDirection: 'column',
                alignItems: 'stretch',
                bgcolor: isCritical ? 'error.light' : 'warning.light',
                opacity: isCritical ? 0.9 : 0.7,
              }}
            >
              {/* Task Header */}
              <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                {isCritical ? (
                  <ErrorIcon color="error" fontSize="small" />
                ) : (
                  <WarningIcon color="warning" fontSize="small" />
                )}
                <Typography variant="subtitle2" fontWeight="bold">
                  {task.title}
                </Typography>
                <Chip label={task.eventCode} size="small" variant="outlined" />
              </Stack>

              {/* Task Description */}
              <ListItemText
                secondaryTypographyProps={{ component: 'div' }}
                secondary={
                  <Stack spacing={0.5}>
                    <Typography variant="body2">{task.description}</Typography>
                    <Stack direction="row" spacing={2} mt={1}>
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <EventIcon fontSize="small" />
                        <Typography variant="caption">
                          {t('dashboard.dueDate')}: {formattedDate}
                        </Typography>
                      </Stack>
                      {task.assignedTo && (
                        <Stack direction="row" spacing={0.5} alignItems="center">
                          <PersonIcon fontSize="small" />
                          <Typography variant="caption">
                            {t('dashboard.assignedTo', { name: task.assignedTo })}
                          </Typography>
                        </Stack>
                      )}
                    </Stack>
                  </Stack>
                }
              />

              {/* Task Actions */}
              {task.actions && task.actions.length > 0 && (
                <Stack direction="row" spacing={1} mt={2}>
                  {task.actions.map((action) => (
                    <Button
                      key={action.id}
                      variant="contained"
                      size="small"
                      onClick={() => handleAction(task.id, action.id, action.requiresConfirmation)}
                    >
                      {action.label}
                    </Button>
                  ))}
                </Stack>
              )}
            </ListItem>
          );
        })}
      </List>

      {/* Confirmation Dialog */}
      <Dialog open={!!confirmationDialog} onClose={handleCancelConfirmation}>
        <DialogTitle>{t('dashboard.confirmAction')}</DialogTitle>
        <DialogContent>
          <Typography>{t('dashboard.areYouSure')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelConfirmation}>{t('common.cancel')}</Button>
          <Button onClick={handleConfirm} variant="contained" color="primary">
            {t('common.confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
