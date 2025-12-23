/**
 * TaskCard Component (Shared)
 *
 * Reusable task card component used in:
 * - TaskWidget (critical tasks sidebar)
 * - TaskBoardPage (Kanban board)
 *
 * Features:
 * - Displays task name, event code, due date, assignee
 * - Optional complete button
 * - Optional trigger state and completion info
 * - Configurable styling via sx prop
 */

import React from 'react';
import {
  ListItem,
  ListItemText,
  Typography,
  Stack,
  Chip,
  IconButton,
  Tooltip,
} from '@mui/material';
import { CheckCircle as CheckCircleIcon } from '@mui/icons-material';
import { format } from 'date-fns';
import { type Locale } from 'date-fns/locale';
import { type EventTaskResponse } from '@/services/taskService';
import { type SxProps, type Theme } from '@mui/material/styles';

export interface TaskCardProps {
  task: EventTaskResponse;
  locale: Locale;
  onComplete?: (taskId: string) => void;
  showCompleteButton?: boolean;
  showEventCode?: boolean;
  showTriggerState?: boolean;
  showCompletionInfo?: boolean;
  sx?: SxProps<Theme>;
  t: (key: string, fallback?: string) => string;
  // Drag-and-drop props (optional)
  draggableRef?: React.Ref<HTMLLIElement>;
  draggableStyle?: React.CSSProperties;
  draggableListeners?: Record<string, (event: React.SyntheticEvent) => void>;
  draggableAttributes?: Record<string, unknown>;
}

export const TaskCard: React.FC<TaskCardProps> = ({
  task,
  locale,
  onComplete,
  showCompleteButton = true,
  showEventCode = true,
  showTriggerState = false,
  showCompletionInfo = false,
  sx,
  t,
  draggableRef,
  draggableStyle,
  draggableListeners,
  draggableAttributes,
}) => {
  const formattedDueDate = task.dueDate
    ? format(new Date(task.dueDate), 'dd MMM yyyy HH:mm', { locale })
    : null;

  const formattedCompletedDate =
    showCompletionInfo && task.completedDate
      ? format(new Date(task.completedDate), 'dd MMM yyyy HH:mm', { locale })
      : null;

  return (
    <ListItem
      ref={draggableRef}
      style={draggableStyle}
      {...draggableListeners}
      {...draggableAttributes}
      sx={{
        border: 1,
        borderColor: 'divider',
        borderRadius: 1,
        mb: 1,
        flexDirection: 'column',
        alignItems: 'stretch',
        bgcolor: 'background.paper',
        ...sx,
      }}
    >
      {/* Task Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="start" width="100%">
        <Typography variant="subtitle2" fontWeight="bold">
          {task.taskName}
        </Typography>
        {showCompleteButton && onComplete && task.status !== 'completed' && (
          <Tooltip title={t('tasks.markComplete', 'Mark complete')}>
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation();
                onComplete(task.id);
              }}
              aria-label="Complete task"
            >
              <CheckCircleIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>

      {/* Task Details */}
      <ListItemText
        secondaryTypographyProps={{ component: 'div' }}
        secondary={
          <Stack spacing={0.5} mt={1}>
            {/* Event Code */}
            {showEventCode && task.eventCode && (
              <Chip
                label={task.eventCode}
                size="small"
                color="primary"
                sx={{ fontSize: '0.7rem', height: 22, width: 'fit-content', fontWeight: 'bold' }}
              />
            )}

            {/* Due Date and Assignee */}
            {(formattedDueDate || task.assignedOrganizerUsername) && (
              <Stack direction="row" spacing={2} flexWrap="wrap">
                {formattedDueDate && (
                  <Typography variant="caption" color="text.secondary">
                    {t('tasks.due', 'Due')}: {formattedDueDate}
                  </Typography>
                )}
                {task.assignedOrganizerUsername && (
                  <Typography variant="caption" color="text.secondary">
                    {task.assignedOrganizerUsername}
                  </Typography>
                )}
              </Stack>
            )}

            {/* Trigger State */}
            {showTriggerState && task.triggerState && (
              <Chip
                label={task.triggerState}
                size="small"
                variant="outlined"
                sx={{ fontSize: '0.65rem', height: 20, mt: 0.5, width: 'fit-content' }}
              />
            )}

            {/* Completion Info */}
            {showCompletionInfo && task.status === 'completed' && (
              <>
                {formattedCompletedDate && (
                  <Typography variant="caption" color="success.main">
                    {t('tasks.completedOn', 'Completed on')}: {formattedCompletedDate}
                  </Typography>
                )}
                {task.completedByUsername && (
                  <Typography variant="caption" color="text.secondary">
                    {t('tasks.completedBy', 'Completed by')}: {task.completedByUsername}
                  </Typography>
                )}
              </>
            )}

            {/* Notes */}
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
