/**
 * Unit Test: ProfileHeader Component
 * Story 2.6: User Account Management Frontend
 * Tests AC1-4: Profile header display
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProfileHeader from './ProfileHeader';

const mockUser = {
  id: 'user-123',
  username: 'anna.mueller',
  email: 'anna.mueller@techcorp.ch',
  emailVerified: true,
  firstName: 'Anna',
  lastName: 'Müller',
  bio: 'Passionate about distributed systems',
  profilePictureUrl: 'https://cdn.batbern.ch/logos/users/user-123/profile.jpg',
  company: { id: 'comp-1', name: 'TechCorp AG', uid: 'CHE-123.456.789' },
  roles: ['ORGANIZER', 'SPEAKER'],
  memberSince: '2020-01-15T00:00:00Z',
};

describe('ProfileHeader', () => {
  it('should_displayProfilePhoto_when_userHasPhoto', () => {
    // Test 1.1 (AC1): Profile header displays user photo (200×200px)
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    const profilePhoto = screen.getByTestId('profile-photo');
    expect(profilePhoto).toBeInTheDocument();
    expect(profilePhoto).toHaveAttribute('src', mockUser.profilePictureUrl);
    expect(profilePhoto).toHaveAttribute('alt', 'Anna Müller');
  });

  it('should_displayUserName_when_rendered', () => {
    // Test 1.1 (AC1): Profile header displays name
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    expect(screen.getByTestId('user-name')).toHaveTextContent('Anna Müller');
  });

  it('should_displayCompanyName_when_userHasCompany', () => {
    // Test 1.1 (AC1): Profile header displays company
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    expect(screen.getByTestId('user-company')).toHaveTextContent('TechCorp AG');
  });

  it('should_displayEmail_when_rendered', () => {
    // Test 1.1 (AC1): Profile header displays email
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    expect(screen.getByTestId('user-email')).toHaveTextContent('anna.mueller@techcorp.ch');
  });

  it('should_showVerifiedBadge_when_emailVerified', () => {
    // Test 1.2 (AC2): Email displays with ✓ Verified badge
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    const verifiedBadge = screen.getByTestId('email-verified-badge');
    expect(verifiedBadge).toBeInTheDocument();
    expect(verifiedBadge).toHaveTextContent('Verified');
  });

  it('should_notShowVerifiedBadge_when_emailNotVerified', () => {
    // Test 1.2 (AC2): No verified badge when email not verified
    const unverifiedUser = { ...mockUser, emailVerified: false };
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader
        user={unverifiedUser}
        onPhotoUpload={onPhotoUpload}
        onPhotoRemove={onPhotoRemove}
      />
    );

    expect(screen.queryByTestId('email-verified-badge')).not.toBeInTheDocument();
  });

  it('should_displayRoleBadges_when_userHasRoles', () => {
    // Test 1.3 (AC3): Role badges display all assigned roles
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    const roleBadges = screen.getAllByTestId('role-badge');
    expect(roleBadges).toHaveLength(2);
    expect(roleBadges[0]).toHaveTextContent('Organizer');
    expect(roleBadges[1]).toHaveTextContent('Speaker');
  });

  it('should_formatMemberSinceDate_when_rendered', () => {
    // Test 1.4 (AC4): Member since date displays in format "January 2020"
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    const memberSince = screen.getByTestId('member-since');
    expect(memberSince).toHaveTextContent('January 2020');
  });

  it('should_callOnPhotoUpload_when_uploadButtonClicked', async () => {
    // Test 1.9 (AC10): Upload New Photo button triggers callback
    const user = userEvent.setup();
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    const uploadButton = screen.getByTestId('upload-photo-button');
    await user.click(uploadButton);

    expect(onPhotoUpload).toHaveBeenCalledTimes(1);
  });

  it('should_callOnPhotoRemove_when_removeButtonClicked', async () => {
    // Test 1.13 (AC13): Remove Photo button triggers callback
    const user = userEvent.setup();
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    const removeButton = screen.getByTestId('remove-photo-button');
    await user.click(removeButton);

    expect(onPhotoRemove).toHaveBeenCalledTimes(1);
  });

  it('should_showDefaultAvatar_when_noProfilePicture', () => {
    // Test 1.13 (AC13): Default avatar shown when no profile picture
    const userWithoutPhoto = { ...mockUser, profilePictureUrl: undefined };
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader
        user={userWithoutPhoto}
        onPhotoUpload={onPhotoUpload}
        onPhotoRemove={onPhotoRemove}
      />
    );

    const profilePhoto = screen.getByTestId('profile-photo');
    expect(profilePhoto).toBeInTheDocument();
    // Should show initials or default avatar
    expect(profilePhoto).toHaveAttribute('alt', 'Anna Müller');
  });

  it('should_haveAccessibleLabels_when_rendered', () => {
    // Test 5.14 (AC45): Screen reader compatible with ARIA labels
    const onPhotoUpload = vi.fn();
    const onPhotoRemove = vi.fn();

    render(
      <ProfileHeader user={mockUser} onPhotoUpload={onPhotoUpload} onPhotoRemove={onPhotoRemove} />
    );

    const uploadButton = screen.getByTestId('upload-photo-button');
    expect(uploadButton).toHaveAttribute('aria-label');

    const removeButton = screen.getByTestId('remove-photo-button');
    expect(removeButton).toHaveAttribute('aria-label');
  });
});
