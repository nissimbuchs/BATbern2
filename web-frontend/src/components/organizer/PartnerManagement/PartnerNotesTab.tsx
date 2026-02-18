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

interface PartnerNotesTabProps {
  companyName: string;
}

interface Note {
  id: string;
  title: string;
  content: string;
  authorUsername: string;
  createdAt: string;
  updatedAt: string;
}

interface NoteFormData {
  title: string;
  content: string;
}

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

const PartnerNotesTab: React.FC<PartnerNotesTabProps> = ({ companyName }) => {
  const { t } = useTranslation('partners');
  const { showNoteModal, setShowNoteModal } = usePartnerDetailStore();
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
    if (window.confirm(`Are you sure you want to delete the note "${noteTitle}"?`)) {
      deleteNote(noteId);
    }
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
        <Typography variant="body2" color="text.secondary" mb={3}>
          Add your first note to track important information about this partner
        </Typography>
        <Button variant="contained" startIcon={<Add />} onClick={handleAddNote}>
          {t('detail.notesTab.addNote')}
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      {/* Header with add button */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h6">{t('detail.notesTab.title')}</Typography>
        <Button variant="contained" startIcon={<Add />} onClick={handleAddNote}>
          {t('detail.notesTab.addNote')}
        </Button>
      </Stack>

      {/* Notes list */}
      <Stack spacing={2}>
        {notes.map((note) => (
          <Card key={note.id} data-testid={`note-item-${note.id}`}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={2}>
                <Typography variant="h6" fontWeight="medium">
                  {'title' in note ? (note as Note).title : 'Note'}
                </Typography>
                <Stack direction="row" spacing={1}>
                  <IconButton
                    size="small"
                    onClick={() => handleEditNote(note as Note)}
                    aria-label="Edit"
                  >
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={() =>
                      handleDeleteNote(note.id, 'title' in note ? (note as Note).title : 'Note')
                    }
                    aria-label="Delete"
                    color="error"
                  >
                    <Delete fontSize="small" />
                  </IconButton>
                </Stack>
              </Stack>

              {/* Content preview */}
              <Typography variant="body2" color="text.secondary" mb={2}>
                {getContentPreview(note.content)}
              </Typography>

              {/* Author and timestamp */}
              <Stack direction="row" spacing={2} alignItems="center">
                <Chip
                  label={'authorUsername' in note ? (note as Note).authorUsername : 'Unknown'}
                  size="small"
                  variant="outlined"
                />
                <Typography variant="caption" color="text.secondary">
                  {formatDate(note.createdAt)}
                  {'updatedAt' in note &&
                    (note as Note).updatedAt !== note.createdAt &&
                    ' (edited)'}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>

      {/* Note Modal */}
      <Dialog open={showNoteModal} onClose={handleCloseModal} maxWidth="md" fullWidth>
        <DialogTitle>
          {editingNote ? t('detail.notesTab.addNote') : t('detail.notesTab.addNote')}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} mt={1}>
            <TextField
              label={t('detail.notesTab.titleLabel')}
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
          <Button onClick={handleCloseModal}>{t('modal.actions.cancel')}</Button>
          <Button
            variant="contained"
            onClick={handleSaveNote}
            disabled={!formData.title || !formData.content}
          >
            {t('modal.actions.save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PartnerNotesTab;
