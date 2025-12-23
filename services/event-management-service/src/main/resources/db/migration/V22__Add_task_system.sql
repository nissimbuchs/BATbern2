-- V22: Add task system for configurable event planning tasks
-- Story 5.5: Speaker Content Submission, Quality Review & Configurable Task System

-- Create task_templates table
CREATE TABLE IF NOT EXISTS task_templates (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  trigger_state VARCHAR(50) NOT NULL,
  due_date_type VARCHAR(20) NOT NULL CHECK (due_date_type IN ('immediate', 'relative_to_event', 'absolute')),
  due_date_offset_days INTEGER,
  is_default BOOLEAN DEFAULT false,
  created_by_username VARCHAR(100),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create event_tasks table with optimistic locking
CREATE TABLE IF NOT EXISTS event_tasks (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  template_id UUID REFERENCES task_templates(id),
  task_name VARCHAR(255) NOT NULL,
  trigger_state VARCHAR(50) NOT NULL,
  due_date TIMESTAMP WITH TIME ZONE,
  assigned_organizer_username VARCHAR(100),
  status VARCHAR(20) DEFAULT 'todo' CHECK (status IN ('pending', 'todo', 'in_progress', 'completed')),
  notes TEXT,
  completed_date TIMESTAMP WITH TIME ZONE,
  completed_by_username VARCHAR(100),
  version BIGINT DEFAULT 0 NOT NULL,  -- Optimistic locking (AC35)
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_task_templates_default ON task_templates(is_default);
CREATE INDEX IF NOT EXISTS idx_event_tasks_event_id ON event_tasks(event_id);
CREATE INDEX IF NOT EXISTS idx_event_tasks_assigned_organizer ON event_tasks(assigned_organizer_username);
CREATE INDEX IF NOT EXISTS idx_event_tasks_status ON event_tasks(status);
CREATE INDEX IF NOT EXISTS idx_event_tasks_due_date ON event_tasks(due_date);

-- Unique constraint for task idempotency (AC36)
CREATE UNIQUE INDEX idx_event_tasks_unique_template
  ON event_tasks(event_id, template_id)
  WHERE template_id IS NOT NULL;

COMMENT ON INDEX idx_event_tasks_unique_template IS
  'Prevent duplicate tasks from same template for same event (AC36)';

-- Triggers
DROP TRIGGER IF EXISTS update_task_templates_updated_at ON task_templates;
CREATE TRIGGER update_task_templates_updated_at BEFORE UPDATE ON task_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_event_tasks_updated_at ON event_tasks;
CREATE TRIGGER update_event_tasks_updated_at BEFORE UPDATE ON event_tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Seed default templates (AC19)
INSERT INTO task_templates (name, trigger_state, due_date_type, due_date_offset_days, is_default)
VALUES
  ('Venue Booking', 'topic_selection', 'relative_to_event', -90, true),
  ('Partner Meeting Coordination', 'topic_selection', 'relative_to_event', 0, true),
  ('Moderator Assignment', 'topic_selection', 'relative_to_event', -14, true),
  ('Newsletter: Topic Announcement', 'topic_selection', 'immediate', 0, true),
  ('Newsletter: Speaker Lineup', 'agenda_published', 'relative_to_event', -30, true),
  ('Newsletter: Final Agenda', 'agenda_finalized', 'relative_to_event', -14, true),
  ('Catering Coordination', 'agenda_finalized', 'relative_to_event', -30, true);

-- Comments
COMMENT ON TABLE task_templates IS 'Reusable task templates for event planning workflow - Story 5.5';
COMMENT ON TABLE event_tasks IS 'Event-specific tasks assigned to organizers - Story 5.5';
COMMENT ON COLUMN speaker_pool.session_id IS 'FK to sessions table - links speaker to their presentation session';
