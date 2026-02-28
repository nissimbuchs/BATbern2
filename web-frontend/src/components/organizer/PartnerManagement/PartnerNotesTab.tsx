import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Stack,
  Chip,
  Alert,
  IconButton,
  TextField,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import { Add, Edit, Delete, Note as NoteIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { usePartnerNotes } from '@/hooks/usePartnerNotes';
import { usePartnerDetailStore } from '@/stores/partnerDetailStore';
import type { PartnerNoteDTO } from '@/services/api/partnerNotesApi';

type UserRole = 'ORGANIZER' | 'PARTNER' | 'SPEAKER' | 'ATTENDEE';

interface PartnerNotesTabProps {
  companyName: string;
  role?: UserRole; // Story 8.0: read-only for PARTNER
}

interface NoteFormData {
  title: string;
  content: string;
}

interface DeleteConfirmState {
  noteId: string;
  noteTitle: string;
}

type Note = PartnerNoteDTO;

// Format date for display
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
};

// Extract plain text preview from HTML content
const getContentPreview = (htmlContent: string, maxLength: number = 100): string => {
  const div = document.createElement('div');
  div.innerHTML = htmlContent;
  const text = div.textContent || div.innerText || '';
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
};

const PartnerNotesTab: React.FC<PartnerNotesTabProps> = ({ companyName, role }) => {
  const { t } = useTranslation('partners');
  const { showNoteModal, setShowNoteModal } = usePartnerDetailStore();
  const isPartner = role === 'PARTNER';
  const {
    data: notes,
    isLoading,
    error,
    createNote,
    updateNote,
    deleteNote,
  } = usePartnerNotes(companyName);

  const [editingNote, setEditingNote] = useState<Note | null>(null);
  const [formData, setFormData] = useState<NoteFormData>({ title: '', content: '' });
  const [deleteConfirm, setDeleteConfirm] = useState<DeleteConfirmState | null>(null);

  const handleAddNote = () => {
    setEditingNote(null);
    setFormData({ title: '', content: '' });
    setShowNoteModal(true);
  };

  const handleEditNote = (note: Note) => {
    setEditingNote(note);
    setFormData({ title: note.title, content: note.content });
    setShowNoteModal(true);
  };

  const handleDeleteNote = (noteId: string, noteTitle: string) => {
    setDeleteConfirm({ noteId, noteTitle });
  };

  const handleConfirmDelete = () => {
    if (deleteConfirm) {
      deleteNote(deleteConfirm.noteId);
      setDeleteConfirm(null);
    }
  };

  const handleCancelDelete = () => {
    setDeleteConfirm(null);
  };

  const handleSaveNote = () => {
    if (editingNote) {
      updateNote({ noteId: editingNote.id, ...formData });
    } else {
      createNote(formData);
    }
    setShowNoteModal(false);
    setFormData({ title: '', content: '' });
    setEditingNote(null);
  };

  const handleCloseModal = () => {
    setShowNoteModal(false);
    setFormData({ title: '', content: '' });
    setEditingNote(null);
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="300px">
        <BATbernLoader size={96} />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error">
        <Typography variant="h6">Failed to load notes</Typography>
        <Typography variant="body2">{(error as Error).message}</Typography>
      </Alert>
    );
  }

  if (!notes || notes.length === 0) {
    return (
      <Box textAlign="center" py={6}>
        <NoteIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary" gutterBottom>
          {t('detail.notesTab.noNotes')}
        </Typography>
        {!isPartner && (
          <>
            <Typography variant="body2" color="text.secondary" mb={3}>
              Add your first note to track important information about this partner
            </Typography>
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={handleAddNote}
              data-testid="add-note-button"
            >
              {t('detail.notesTab.addNote')}
            </Button>
          </>
        )}

        {/* Note Modal — must be present in empty state too (showNoteModal set in store) */}
        <Dialog open={showNoteModal} onClose={handleCloseModal} maxWidth="md" fullWidth>
          <DialogTitle>{t('detail.notesTab.addNote')}</DialogTitle>
          <DialogContent>
            <Stack spacing={3} mt={1}>
              <TextField
                label={t('common:labels.title')}
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                fullWidth
                required
              />
              <TextField
                label={t('detail.notesTab.contentLabel')}
                value={formData.content}
                onChange={(e) => setFormData({ ...formData, content: e.target.value })}
                multiline
                rows={8}
                fullWidth
                required
                helperText={t('detail.notesTab.htmlSupported')}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseModal}>{t('common:actions.cancel')}</Button>
            <Button
              variant="contained"
              onClick={handleSaveNote}
              disabled={!formData.title || !formData.content}
            >
              {t('common:actions.save')}
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    );
  }

  return (
    <Box>
      {/* Header with add button — hidden for PARTNER */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">{t('detail.notesTab.title')}</Typography>
        {!isPartner && (
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={handleAddNote}
            data-testid="add-note-button"
          >
            {t('detail.notesTab.addNote')}
          </Button>
        )}
      </Stack>

      {/* Notes list */}
      <Stack spacing={2}>
        {notes.map((note) => (
          <Card key={note.id} data-testid={`note-item-${note.id}`}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={2}>
                <Typography variant="h6" fontWeight="medium">
                  {note.title}
                </Typography>
                {!isPartner && (
                  <Stack direction="row" spacing={1}>
                    <IconButton size="small" onClick={() => handleEditNote(note)} aria-label="Edit">
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => handleDeleteNote(note.id, note.title)}
                      aria-label="Delete"
                      color="error"
                    >
                      <Delete fontSize="small" />
                    </IconButton>
                  </Stack>
                )}
              </Stack>

              {/* Content preview */}
              <Typography variant="body2" color="text.secondary" mb={2}>
                {getContentPreview(note.content)}
              </Typography>

              {/* Author and timestamp */}
              <Stack direction="row" spacing={2} alignItems="center">
                <Chip label={note.authorUsername} size="small" variant="outlined" />
                <Typography variant="caption" color="text.secondary">
                  {formatDate(note.createdAt)}
                  {note.updatedAt !== note.createdAt && ' (edited)'}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>

      {/* Delete Confirmation Dialog */}
      <Dialog open={!!deleteConfirm} onClose={handleCancelDelete}>
        <DialogTitle>{t('detail.notesTab.deleteNoteConfirmTitle')}</DialogTitle>
        <DialogContent>
          <Typography>
            {t('detail.notesTab.deleteNoteConfirmMessage', {
              title: deleteConfirm?.noteTitle ?? '',
            })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCancelDelete}>{t('common:actions.cancel')}</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleConfirmDelete}
            data-testid="confirm-delete-note"
          >
            {t('common:actions.delete')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Note Modal */}
      <Dialog open={showNoteModal} onClose={handleCloseModal} maxWidth="md" fullWidth>
        <DialogTitle>
          {editingNote ? t('detail.notesTab.editNote') : t('detail.notesTab.addNote')}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} mt={1}>
            <TextField
              label={t('common:labels.title')}
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              fullWidth
              required
            />
            <TextField
              label={t('detail.notesTab.contentLabel')}
              value={formData.content}
              onChange={(e) => setFormData({ ...formData, content: e.target.value })}
              multiline
              rows={8}
              fullWidth
              required
              helperText={t('detail.notesTab.htmlSupported')}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseModal}>{t('common:actions.cancel')}</Button>
          <Button
            variant="contained"
            onClick={handleSaveNote}
            disabled={!formData.title || !formData.content}
          >
            {t('common:actions.save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PartnerNotesTab;
