/**
 * Unit tests for GitHub Issues Lambda (CloudWatch alarm → GitHub issue integration)
 *
 * This Lambda receives SNS events from CloudWatch alarms and:
 * - ALARM state → creates or updates a GitHub issue
 * - OK state → closes the corresponding issue
 * - Errors per record are caught; processing continues for remaining records
 */

import { describe, it, expect, jest, beforeEach } from '@jest/globals';
import type { SNSEvent } from 'aws-lambda';

// ------------------------------------------------------------------
// Mocks
// ------------------------------------------------------------------

const mockSsmSend = jest.fn<(...args: any[]) => Promise<any>>();
const mockIssuesCreate = jest.fn<(...args: any[]) => Promise<any>>();
const mockIssuesUpdate = jest.fn<(...args: any[]) => Promise<any>>();
const mockIssuesCreateComment = jest.fn<(...args: any[]) => Promise<any>>();
const mockSearchIssues = jest.fn<(...args: any[]) => Promise<any>>();

jest.mock('@aws-sdk/client-ssm', () => ({
  SSMClient: jest.fn().mockImplementation(() => ({ send: mockSsmSend })),
  GetParameterCommand: jest.fn().mockImplementation((input) => input),
}));

jest.mock('@octokit/rest', () => ({
  Octokit: jest.fn().mockImplementation(() => ({
    issues: {
      create: mockIssuesCreate,
      update: mockIssuesUpdate,
      createComment: mockIssuesCreateComment,
    },
    search: {
      issuesAndPullRequests: mockSearchIssues,
    },
  })),
}));

import { handler } from '../../../lambda/github-issues-integration/index';

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function makeAlarmMessage(overrides: Partial<{
  AlarmName: string;
  NewStateValue: 'ALARM' | 'OK' | 'INSUFFICIENT_DATA';
  AlarmDescription: string;
}> = {}) {
  return {
    AlarmName: 'batbern-staging-EventManagement-High-Memory',
    AlarmDescription: 'Memory usage is high',
    NewStateValue: 'ALARM',
    NewStateReason: 'Threshold Crossed: memory > 80%',
    StateChangeTime: '2026-05-16T10:00:00.000+0000',
    Region: 'eu-central-1',
    AlarmArn: 'arn:aws:cloudwatch:eu-central-1:123:alarm:test-alarm',
    OldStateValue: 'OK',
    Trigger: {
      MetricName: 'MemoryUtilization',
      Namespace: 'AWS/ECS',
      Statistic: 'Average',
      Threshold: 80,
      ComparisonOperator: 'GreaterThanThreshold',
    },
    ...overrides,
  };
}

function makeSnsEvent(messages: object[]): SNSEvent {
  return {
    Records: messages.map((msg) => ({
      EventVersion: '1.0',
      EventSubscriptionArn: 'arn:aws:sns:test',
      EventSource: 'aws:sns',
      Sns: {
        SignatureVersion: '1',
        Timestamp: '2026-05-16T10:00:00.000Z',
        Signature: 'sig',
        SigningCertUrl: 'url',
        MessageId: 'msg-id',
        Message: JSON.stringify(msg),
        MessageAttributes: {},
        Type: 'Notification',
        UnsubscribeUrl: 'url',
        TopicArn: 'arn:aws:sns:test',
        Subject: 'alarm',
      },
    })),
  };
}

// ------------------------------------------------------------------
// Tests
// ------------------------------------------------------------------

describe('github-issues Lambda handler', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.GITHUB_OWNER = 'batbern';
    process.env.GITHUB_REPO = 'BATbern-develop';
    process.env.GITHUB_TOKEN_PARAM = '/batbern/production/github/token';

    // SSM returns a token
    mockSsmSend.mockResolvedValue({ Parameter: { Value: 'ghp_test_token' } });
    // No existing issue by default
    mockSearchIssues.mockResolvedValue({ data: { items: [] } });
    mockIssuesCreate.mockResolvedValue({ data: { number: 42 } });
    mockIssuesUpdate.mockResolvedValue({ data: { number: 42 } });
    mockIssuesCreateComment.mockResolvedValue({ data: { id: 1 } });
  });

  it('module loads without crashing', () => {
    expect(typeof handler).toBe('function');
  });

  it('should_createGitHubIssue_when_alarmEntersAlarmState', async () => {
    const event = makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]);
    await handler(event);

    expect(mockIssuesCreate).toHaveBeenCalledTimes(1);
    const createArgs = mockIssuesCreate.mock.calls[0][0] as { title: string; labels: string[] };
    expect(createArgs.title).toContain('CloudWatch Alarm Triggered');
    expect(createArgs.labels).toContain('incident');
    expect(createArgs.labels).toContain('severity:high'); // High-Memory → severity:high
    expect(createArgs.labels).toContain('component:ecs');
  });

  it('should_closeExistingIssue_when_alarmReturnsToOkState', async () => {
    mockSearchIssues.mockResolvedValue({
      data: { items: [{ number: 7, state: 'open' }] },
    });

    const event = makeSnsEvent([makeAlarmMessage({ NewStateValue: 'OK' })]);
    await handler(event);

    expect(mockIssuesUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ issue_number: 7, state: 'closed' })
    );
    expect(mockIssuesCreateComment).toHaveBeenCalledWith(
      expect.objectContaining({ issue_number: 7 })
    );
  });

  it('should_commentAndReopenIssue_when_alarmFiresAgainWithExistingClosedIssue', async () => {
    mockSearchIssues.mockResolvedValue({
      data: { items: [{ number: 5, state: 'closed' }] },
    });

    const event = makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]);
    await handler(event);

    expect(mockIssuesCreate).not.toHaveBeenCalled();
    expect(mockIssuesCreateComment).toHaveBeenCalledTimes(1);
    expect(mockIssuesUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ issue_number: 5, state: 'open' })
    );
  });

  it('should_applyCorrectSeverityLabel_when_alarmNameContainsCriticalKeyword', async () => {
    const event = makeSnsEvent([makeAlarmMessage({
      AlarmName: 'batbern-staging-api-availability',
      NewStateValue: 'ALARM',
    })]);
    await handler(event);

    const createArgs = mockIssuesCreate.mock.calls[0][0] as { labels: string[] };
    expect(createArgs.labels).toContain('severity:critical');
  });

  it('should_notThrow_when_SNSRecordContainsMalformedJSON', async () => {
    const event: SNSEvent = {
      Records: [
        {
          EventVersion: '1.0',
          EventSubscriptionArn: 'arn',
          EventSource: 'aws:sns',
          Sns: {
            SignatureVersion: '1',
            Timestamp: '',
            Signature: '',
            SigningCertUrl: '',
            MessageId: 'id',
            Message: 'not-valid-json',
            MessageAttributes: {},
            Type: 'Notification',
            UnsubscribeUrl: '',
            TopicArn: '',
            Subject: '',
          },
        },
      ],
    };

    // Must not throw — errors are caught per-record
    await expect(handler(event)).resolves.toBeUndefined();
  });

  it('should_notCreateIssue_when_alarmIsInInsufficientDataState', async () => {
    const event = makeSnsEvent([makeAlarmMessage({ NewStateValue: 'INSUFFICIENT_DATA' })]);
    await handler(event);

    expect(mockIssuesCreate).not.toHaveBeenCalled();
    expect(mockIssuesUpdate).not.toHaveBeenCalled();
  });
});
