/**
 * The due date of a task template in words (#1223): "90 days before the event", "1 day after the
 * event", "On the event day", "Immediate".
 *
 * `dueDateOffsetDays` is relative to the event date: negative before it, positive after it
 * (event-tasks-api `TaskTemplateResponse`, and `EventTaskService.calculateDueDate` adds it to the
 * event date). The admin list printed "-90d before event", and the slides newsletter (+1) read
 * "1d before event".
 */
import type { TFunction } from 'i18next';
import type { TaskTemplateResponse } from '@/services/taskService';

type DueFields = Pick<TaskTemplateResponse, 'dueDateType' | 'dueDateOffsetDays'>;

/** `t` must be bound to the `events` namespace. */
export function templateDueLabel(t: TFunction<'events'>, template: DueFields): string {
  if (template.dueDateType === 'immediate') {
    return t('tasks.immediate');
  }
  if (template.dueDateType === 'relative_to_event') {
    // A missing offset is 0, as the backend computes it (EventTaskService.calculateDueDate).
    const offset = template.dueDateOffsetDays ?? 0;
    if (offset === 0) {
      return t('tasks.offset.eventDay');
    }
    return offset < 0
      ? t('tasks.offset.before', { count: -offset })
      : t('tasks.offset.after', { count: offset });
  }
  return template.dueDateType || '—';
}
