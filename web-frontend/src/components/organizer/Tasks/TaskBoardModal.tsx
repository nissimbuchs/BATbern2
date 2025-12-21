/**
 * TaskBoardModal Component (Story 5.5 Phase 6)
 *
 * AC24: Full Kanban task board modal
 * Wireframe: docs/wireframes/5.5-content-review-task-system-ux-flow.md
 *
 * Features:
 * - Full-screen modal (90% viewport width)
 * - 3-column Kanban: Pending | To Do | Completed
 * - Filters: All Events, My Tasks, Sort by Due Date
 * - Pending column shows tasks waiting for trigger state
 * - To Do column shows active tasks
 * - Completed column shows finished tasks with timestamps
 * - "Add Custom Task" button
 * - Task completion opens notes modal
 */

import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Grid,
  Paper,
  Stack,
  Chip,
  IconButton,
  TextField,
  MenuItem,
  List,
  ListItem,
  ListItemText,
  CircularProgress,
  Alert,
  Tooltip,
} from '@mui/material';
import {
  Close as CloseIcon,
  Add as AddIcon,
  CheckCircle as CheckCircleIcon,
  HourglassEmpty as PendingIcon,
  PlayArrow as TodoIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { de, enUS, type Locale } from 'date-fns/locale';
import { taskService, type EventTaskResponse } from '@/services/taskService';
import { CustomTaskModal } from './CustomTaskModal';

interface TaskBoardModalProps {
  open: boolean;
  onClose: () => void;
  organizerUsername: string;
}

export const TaskBoardModal: React.FC<TaskBoardModalProps> = ({
  open,
  onClose,
  organizerUsername,
}) => {
  const { t, i18n } = useTranslation('events');
  const queryClient = useQueryClient();
  const locale = i18n.language === 'de' ? de : enUS;

  const [filter, setFilter] = useState<'all' | 'mine'>('mine');
  const [sortBy, setSortBy] = useState<'dueDate' | 'createdAt'>('dueDate');
  const [isCustomTaskModalOpen, setIsCustomTaskModalOpen] = useState(false);
  const [completingTaskId, setCompletingTaskId] = useState<string | null>(null);
  const [completionNotes, setCompletionNotes] = useState('');

  // Fetch all tasks
  const {
    data: allTasks = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['tasks', 'my-tasks', organizerUsername],
    queryFn: () => taskService.getMyTasks(false),
    enabled: open,
  });

  // Complete task mutation
  const completeTaskMutation = useMutation({
    mutationFn: ({ taskId, notes }: { taskId: string; notes?: string }) =>
      taskService.completeTask(taskId, { notes }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      setCompletingTaskId(null);
      setCompletionNotes('');
    },
  });

  // Filter tasks
  const filteredTasks =
    filter === 'mine'
      ? allTasks.filter((task) => task.assignedOrganizerUsername === organizerUsername)
      : allTasks;

  // Sort tasks
  const sortedTasks = [...filteredTasks].sort((a, b) => {
    if (sortBy === 'dueDate') {
      if (!a.dueDate) return 1;
      if (!b.dueDate) return -1;
      return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
    }
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });

  // Categorize tasks by status
  const pendingTasks = sortedTasks.filter((task) => task.status === 'pending');
  const todoTasks = sortedTasks.filter(
    (task) => task.status === 'todo' || task.status === 'in_progress'
  );
  const completedTasks = sortedTasks.filter((task) => task.status === 'completed');

  const handleCompleteTask = (taskId: string) => {
    setCompletingTaskId(taskId);
  };

  const handleConfirmComplete = () => {
    if (completingTaskId) {
      completeTaskMutation.mutate({
        taskId: completingTaskId,
        notes: completionNotes || undefined,
      });
    }
  };

  const handleCancelComplete = () => {
    setCompletingTaskId(null);
    setCompletionNotes('');
  };

  return (
    <>
      <Dialog
        open={open}
        onClose={onClose}
        maxWidth={false}
        fullWidth
        PaperProps={{
          sx: {
            width: '90vw',
            maxHeight: '90vh',
          },
        }}
      >
        <DialogTitle>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h5">{t('tasks.taskBoard', 'Task Board')}</Typography>
            <Stack direction="row" spacing={2} alignItems="center">
              {/* Filters */}
              <TextField
                select
                size="small"
                value={filter}
                onChange={(e) => setFilter(e.target.value as 'all' | 'mine')}
                label={t('tasks.filter', 'Filter')}
                sx={{ minWidth: 150 }}
              >
                <MenuItem value="mine">{t('tasks.myTasks', 'My Tasks')}</MenuItem>
                <MenuItem value="all">{t('tasks.allTasks', 'All Tasks')}</MenuItem>
              </TextField>

              <TextField
                select
                size="small"
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as 'dueDate' | 'createdAt')}
                label={t('tasks.sortBy', 'Sort By')}
                sx={{ minWidth: 150 }}
              >
                <MenuItem value="dueDate">{t('tasks.dueDate', 'Due Date')}</MenuItem>
                <MenuItem value="createdAt">{t('tasks.createdDate', 'Created Date')}</MenuItem>
              </TextField>

              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setIsCustomTaskModalOpen(true)}
              >
                {t('tasks.addCustomTask', 'Add Custom Task')}
              </Button>

              <IconButton onClick={onClose}>
                <CloseIcon />
              </IconButton>
            </Stack>
          </Stack>
        </DialogTitle>

        <DialogContent dividers>
          {isLoading && (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
              <CircularProgress />
            </Box>
          )}

          {isError && (
            <Alert severity="error">{t('tasks.errorLoadingTasks', 'Failed to load tasks')}</Alert>
          )}

          {!isLoading && !isError && (
            <Grid container spacing={3}>
              {/* Pending Column */}
              <Grid size={{ xs: 12, md: 4 }}>
                <Paper sx={{ p: 2, bgcolor: 'grey.50', minHeight: 500 }}>
                  <Stack direction="row" spacing={1} alignItems="center" mb={2}>
                    <PendingIcon color="action" />
                    <Typography variant="h6">{t('tasks.pending', 'Pending')}</Typography>
                    <Chip label={pendingTasks.length} size="small" />
                  </Stack>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mb: 2, display: 'block' }}
                  >
                    {t('tasks.pendingDescription', 'Waiting for trigger state')}
                  </Typography>
                  <TaskColumn tasks={pendingTasks} status="pending" locale={locale} t={t} />
                </Paper>
              </Grid>

              {/* To Do Column */}
              <Grid size={{ xs: 12, md: 4 }}>
                <Paper sx={{ p: 2, bgcolor: 'warning.lighter', minHeight: 500 }}>
                  <Stack direction="row" spacing={1} alignItems="center" mb={2}>
                    <TodoIcon color="warning" />
                    <Typography variant="h6">{t('tasks.toDo', 'To Do')}</Typography>
                    <Chip label={todoTasks.length} size="small" color="warning" />
                  </Stack>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mb: 2, display: 'block' }}
                  >
                    {t('tasks.todoDescription', 'Active tasks')}
                  </Typography>
                  <TaskColumn
                    tasks={todoTasks}
                    status="todo"
                    locale={locale}
                    onComplete={handleCompleteTask}
                    t={t}
                  />
                </Paper>
              </Grid>

              {/* Completed Column */}
              <Grid size={{ xs: 12, md: 4 }}>
                <Paper sx={{ p: 2, bgcolor: 'success.lighter', minHeight: 500 }}>
                  <Stack direction="row" spacing={1} alignItems="center" mb={2}>
                    <CheckCircleIcon color="success" />
                    <Typography variant="h6">{t('tasks.completed', 'Completed')}</Typography>
                    <Chip label={completedTasks.length} size="small" color="success" />
                  </Stack>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ mb: 2, display: 'block' }}
                  >
                    {t('tasks.completedDescription', 'Finished tasks')}
                  </Typography>
                  <TaskColumn tasks={completedTasks} status="completed" locale={locale} t={t} />
                </Paper>
              </Grid>
            </Grid>
          )}
        </DialogContent>

        <DialogActions>
          <Button onClick={onClose}>{t('common.close', 'Close')}</Button>
        </DialogActions>
      </Dialog>

      {/* Task Completion Modal */}
      <Dialog open={!!completingTaskId} onClose={handleCancelComplete} maxWidth="sm" fullWidth>
        <DialogTitle>{t('tasks.completeTask', 'Complete Task')}</DialogTitle>
        <DialogContent>
          <TextField
            label={t('tasks.completionNotes', 'Completion Notes (Optional)')}
            multiline
            rows={4}
            fullWidth
            value={completionNotes}
            onChange={(e) => setCompletionNotes(e.target.value)}
            placeholder={t(
              'tasks.completionNotesPlaceholder',
              'Add any notes about task completion...'
            )}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelComplete}>{t('common.cancel', 'Cancel')}</Button>
          <Button
            onClick={handleConfirmComplete}
            variant="contained"
            color="success"
            disabled={completeTaskMutation.isPending}
          >
            {completeTaskMutation.isPending ? (
              <CircularProgress size={20} />
            ) : (
              t('tasks.markComplete', 'Mark Complete')
            )}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Custom Task Modal */}
      <CustomTaskModal
        open={isCustomTaskModalOpen}
        onClose={() => setIsCustomTaskModalOpen(false)}
        eventId={null} // Ad-hoc task not tied to specific event
        organizerUsername={organizerUsername}
      />
    </>
  );
};

/**
 * TaskColumn Component
 *
 * Individual column for Kanban board
 */
interface TaskColumnProps {
  tasks: EventTaskResponse[];
  status: 'pending' | 'todo' | 'completed';
  locale: Locale;
  onComplete?: (taskId: string) => void;
  t: (key: string, defaultValue?: string) => string;
}

const TaskColumn: React.FC<TaskColumnProps> = ({ tasks, status, locale, onComplete, t }) => {
  if (tasks.length === 0) {
    return (
      <Box textAlign="center" py={4}>
        <Typography variant="body2" color="text.secondary">
          {t('tasks.noTasks', 'No tasks')}
        </Typography>
      </Box>
    );
  }

  return (
    <List sx={{ maxHeight: 400, overflow: 'auto' }}>
      {tasks.map((task) => (
        <ListItem
          key={task.id}
          sx={{
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            mb: 1,
            flexDirection: 'column',
            alignItems: 'stretch',
            bgcolor: 'background.paper',
          }}
        >
          {/* Task Header */}
          <Stack direction="row" justifyContent="space-between" alignItems="start" width="100%">
            <Typography variant="subtitle2" fontWeight="bold">
              {task.taskName}
            </Typography>
            {status === 'todo' && onComplete && (
              <Tooltip title={t('tasks.markComplete', 'Mark complete')}>
                <IconButton size="small" onClick={() => onComplete(task.id)}>
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
                {task.dueDate && (
                  <Typography variant="caption" color="text.secondary">
                    {t('tasks.due', 'Due')}:{' '}
                    {format(new Date(task.dueDate), 'dd MMM yyyy HH:mm', { locale })}
                  </Typography>
                )}
                {task.assignedOrganizerUsername && (
                  <Typography variant="caption" color="text.secondary">
                    {t('tasks.assignedTo', 'Assigned to')}: {task.assignedOrganizerUsername}
                  </Typography>
                )}
                {task.triggerState && (
                  <Chip
                    label={task.triggerState}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.65rem', height: 20, mt: 0.5 }}
                  />
                )}
                {status === 'completed' && task.completedDate && (
                  <Typography variant="caption" color="success.main">
                    {t('tasks.completedOn', 'Completed on')}:{' '}
                    {format(new Date(task.completedDate), 'dd MMM yyyy HH:mm', { locale })}
                  </Typography>
                )}
                {status === 'completed' && task.completedByUsername && (
                  <Typography variant="caption" color="text.secondary">
                    {t('tasks.completedBy', 'Completed by')}: {task.completedByUsername}
                  </Typography>
                )}
                {task.notes && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ fontStyle: 'italic', mt: 0.5 }}
                  >
                    {task.notes}
                  </Typography>
                )}
              </Stack>
            }
          />
        </ListItem>
      ))}
    </List>
  );
};
