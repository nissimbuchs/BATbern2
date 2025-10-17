import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Avatar,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  Snackbar,
  Skeleton,
  
} from '@mui/material';
import {
  Add as AddIcon,
  LinkOff as UnlinkIcon,
  Person as PersonIcon,
} from '@mui/icons-material';

interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  avatarUrl?: string | null;
}

interface AssociatedUsersPanelProps {
  companyId: string;
  users?: User[];
  isLoading?: boolean;
  canEdit?: boolean;
  error?: string;
  searchResults?: User[];
  onLinkUser: (companyId: string, userId: string) => void;
  onUnlinkUser: (companyId: string, userId: string) => void;
  onSearchUsers?: (query: string) => void;
}

export const AssociatedUsersPanel: React.FC<AssociatedUsersPanelProps> = ({
  companyId,
  users = [],
  isLoading = false,
  canEdit = true,
  error,
  searchResults = [],
  onLinkUser,
  onUnlinkUser,
  onSearchUsers,
}) => {
  const [isLinkModalOpen, setIsLinkModalOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [unlinkUserId, setUnlinkUserId] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const handleOpenLinkModal = () => {
    setIsLinkModalOpen(true);
    setSearchQuery('');
  };

  const handleCloseLinkModal = () => {
    setIsLinkModalOpen(false);
    setSearchQuery('');
  };

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const query = event.target.value;
    setSearchQuery(query);
    if (onSearchUsers) {
      onSearchUsers(query);
    }
  };

  const handleLinkUser = (userId: string) => {
    onLinkUser(companyId, userId);
    handleCloseLinkModal();
    setSuccessMessage('User linked successfully');
  };

  const handleOpenUnlinkConfirmation = (userId: string) => {
    setUnlinkUserId(userId);
  };

  const handleCloseUnlinkConfirmation = () => {
    setUnlinkUserId(null);
  };

  const handleConfirmUnlink = () => {
    if (unlinkUserId) {
      onUnlinkUser(companyId, unlinkUserId);
      setUnlinkUserId(null);
      setSuccessMessage('User unlinked successfully');
    }
  };

  const handleCloseSuccessMessage = () => {
    setSuccessMessage(null);
  };

  const getUserInitials = (firstName: string, lastName: string) => {
    return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
  };

  const getUserToUnlink = () => {
    return users.find((u) => u.id === unlinkUserId);
  };

  // Loading state
  if (isLoading) {
    return (
      <Box data-testid="users-panel-skeleton">
        <Skeleton variant="rectangular" width="100%" height={200} />
      </Box>
    );
  }

  return (
    <Box data-testid="associated-users-panel">
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6">
          Associated Users ({users.length} {users.length === 1 ? 'user' : 'users'})
        </Typography>
        {canEdit && (
          <Button startIcon={<AddIcon />} variant="contained" onClick={handleOpenLinkModal}>
            Link User
          </Button>
        )}
      </Box>

      {/* Error message */}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {/* Empty state */}
      {users.length === 0 && (
        <Card>
          <CardContent>
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <PersonIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
              <Typography variant="body1" color="text.secondary">
                No users associated with this company
              </Typography>
              {canEdit && (
                <Button
                  startIcon={<AddIcon />}
                  variant="outlined"
                  onClick={handleOpenLinkModal}
                  sx={{ mt: 2 }}
                >
                  Link User
                </Button>
              )}
            </Box>
          </CardContent>
        </Card>
      )}

      {/* Users list */}
      {users.length > 0 && (
        <Card>
          <List>
            {users.map((user, index) => (
              <ListItem
                key={user.id}
                divider={index < users.length - 1}
                secondaryAction={
                  canEdit && (
                    <IconButton
                      edge="end"
                      aria-label="unlink"
                      onClick={() => handleOpenUnlinkConfirmation(user.id)}
                    >
                      <UnlinkIcon />
                    </IconButton>
                  )
                }
              >
                <ListItemAvatar>
                  <Avatar
                    src={user.avatarUrl || undefined}
                    alt={`${user.firstName} ${user.lastName}`}
                  >
                    {!user.avatarUrl && getUserInitials(user.firstName, user.lastName)}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Typography variant="body1">
                        {user.firstName} {user.lastName}
                      </Typography>
                      <Chip label={user.role} size="small" color="primary" />
                    </Box>
                  }
                  secondary={user.email}
                />
              </ListItem>
            ))}
          </List>
        </Card>
      )}

      {/* Link User Modal */}
      <Dialog open={isLinkModalOpen} onClose={handleCloseLinkModal} maxWidth="sm" fullWidth>
        <DialogTitle>Link User to Company</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            placeholder="Search users by name or email"
            value={searchQuery}
            onChange={handleSearchChange}
            sx={{ mt: 2 }}
            autoFocus
          />

          {/* Search results */}
          {searchResults.length > 0 && (
            <List sx={{ mt: 2 }}>
              {searchResults.map((user) => (
                <ListItem
                  key={user.id}
                  secondaryAction={
                    <Button
                      variant="contained"
                      size="small"
                      onClick={() => handleLinkUser(user.id)}
                      aria-label={`link ${user.firstName} ${user.lastName}`}
                    >
                      Link
                    </Button>
                  }
                >
                  <ListItemAvatar>
                    <Avatar
                      src={user.avatarUrl || undefined}
                      alt={`${user.firstName} ${user.lastName}`}
                    >
                      {!user.avatarUrl && getUserInitials(user.firstName, user.lastName)}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={`${user.firstName} ${user.lastName}`}
                    secondary={user.email}
                  />
                </ListItem>
              ))}
            </List>
          )}

          {/* No results message */}
          {searchQuery.length > 0 && searchResults.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 2, textAlign: 'center' }}>
              No users found. Try a different search query.
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseLinkModal}>Cancel</Button>
        </DialogActions>
      </Dialog>

      {/* Unlink Confirmation Dialog */}
      <Dialog
        open={unlinkUserId !== null}
        onClose={handleCloseUnlinkConfirmation}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>Confirm Unlink User</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to unlink{' '}
            {getUserToUnlink()?.firstName} {getUserToUnlink()?.lastName} from this company?
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseUnlinkConfirmation}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleConfirmUnlink}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      {/* Success Snackbar */}
      <Snackbar
        open={successMessage !== null}
        autoHideDuration={3000}
        onClose={handleCloseSuccessMessage}
        message={successMessage}
      />
    </Box>
  );
};
