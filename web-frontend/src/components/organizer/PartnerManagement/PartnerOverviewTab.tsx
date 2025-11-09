/**
 * PartnerOverviewTab Component
 *
 * Displays partnership details, engagement metrics, and recent activity
 * Story 2.8.2: Partner Detail View - AC4
 */

import React from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
  Chip,
  Divider,
  Stack,
} from '@mui/material';
import { CheckCircle, TrendingUp } from '@mui/icons-material';
import { usePartnerDetailStore } from '@/stores/partnerDetailStore';
import type { PartnerResponse } from '@/services/api/partnerApi';

interface PartnerOverviewTabProps {
  partner: PartnerResponse;
}

// Partnership tier emojis
const TIER_EMOJIS: Record<string, string> = {
  STRATEGIC: '🏆',
  PLATINUM: '💎',
  GOLD: '🥇',
  SILVER: '🥈',
  BRONZE: '🥉',
};

// Partnership benefits by tier
const TIER_BENEFITS: Record<string, string[]> = {
  STRATEGIC: [
    'Logo placement on website',
    'Newsletter mentions',
    'Priority event access',
    'Quarterly strategic meetings',
    'ROI analytics dashboard',
    'Dedicated account manager',
  ],
  PLATINUM: [
    'Logo placement on website',
    'Newsletter mentions',
    'Priority event access',
    'Quarterly strategic meetings',
  ],
  GOLD: ['Logo placement on website', 'Newsletter mentions', 'Priority event access'],
  SILVER: ['Newsletter mentions', 'Event access'],
  BRONZE: ['Event access'],
};

// Format date to "Month DD, YYYY" format
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
};

// Format date to "Jan 1, 2024" format
const formatShortDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
};

export const PartnerOverviewTab: React.FC<PartnerOverviewTabProps> = ({ partner }) => {
  const { setShowEditModal } = usePartnerDetailStore();

  const tierEmoji = TIER_EMOJIS[partner.partnershipLevel] || '';
  const benefits = TIER_BENEFITS[partner.partnershipLevel] || [];
  const previousTierEmoji = partner.previousTier ? TIER_EMOJIS[partner.previousTier] : '';

  return (
    <Box>
      <Grid container spacing={3}>
        {/* Partnership Details Panel */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Partnership Details
              </Typography>
              <Divider sx={{ mb: 2 }} />

              <Stack spacing={2}>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Partnership Tier
                  </Typography>
                  <Typography variant="h6">
                    {tierEmoji} {partner.partnershipLevel}
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Tier Start Date
                  </Typography>
                  <Typography variant="body1">{formatShortDate(partner.tierStartDate)}</Typography>
                </Box>

                {partner.previousTier && (
                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Previous Tier
                    </Typography>
                    <Typography variant="body1">
                      {previousTierEmoji} {partner.previousTier}
                    </Typography>
                  </Box>
                )}

                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Benefits
                  </Typography>
                  <Stack spacing={1}>
                    {benefits.map((benefit, index) => (
                      <Box key={index} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <CheckCircle color="success" fontSize="small" />
                        <Typography variant="body2">{benefit}</Typography>
                      </Box>
                    ))}
                  </Stack>
                </Box>

                {partner.company && (
                  <>
                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        Swiss UID
                      </Typography>
                      <Typography variant="body1">{partner.company.swissUid}</Typography>
                    </Box>

                    <Box>
                      <Typography variant="body2" color="text.secondary">
                        Tax Status
                      </Typography>
                      <Typography variant="body1">{partner.company.taxStatus}</Typography>
                    </Box>
                  </>
                )}

                <Button variant="outlined" onClick={() => setShowEditModal(true)} fullWidth>
                  Change Tier
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        {/* Engagement Metrics Panel */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Engagement Metrics
              </Typography>
              <Divider sx={{ mb: 2 }} />

              <Box sx={{ textAlign: 'center', py: 2 }}>
                <Typography variant="body1" color="text.secondary" gutterBottom>
                  📊 Coming Soon - Epic 8
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Advanced engagement metrics and analytics will be available in the next release
                </Typography>

                <Stack spacing={2}>
                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Event Attendance
                    </Typography>
                    <Typography variant="h6" color="text.disabled">
                      --
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Topic Voting
                    </Typography>
                    <Typography variant="h6" color="text.disabled">
                      --
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Meeting Participation
                    </Typography>
                    <Typography variant="h6" color="text.disabled">
                      --
                    </Typography>
                  </Box>

                  <Box>
                    <Typography variant="body2" color="text.secondary">
                      Content Interaction
                    </Typography>
                    <Typography variant="h6" color="text.disabled">
                      --
                    </Typography>
                  </Box>
                </Stack>

                <Button variant="text" endIcon={<TrendingUp />} disabled sx={{ mt: 2 }}>
                  View Full Analytics →
                </Button>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Recent Activity Panel */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Recent Activity
              </Typography>
              <Divider sx={{ mb: 2 }} />

              <Grid container spacing={3}>
                {/* Last Event */}
                <Grid item xs={12} md={4}>
                  <Box>
                    <Typography variant="subtitle1" gutterBottom>
                      Last Event
                    </Typography>
                    {partner.lastEvent ? (
                      <Stack spacing={1}>
                        <Typography variant="body1" fontWeight="bold">
                          {partner.lastEvent.eventName}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {formatDate(partner.lastEvent.attendedAt)}
                        </Typography>
                        <Box>
                          <Typography variant="body2" color="text.secondary">
                            {partner.lastEvent.attendeeCount} attendees •{' '}
                            {partner.lastEvent.registrationsCount} registrations •{' '}
                            {partner.lastEvent.downloadsCount} downloads
                          </Typography>
                        </Box>
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No recent event
                      </Typography>
                    )}
                  </Box>
                </Grid>

                {/* Active Topic Votes */}
                <Grid item xs={12} md={4}>
                  <Box>
                    <Typography variant="subtitle1" gutterBottom>
                      Active Topic Votes
                    </Typography>
                    {partner.activeVotes && partner.activeVotes.length > 0 ? (
                      <Stack spacing={1}>
                        {partner.activeVotes.map((vote) => (
                          <Box key={vote.topicId}>
                            <Typography variant="body2">{vote.topicName}</Typography>
                            <Chip
                              label={vote.priority}
                              size="small"
                              color={
                                vote.priority === 'HIGH'
                                  ? 'error'
                                  : vote.priority === 'MEDIUM'
                                    ? 'warning'
                                    : 'default'
                              }
                              sx={{ mt: 0.5 }}
                            />
                          </Box>
                        ))}
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No active votes
                      </Typography>
                    )}
                  </Box>
                </Grid>

                {/* Next Meeting */}
                <Grid item xs={12} md={4}>
                  <Box>
                    <Typography variant="subtitle1" gutterBottom>
                      Next Meeting
                    </Typography>
                    {partner.nextMeeting ? (
                      <Stack spacing={1}>
                        <Typography variant="body1" fontWeight="bold">
                          {partner.nextMeeting.meetingType}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {formatDate(partner.nextMeeting.scheduledDate)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          📍 {partner.nextMeeting.location}
                        </Typography>
                        <Typography variant="body2">{partner.nextMeeting.agenda}</Typography>
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No upcoming meeting
                      </Typography>
                    )}
                  </Box>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};
