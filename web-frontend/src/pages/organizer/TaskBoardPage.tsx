/**
 * TaskBoardPage Component (Story 5.5 - Modified for routing)
 *
 * AC24: Full Kanban task board page (converted from modal)
 * Wireframe: docs/wireframes/5.5-content-review-task-system-ux-flow.md
 *
 * Features:
 * - Full-width page layout
 * - 4-column Kanban: Pending | To Do | In Progress | Completed
 * - Filters: All Events, My Tasks, Sort by Due Date
 * - Pending column shows tasks waiting for trigger state (with complete button)
 * - To Do column shows ready tasks (with complete button)
 * - In Progress column shows active work (with complete button)
 * - Completed column shows finished tasks with timestamps
 * - "Add Custom Task" button
 * - Task completion opens notes modal
 * - Drag-and-drop between columns to change status
 */

import React, { useState } from 'react';
import {
  Container,
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
  Add as AddIcon,
  CheckCircle as CheckCircleIcon,
  HourglassEmpty as PendingIcon,
  PlayArrow as TodoIcon,
  Autorenew as InProgressIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { de, enUS, type Locale } from 'date-fns/locale';
import {
  DndContext,
  DragEndEvent,
  DragStartEvent,
  DragOverlay,
  closestCenter,
  useSensor,
  useSensors,
  PointerSensor,
  useDraggable,
  useDroppable,
} from '@dnd-kit/core';
import { taskService, type EventTaskResponse } from '@/services/taskService';
import { CustomTaskModal } from '@/components/organizer/Tasks/CustomTaskModal';
import { useAuth } from '@/hooks/useAuth';

const TaskBoardPage: React.FC = () => {
  const { t, i18n } = useTranslation('events');
  const queryClient = useQueryClient();
  const locale = i18n.language === 'de' ? de : enUS;
  const { user } = useAuth();
  const organizerUsername = user?.username || '';

  const [filter, setFilter] = useState<'all' | 'mine'>('mine');
  const [sortBy, setSortBy] = useState<'dueDate' | 'createdAt'>('dueDate');
  const [isCustomTaskModalOpen, setIsCustomTaskModalOpen] = useState(false);
  const [completingTaskId, setCompletingTaskId] = useState<string | null>(null);
  const [completionNotes, setCompletionNotes] = useState('');
  const [activeTask, setActiveTask] = useState<EventTaskResponse | null>(null);

  // Drag-and-drop sensors
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8, // 8px movement required to activate drag
      },
    })
  );

  // Fetch tasks based on filter
  const {
    data: allTasks = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['tasks', filter, organizerUsername],
    queryFn: () => (filter === 'mine' ? taskService.getMyTasks(false) : taskService.getAllTasks(false)),
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

  // Update task status mutation (drag-and-drop)
  const updateStatusMutation = useMutation({
    mutationFn: ({ taskId, newStatus }: { taskId: string; newStatus: 'pending' | 'todo' | 'in_progress' | 'completed' }) =>
      taskService.updateTaskStatus(taskId, newStatus),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
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
  const todoTasks = sortedTasks.filter((task) => task.status === 'todo');
  const inProgressTasks = sortedTasks.filter((task) => task.status === 'in_progress');
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

  const handleDragStart = (event: DragStartEvent) => {
    const taskId = event.active.id as string;
    const task = allTasks.find((t) => t.id === taskId);
    setActiveTask(task || null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    setActiveTask(null);

    if (!over || active.id === over.id) {
      return;
    }

    const taskId = active.id as string;
    const newStatus = over.id as 'pending' | 'todo' | 'in_progress' | 'completed';
    const task = allTasks.find((t) => t.id === taskId);

    if (task && task.status !== newStatus) {
      // Update task status via API
      updateStatusMutation.mutate({
        taskId,
        newStatus,
      });
    }
  };

  return (
    <>
      <Container maxWidth={false} sx={{ py: 3 }}>
        {/* Page Header */}
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
          <Typography variant="h4">{t('tasks.taskBoard', 'Task Board')}</Typography>
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
          </Stack>
        </Stack>

        {/* Task Board Content */}
        {isLoading && (
          <Box display="flex" justifyContent="center" alignItems="center" minHeight={400}>
            <CircularProgress />
          </Box>
        )}

        {isError && (
          <Alert severity="error">{t('tasks.errorLoadingTasks', 'Failed to load tasks')}</Alert>
        )}

        {!isLoading && !isError && (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}
          >
            <Grid container spacing={3}>
              {/* Pending Column */}
              <Grid size={{ xs: 12, md: 3 }}>
                <DroppableColumn columnId="pending">
                  <Paper
                    sx={{
                      p: 2,
                      bgcolor: 'grey.50',
                      height: 'calc(100vh - 220px)',
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                  >
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
                    <TaskColumn
                      tasks={pendingTasks}
                      status="pending"
                      locale={locale}
                      onComplete={handleCompleteTask}
                      t={t}
                    />
                  </Paper>
                </DroppableColumn>
              </Grid>

              {/* To Do Column */}
              <Grid size={{ xs: 12, md: 3 }}>
                <DroppableColumn columnId="todo">
                  <Paper
                    sx={{
                      p: 2,
                      bgcolor: 'warning.lighter',
                      height: 'calc(100vh - 220px)',
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                  >
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
                      {t('tasks.todoDescription', 'Ready to work on')}
                    </Typography>
                    <TaskColumn
                      tasks={todoTasks}
                      status="todo"
                      locale={locale}
                      onComplete={handleCompleteTask}
                      t={t}
                    />
                  </Paper>
                </DroppableColumn>
              </Grid>

              {/* In Progress Column */}
              <Grid size={{ xs: 12, md: 3 }}>
                <DroppableColumn columnId="in_progress">
                  <Paper
                    sx={{
                      p: 2,
                      bgcolor: 'info.lighter',
                      height: 'calc(100vh - 220px)',
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                  >
                    <Stack direction="row" spacing={1} alignItems="center" mb={2}>
                      <InProgressIcon color="info" />
                      <Typography variant="h6">{t('tasks.inProgress', 'In Progress')}</Typography>
                      <Chip label={inProgressTasks.length} size="small" color="info" />
                    </Stack>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ mb: 2, display: 'block' }}
                    >
                      {t('tasks.inProgressDescription', 'Currently being worked on')}
                    </Typography>
                    <TaskColumn
                      tasks={inProgressTasks}
                      status="in_progress"
                      locale={locale}
                      onComplete={handleCompleteTask}
                      t={t}
                    />
                  </Paper>
                </DroppableColumn>
              </Grid>

              {/* Completed Column */}
              <Grid size={{ xs: 12, md: 3 }}>
                <DroppableColumn columnId="completed">
                  <Paper
                    sx={{
                      p: 2,
                      bgcolor: 'success.lighter',
                      height: 'calc(100vh - 220px)',
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                  >
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
                </DroppableColumn>
              </Grid>
            </Grid>

            {/* Drag Overlay */}
            <DragOverlay>
              {activeTask ? (
                <Paper sx={{ p: 2, opacity: 0.8, cursor: 'grabbing' }}>
                  <Typography variant="subtitle2" fontWeight="bold">
                    {activeTask.taskName}
                  </Typography>
                </Paper>
              ) : null}
            </DragOverlay>
          </DndContext>
        )}
      </Container>

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
 * DroppableColumn Component
 *
 * Droppable wrapper for task columns
 */
interface DroppableColumnProps {
  columnId: string;
  children: React.ReactNode;
}

const DroppableColumn: React.FC<DroppableColumnProps> = ({ columnId, children }) => {
  const { setNodeRef } = useDroppable({
    id: columnId,
  });

  return <div ref={setNodeRef}>{children}</div>;
};

/**
 * DraggableTask Component
 *
 * Individual draggable task item
 */
interface DraggableTaskProps {
  task: EventTaskResponse;
  status: 'pending' | 'todo' | 'in_progress' | 'completed';
  locale: Locale;
  onComplete?: (taskId: string) => void;
  t: ReturnType<typeof useTranslation>['t'];
}

const DraggableTask: React.FC<DraggableTaskProps> = ({ task, status, locale, onComplete, t }) => {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: task.id,
  });

  const style = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
        opacity: isDragging ? 0.5 : 1,
        cursor: isDragging ? 'grabbing' : 'grab',
      }
    : undefined;

  return (
    <ListItem
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      sx={{
        border: 1,
        borderColor: 'divider',
        borderRadius: 1,
        mb: 1,
        flexDirection: 'column',
        alignItems: 'stretch',
        bgcolor: 'background.paper',
        cursor: 'grab',
        '&:active': {
          cursor: 'grabbing',
        },
      }}
    >
      {/* Task Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="start" width="100%">
        <Typography variant="subtitle2" fontWeight="bold">
          {task.taskName}
        </Typography>
        {/* Show complete button for both pending and todo status */}
        {(status === 'pending' || status === 'todo') && onComplete && (
          <Tooltip title={t('tasks.markComplete', 'Mark complete')}>
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation();
                onComplete(task.id);
              }}
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
            {task.eventCode && (
              <Chip
                label={task.eventCode}
                size="small"
                color="primary"
                sx={{ fontSize: '0.7rem', height: 22, width: 'fit-content', fontWeight: 'bold' }}
              />
            )}
            {(task.dueDate || task.assignedOrganizerUsername) && (
              <Stack direction="row" spacing={2} flexWrap="wrap">
                {task.dueDate && (
                  <Typography variant="caption" color="text.secondary">
                    {t('tasks.due', 'Due')}:{' '}
                    {format(new Date(task.dueDate), 'dd MMM yyyy HH:mm', { locale })}
                  </Typography>
                )}
                {task.assignedOrganizerUsername && (
                  <Typography variant="caption" color="text.secondary">
                    {task.assignedOrganizerUsername}
                  </Typography>
                )}
              </Stack>
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
  );
};

/**
 * TaskColumn Component
 *
 * Individual column for Kanban board
 */
interface TaskColumnProps {
  tasks: EventTaskResponse[];
  status: 'pending' | 'todo' | 'in_progress' | 'completed';
  locale: Locale;
  onComplete?: (taskId: string) => void;
  t: ReturnType<typeof useTranslation>['t'];
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
    <List sx={{ flexGrow: 1, overflow: 'auto', minHeight: 0 }}>
      {tasks.map((task) => (
        <DraggableTask
          key={task.id}
          task={task}
          status={status}
          locale={locale}
          onComplete={onComplete}
          t={t}
        />
      ))}
    </List>
  );
};

export default TaskBoardPage;
