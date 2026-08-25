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
const mockCwSend = jest.fn<(...args: any[]) => Promise<any>>();
const mockLogsSend = jest.fn<(...args: any[]) => Promise<any>>();
const mockIssuesCreate = jest.fn<(...args: any[]) => Promise<any>>();
const mockIssuesUpdate = jest.fn<(...args: any[]) => Promise<any>>();
const mockIssuesCreateComment = jest.fn<(...args: any[]) => Promise<any>>();
const mockSearchIssues = jest.fn<(...args: any[]) => Promise<any>>();

jest.mock('@aws-sdk/client-ssm', () => ({
  SSMClient: jest.fn().mockImplementation(() => ({ send: mockSsmSend })),
  GetParameterCommand: jest.fn().mockImplementation((input) => input),
}));

jest.mock('@aws-sdk/client-cloudwatch', () => ({
  CloudWatchClient: jest.fn().mockImplementation(() => ({ send: mockCwSend })),
  DescribeAlarmsCommand: jest.fn().mockImplementation((input) => ({ __type: 'describe', input })),
  GetMetricDataCommand: jest.fn().mockImplementation((input) => ({ __type: 'getdata', input })),
}));

jest.mock('@aws-sdk/client-cloudwatch-logs', () => ({
  CloudWatchLogsClient: jest.fn().mockImplementation(() => ({ send: mockLogsSend })),
  FilterLogEventsCommand: jest.fn().mockImplementation((input) => ({ __type: 'filter', input })),
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

import { handler, redactPath } from '../../../lambda/github-issues-integration/index';

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function makeAlarmMessage(
  overrides: Partial<{
    AlarmName: string;
    NewStateValue: 'ALARM' | 'OK' | 'INSUFFICIENT_DATA';
    AlarmDescription: string;
  }> = {}
) {
  return {
    AlarmName: 'batbern-staging-EventManagement-High-Memory',
    AlarmDescription: 'Memory usage is high',
    NewStateValue: 'ALARM',
    NewStateReason: 'Threshold Crossed: memory > 80%',
    StateChangeTime: '2026-05-16T10:00:00.000+0000',
    // #987: CloudWatch puts the human DISPLAY NAME in Region, not the region code. This
    // fixture said 'eu-central-1' for a year, which is why three broken console links
    // shipped and stayed — the test could not see a bug the fixture had defined away.
    // Verified against a real payload in the batbern-staging-github-issues log group.
    Region: 'EU (Frankfurt)',
    AlarmArn: 'arn:aws:cloudwatch:eu-central-1:188701360969:alarm:test-alarm',
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
    delete process.env.CLAUDE_TRIAGE_ENABLED;

    // SSM returns a token
    mockSsmSend.mockResolvedValue({ Parameter: { Value: 'ghp_test_token' } });
    // No existing issue by default
    mockSearchIssues.mockResolvedValue({ data: { items: [] } });
    mockIssuesCreate.mockResolvedValue({ data: { number: 42 } });
    mockIssuesUpdate.mockResolvedValue({ data: { number: 42 } });
    mockIssuesCreateComment.mockResolvedValue({ data: { id: 1 } });
    mockLogsSend.mockResolvedValue({ events: [] });

    // #996: metric context. Default is a metric-math alarm (api-4xx-ratio's shape), because
    // that is the one whose numbers actually need explaining.
    mockCwSend.mockImplementation((cmd: any) => {
      if (cmd.__type === 'describe') {
        return Promise.resolve({
          MetricAlarms: [
            {
              AlarmName: cmd.input.AlarmNames[0],
              Period: 300,
              Metrics: [{ Id: 'ratio', Expression: 'errors / requests * 100', ReturnData: true }],
            },
          ],
        });
      }
      return Promise.resolve({
        MetricDataResults: [
          {
            Id: 'ratio',
            Label: 'Served API 4xx %',
            Timestamps: ['2026-08-24T03:40:00Z', '2026-08-24T03:45:00Z'],
            Values: [88.4321, 81.2999],
          },
        ],
      });
    });
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

  describe('console links (#987)', () => {
    const bodyOf = () => (mockIssuesCreate.mock.calls[0][0] as { body: string }).body;

    it('should_buildLinksWithTheRegionCode_when_cloudWatchSendsTheDisplayName', async () => {
      // The defect: every link read `region=EU (Frankfurt)` — display name, with a space —
      // so none of them resolved. AWS's own notification mail gets this right, which made
      // the mail usable and the issue not, backwards given the issue is the workflow.
      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const body = bodyOf();
      expect(body).toContain('region=eu-central-1');
      expect(body).not.toContain('region=EU (Frankfurt)');
      expect(body).not.toContain('region=EU%20(Frankfurt)');
    });

    it('should_stillShowTheHumanRegionName_when_bodyIsRendered', async () => {
      // The display name is the right thing for a human to read; it is only wrong in a URL.
      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      expect(bodyOf()).toContain('**Region:** EU (Frankfurt)');
    });

    it('should_deriveRegionFromTheArn_when_regionFieldIsMissingEntirely', async () => {
      // The ARN is the authority and is always present. Region is a convenience field.
      const message = makeAlarmMessage({ NewStateValue: 'ALARM' }) as Record<string, unknown>;
      delete message.Region;
      await handler(makeSnsEvent([message]));

      expect(bodyOf()).toContain('region=eu-central-1');
    });

    it('should_useTheRegionCodeInEveryLink_when_bodyIsRendered', async () => {
      // Catches the whole class rather than the three known instances: every region= param
      // anywhere in the body must be a region CODE. Asserting on the rendered URL alone is
      // not enough — a naive `https?://[^\s)]*` match stops at the space, so a broken link
      // looks well-formed to the matcher. Read the parameter values instead.
      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const values = [...bodyOf().matchAll(/region=([^&#)\s]*)/g)].map((m) => m[1]);
      expect(values.length).toBeGreaterThanOrEqual(3);
      expect([...new Set(values)]).toEqual(['eu-central-1']);
    });
  });

  describe('metric context (#996)', () => {
    const bodyOf = () => (mockIssuesCreate.mock.calls[0][0] as { body: string }).body;

    it('should_embedRecentDatapoints_when_issueIsCreated', async () => {
      // On the alarm's first real firing the triage agent had no tools and no AWS credentials,
      // so it could not query anything and said so. The numbers that explained #994 existed only
      // in CloudWatch. Putting them in the body means the triage has real data regardless.
      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const body = bodyOf();
      expect(body).toContain('Metric context');
      expect(body).toContain('Served API 4xx %');
      expect(body).toContain('88.43');
      expect(body).toContain('03:40');
    });

    it('should_handleMetricMathAlarms_when_fetchingContext', async () => {
      // api-4xx-ratio is metric math, so DescribeAlarms returns Metrics[] rather than a single
      // MetricName. Those must be passed to GetMetricData verbatim.
      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const getData = mockCwSend.mock.calls
        .map((c) => c[0] as any)
        .find((c) => c.__type === 'getdata');
      expect(getData.input.MetricDataQueries).toEqual([
        { Id: 'ratio', Expression: 'errors / requests * 100', ReturnData: true },
      ]);
    });

    it('should_stillFileTheIssue_when_cloudWatchFails', async () => {
      // Fail-open, and this is the property that matters most: an enrichment that can stop an
      // incident from being recorded is worse than no enrichment.
      mockCwSend.mockRejectedValue(new Error('AccessDenied'));

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      expect(mockIssuesCreate).toHaveBeenCalledTimes(1);
      const body = bodyOf();
      expect(body).toContain('## CloudWatch Alarm Details');
      expect(body).toContain('@claude');
      expect(body).not.toContain('Metric context');
    });

    it('should_saySoExplicitly_when_thereAreNoDatapoints', async () => {
      // Silence and "no data" are different facts, and the difference matters to whoever reads
      // the issue — an empty metric can itself be the finding.
      mockCwSend.mockImplementation((cmd: any) =>
        cmd.__type === 'describe'
          ? Promise.resolve({
              MetricAlarms: [
                { AlarmName: 'a', Period: 300, Namespace: 'N', MetricName: 'M', Statistic: 'Sum' },
              ],
            })
          : Promise.resolve({ MetricDataResults: [{ Id: 'm1', Timestamps: [], Values: [] }] })
      );

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      expect(bodyOf()).toContain('No datapoints in the 45 minutes');
    });

    it('should_notFetchContext_when_alarmMerelyRecovers', async () => {
      // An OK transition closes the issue; there is no body to enrich, so no reason to spend a
      // CloudWatch call on it.
      mockSearchIssues.mockResolvedValue({ data: { items: [{ number: 42, state: 'open' }] } });

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'OK' })]));

      expect(mockCwSend).not.toHaveBeenCalled();
    });
  });

  describe('log context redaction (#1002)', () => {
    const bodyOf = () => (mockIssuesCreate.mock.calls[0][0] as { body: string }).body;

    // These issues are filed into a PUBLIC repository. Anything redactPath fails to strip is
    // published to the internet, so this is a disclosure boundary, not formatting.
    it.each([
      ['/api/v1/users/john.doe', '/api/v1/users/{id}'],
      ['/api/v1/users/nissim.buchs@elca.ch', '/api/v1/users/{email}'],
      [
        '/api/v1/events/BATbern57/speakers/3f2504e0-4f89-11d3-9a0c-0305e82c3301',
        '/api/v1/events/BATbern57/speakers/{uuid}',
      ],
      ['/api/v1/registrations/12345', '/api/v1/registrations/{n}'],
      ['/api/v1/events/BATbern142/agenda-config', '/api/v1/events/BATbern142/agenda-config'],
      ['/api/v1/companies/GoogleZH', '/api/v1/companies/{id}'],
    ])('should_redactIdentifiers_when_pathIs_%s', (input, expected) => {
      expect(redactPath(input)).toBe(expected);
    });

    it('should_keepTheEventCode_when_redacting', () => {
      // BATbernNN is public information and the single most useful thing to see in a failing
      // route, so it deliberately survives.
      expect(redactPath('/api/v1/events/BATbern57')).toContain('BATbern57');
    });

    it('should_aggregateFailingRoutes_when_logsContainErrors', async () => {
      mockLogsSend.mockResolvedValue({
        events: [
          {
            message:
              'GATEWAY_API_REQUEST status=401 clientError=true method=PUT path=/api/v1/events/BATbern142/agenda-config',
          },
          {
            message:
              'GATEWAY_API_REQUEST status=401 clientError=true method=PUT path=/api/v1/events/BATbern142/agenda-config',
          },
          {
            message:
              'GATEWAY_API_REQUEST status=200 clientError=false method=GET path=/api/v1/users/john.doe',
          },
        ],
      });

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const body = bodyOf();
      expect(body).toContain('Log context');
      expect(body).toContain('| 401 | 2 |');
      expect(body).toContain('PUT /api/v1/events/BATbern142/agenda-config');
      // the 200 carried a username; it must not have been published
      expect(body).not.toContain('john.doe');
    });

    it('should_neverPublishARawIdentifier_when_bodyIsRendered', async () => {
      mockLogsSend.mockResolvedValue({
        events: [
          {
            message:
              'GATEWAY_API_REQUEST status=403 clientError=true method=GET path=/api/v1/users/alice.smith@example.com',
          },
          {
            message:
              'GATEWAY_API_REQUEST status=404 clientError=true method=GET path=/api/v1/sessions/3f2504e0-4f89-11d3-9a0c-0305e82c3301',
          },
        ],
      });

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const body = bodyOf();
      expect(body).not.toContain('alice.smith');
      expect(body).not.toContain('example.com');
      expect(body).not.toContain('3f2504e0');
      expect(body).toContain('{email}');
      expect(body).toContain('{uuid}');
    });

    it('should_stillFileTheIssue_when_logsAreUnavailable', async () => {
      // Fail-open, including when the lazily-imported SDK client or the permission is missing.
      mockLogsSend.mockRejectedValue(new Error('AccessDeniedException'));

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      expect(mockIssuesCreate).toHaveBeenCalledTimes(1);
      expect(bodyOf()).toContain('## CloudWatch Alarm Details');
      expect(bodyOf()).not.toContain('Log context —');
    });

    it('should_pickTheServiceLogGroup_when_alarmNamesAService', async () => {
      await handler(
        makeSnsEvent([
          makeAlarmMessage({
            NewStateValue: 'ALARM',
            AlarmName: 'batbern-staging-EventManagement-High-Memory',
          }),
        ])
      );

      const filter = mockLogsSend.mock.calls
        .map((c) => c[0] as any)
        .find((c) => c.__type === 'filter');
      expect(filter.input.logGroupName).toBe('/aws/ecs/BATbern-staging/event-management');
    });
  });

  describe('@claude triage handoff', () => {
    const bodyOf = () => (mockIssuesCreate.mock.calls[0][0] as { body: string }).body;

    it('should_mentionClaude_when_issueIsCreated', async () => {
      // .github/workflows/claude.yml fires on `issues: opened` only when the body or title
      // contains '@claude'. Without the mention the workflow is inert and the alarm sits
      // there waiting for a human.
      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      expect(bodyOf()).toContain('@claude');
    });

    it('should_giveClaudeTheConstraints_when_issueIsCreated', async () => {
      // The mention alone would hand an agent a production incident with no boundaries. The
      // body is the prompt for an `issues: opened` run, so the boundaries have to live in it.
      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const body = bodyOf();
      expect(body).toContain('Do not open a pull request');
      expect(body).toContain('read-only');
    });

    it('should_notMentionClaude_when_alarmReTriggersOnAnOpenIssue', async () => {
      // This is the flap guard. An alarm that oscillates would otherwise start one agent run
      // per cycle: batbern-staging-alb-4xx managed six cycles in three days, two of them 60
      // seconds long. Re-trigger updates the issue and must stay silent.
      mockSearchIssues.mockResolvedValue({
        data: { items: [{ number: 42, state: 'open', title: 'existing' }] },
      });

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      expect(mockIssuesCreate).not.toHaveBeenCalled();
      const comments = mockIssuesCreateComment.mock.calls.map(
        (c) => (c[0] as { body: string }).body
      );
      expect(comments.length).toBeGreaterThan(0);
      for (const comment of comments) {
        expect(comment).not.toContain('@claude');
      }
    });

    it('should_notMentionClaude_when_alarmRecoversAndIssueIsClosed', async () => {
      mockSearchIssues.mockResolvedValue({
        data: { items: [{ number: 42, state: 'open', title: 'existing' }] },
      });

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'OK' })]));

      const comments = mockIssuesCreateComment.mock.calls.map(
        (c) => (c[0] as { body: string }).body
      );
      for (const comment of comments) {
        expect(comment).not.toContain('@claude');
      }
    });

    it('should_omitTheMention_when_killSwitchIsOff', async () => {
      // Runtime off switch, same convention as FEATURES_SSO_ENABLED. A Lambda env var change
      // takes effect immediately, so an agent storm can be stopped without a code deploy.
      process.env.CLAUDE_TRIAGE_ENABLED = 'false';

      await handler(makeSnsEvent([makeAlarmMessage({ NewStateValue: 'ALARM' })]));

      const body = bodyOf();
      expect(body).not.toContain('@claude');
      // Everything a human needs must still be there.
      expect(body).toContain('## CloudWatch Alarm Details');
      expect(body).toContain('region=eu-central-1');
    });
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
    const event = makeSnsEvent([
      makeAlarmMessage({
        AlarmName: 'batbern-staging-api-availability',
        NewStateValue: 'ALARM',
      }),
    ]);
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
