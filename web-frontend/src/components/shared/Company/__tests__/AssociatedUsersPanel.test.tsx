import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AssociatedUsersPanel } from '@/components/shared/Company/AssociatedUsersPanel';

// Mock user data
const mockUsers = [
  {
    id: 'user-1',
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@test-company.ch',
    role: 'Speaker',
    avatarUrl: 'https://cdn.example.com/avatars/john-doe.png',
  },
  {
    id: 'user-2',
    firstName: 'Jane',
    lastName: 'Smith',
    email: 'jane.smith@test-company.ch',
    role: 'Organizer',
    avatarUrl: 'https://cdn.example.com/avatars/jane-smith.png',
  },
  {
    id: 'user-3',
    firstName: 'Bob',
    lastName: 'Johnson',
    email: 'bob.johnson@test-company.ch',
    role: 'Attendee',
    avatarUrl: null,
  },
];

describe('AssociatedUsersPanel Component', () => {
  const mockOnLinkUser = vi.fn();
  const mockOnUnlinkUser = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('AC9.1: Display list of users associated with company', () => {
    it('should_displayUsersList_when_usersLoaded', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
        />
      );

      // Verify all users are displayed
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
      expect(screen.getByText('Bob Johnson')).toBeInTheDocument();

      // Verify emails are displayed
      expect(screen.getByText('john.doe@test-company.ch')).toBeInTheDocument();
      expect(screen.getByText('jane.smith@test-company.ch')).toBeInTheDocument();
    });

    it('should_displayUserCount_when_usersExist', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
        />
      );

      // Verify user count is displayed
      expect(screen.getByText(/3 users/i)).toBeInTheDocument();
    });

    it('should_displayEmptyState_when_noUsersAssociated', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={[]}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
        />
      );

      // Verify empty state message
      expect(screen.getByText(/no users associated/i)).toBeInTheDocument();
    });

    it('should_displayLoadingSkeleton_when_usersLoading', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={[]}
          isLoading={true}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
        />
      );

      // Verify skeleton loader is shown
      expect(screen.getByTestId('users-panel-skeleton')).toBeInTheDocument();
    });
  });

  describe('AC9.2: Open user search modal when link user clicked', () => {
    it('should_openUserSearchModal_when_linkUserClicked', async () => {
      const user = userEvent.setup();

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          canEdit={true}
        />
      );

      const linkButton = screen.getByRole('button', { name: /link user/i });
      await user.click(linkButton);

      // Verify modal is opened
      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.getByPlaceholderText(/search users/i)).toBeInTheDocument();
      });
    });

    it('should_searchUsers_when_typingInSearchField', async () => {
      const user = userEvent.setup();
      const mockOnSearchUsers = vi.fn();

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          onSearchUsers={mockOnSearchUsers}
          canEdit={true}
        />
      );

      // Open modal
      const linkButton = screen.getByRole('button', { name: /link user/i });
      await user.click(linkButton);

      // Type in search field
      const searchInput = screen.getByPlaceholderText(/search users/i);
      await user.type(searchInput, 'Alice');

      // Verify search callback is called
      await waitFor(() => {
        expect(mockOnSearchUsers).toHaveBeenCalledWith('Alice');
      });
    });

    it('should_displaySearchResults_when_usersFound', async () => {
      const user = userEvent.setup();
      const searchResults = [
        {
          id: 'user-4',
          firstName: 'Alice',
          lastName: 'Brown',
          email: 'alice.brown@example.com',
          role: 'Speaker',
        },
      ];

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          searchResults={searchResults}
          canEdit={true}
        />
      );

      // Open modal
      const linkButton = screen.getByRole('button', { name: /link user/i });
      await user.click(linkButton);

      // Verify search results are displayed
      expect(screen.getByText('Alice Brown')).toBeInTheDocument();
      expect(screen.getByText('alice.brown@example.com')).toBeInTheDocument();
    });
  });

  describe('AC9.3: Call User Service API when user linked', () => {
    it('should_callUserServiceAPI_when_userLinked', async () => {
      const user = userEvent.setup();
      const searchResults = [
        {
          id: 'user-4',
          firstName: 'Alice',
          lastName: 'Brown',
          email: 'alice.brown@example.com',
          role: 'Speaker',
        },
      ];

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          searchResults={searchResults}
          canEdit={true}
        />
      );

      // Open modal
      const linkButton = screen.getByRole('button', { name: /link user/i });
      await user.click(linkButton);

      // Click on a search result to link the user
      const linkUserButton = screen.getByRole('button', { name: /link alice brown/i });
      await user.click(linkUserButton);

      // Verify onLinkUser callback is called
      expect(mockOnLinkUser).toHaveBeenCalledWith('company-123', 'user-4');
    });

    it('should_closeModal_when_userLinkedSuccessfully', async () => {
      const user = userEvent.setup();
      const searchResults = [
        {
          id: 'user-4',
          firstName: 'Alice',
          lastName: 'Brown',
          email: 'alice.brown@example.com',
          role: 'Speaker',
        },
      ];

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          searchResults={searchResults}
          canEdit={true}
        />
      );

      // Open modal
      const linkButton = screen.getByRole('button', { name: /link user/i });
      await user.click(linkButton);

      // Link user
      const linkUserButton = screen.getByRole('button', { name: /link alice brown/i });
      await user.click(linkUserButton);

      // Verify modal is closed
      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
    });

    it('should_displaySuccessMessage_when_userLinkedSuccessfully', async () => {
      const user = userEvent.setup();
      const searchResults = [
        {
          id: 'user-4',
          firstName: 'Alice',
          lastName: 'Brown',
          email: 'alice.brown@example.com',
          role: 'Speaker',
        },
      ];

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          searchResults={searchResults}
          canEdit={true}
        />
      );

      // Open modal and link user
      const linkButton = screen.getByRole('button', { name: /link user/i });
      await user.click(linkButton);

      const linkUserButton = screen.getByRole('button', { name: /link alice brown/i });
      await user.click(linkUserButton);

      // Verify success message
      await waitFor(() => {
        expect(screen.getByText(/user linked successfully/i)).toBeInTheDocument();
      });
    });
  });

  describe('AC9.4: Remove user when unlink button clicked', () => {
    it('should_removeUser_when_unlinkButtonClicked', async () => {
      const user = userEvent.setup();

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          canEdit={true}
        />
      );

      // Find and click unlink button for first user
      const unlinkButtons = screen.getAllByRole('button', { name: /unlink/i });
      await user.click(unlinkButtons[0]);

      // Verify confirmation dialog appears with user name
      expect(screen.getByText(/are you sure you want to unlink john doe/i)).toBeInTheDocument();
    });

    it('should_callOnUnlinkUser_when_confirmationAccepted', async () => {
      const user = userEvent.setup();

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          canEdit={true}
        />
      );

      // Click unlink button
      const unlinkButtons = screen.getAllByRole('button', { name: /unlink/i });
      await user.click(unlinkButtons[0]);

      // Confirm unlink
      const confirmButton = screen.getByRole('button', { name: /confirm/i });
      await user.click(confirmButton);

      // Verify callback is called
      expect(mockOnUnlinkUser).toHaveBeenCalledWith('company-123', 'user-1');
    });

    it('should_notCallOnUnlinkUser_when_confirmationCancelled', async () => {
      const user = userEvent.setup();

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          canEdit={true}
        />
      );

      // Click unlink button
      const unlinkButtons = screen.getAllByRole('button', { name: /unlink/i });
      await user.click(unlinkButtons[0]);

      // Cancel unlink
      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      // Verify callback is not called
      expect(mockOnUnlinkUser).not.toHaveBeenCalled();
    });

    it('should_displaySuccessMessage_when_userUnlinkedSuccessfully', async () => {
      const user = userEvent.setup();

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          canEdit={true}
        />
      );

      // Unlink user
      const unlinkButtons = screen.getAllByRole('button', { name: /unlink/i });
      await user.click(unlinkButtons[0]);

      const confirmButton = screen.getByRole('button', { name: /confirm/i });
      await user.click(confirmButton);

      // Verify success message
      await waitFor(() => {
        expect(screen.getByText(/user unlinked successfully/i)).toBeInTheDocument();
      });
    });
  });

  describe('Display user role badges', () => {
    it('should_displayRoleBadges_when_usersHaveRoles', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
        />
      );

      // Verify role badges are displayed
      expect(screen.getByText('Speaker')).toBeInTheDocument();
      expect(screen.getByText('Organizer')).toBeInTheDocument();
      expect(screen.getByText('Attendee')).toBeInTheDocument();
    });

    it('should_displayUserAvatar_when_avatarUrlProvided', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
        />
      );

      // Verify avatars are displayed
      const johnAvatar = screen.getByAltText(/john doe/i);
      expect(johnAvatar).toHaveAttribute('src', 'https://cdn.example.com/avatars/john-doe.png');
    });

    it('should_displayInitialsAvatar_when_noAvatarUrl', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
        />
      );

      // Verify initials avatar for user without avatar URL
      expect(screen.getByText('BJ')).toBeInTheDocument(); // Bob Johnson initials
    });
  });

  describe('Permission-based access', () => {
    it('should_hideLinkUserButton_when_userLacksPermission', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          canEdit={false}
        />
      );

      expect(screen.queryByRole('button', { name: /link user/i })).not.toBeInTheDocument();
    });

    it('should_hideUnlinkButtons_when_userLacksPermission', () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          canEdit={false}
        />
      );

      expect(screen.queryByRole('button', { name: /unlink/i })).not.toBeInTheDocument();
    });
  });

  describe('Error handling', () => {
    it('should_displayErrorMessage_when_linkUserFails', async () => {
      const user = userEvent.setup();

      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          error="Failed to link user"
          canEdit={true}
        />
      );

      // Verify error message is displayed
      expect(screen.getByText(/Failed to link user/i)).toBeInTheDocument();
    });

    it('should_displayErrorMessage_when_unlinkUserFails', async () => {
      render(
        <AssociatedUsersPanel
          companyId="company-123"
          users={mockUsers}
          onLinkUser={mockOnLinkUser}
          onUnlinkUser={mockOnUnlinkUser}
          error="Failed to unlink user"
          canEdit={true}
        />
      );

      // Verify error message is displayed
      expect(screen.getByText(/Failed to unlink user/i)).toBeInTheDocument();
    });
  });
});
