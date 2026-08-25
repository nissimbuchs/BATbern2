import { CloudWatchClient, DescribeAlarmsCommand, MetricAlarm } from '@aws-sdk/client-cloudwatch';
import {
  SNSClient,
  GetTopicAttributesCommand,
  ListSubscriptionsByTopicCommand,
} from '@aws-sdk/client-sns';

/**
 * E2E Test for Incident Response wiring.
 *
 * Requires real AWS resources; skipped unless TEST_E2E=true (deployed environments only).
 *
 * #1003 (2026-08-25) — scope decision. This file used to carry four `describe.skip` blocks
 * asserting infrastructure that was specified and never built:
 *
 *   AC13 PagerDuty          -> DROPPED. Single operator; a pager that wakes the only person who
 *                              already gets the email adds nothing.
 *   AC14 Runbook automation -> REPLACED by operator runbooks (docs/operations/runbooks.md),
 *                              not auto-remediation Lambdas. A bot that restarts production
 *                              services in response to a metric is a bigger risk than the
 *                              incidents it handles. Guarded by
 *                              test/unit/incident-response-docs.test.ts.
 *   AC15 Post-mortem        -> REPLACED by docs/operations/post-mortem-template.md plus the
 *                              Post-mortem issue form. Same unit guard.
 *   AC16 StatusPage         -> DROPPED. No between-event audience watching a status page.
 *
 * Those blocks were also unfalsifiable: three `expect(true).toBe(true)` placeholders and an
 * `expect(requiredSections.length).toBe(6)` against an array literal declared three lines above.
 * Deleting them removes scaffolding that read as coverage.
 *
 * What remains below asserts the delivery path that actually exists, and is written so it FAILS
 * when that path breaks — the previous version queried the prefix `batbern-{env}-critical-`,
 * which matches zero alarms in the account, so its `if` body never ran and it passed
 * unconditionally.
 */
const describeE2E = process.env.TEST_E2E === 'true' ? describe : describe.skip;

describeE2E('Incident Response E2E Tests', () => {
  let cloudwatchClient: CloudWatchClient;
  let snsClient: SNSClient;
  const environment = process.env.TEST_ENVIRONMENT || 'dev';
  const alarmPrefix = `batbern-${environment}`;

  let alarms: MetricAlarm[];

  beforeAll(async () => {
    const region = process.env.AWS_REGION || 'eu-central-1';
    cloudwatchClient = new CloudWatchClient({ region });
    snsClient = new SNSClient({ region });

    alarms = [];
    let token: string | undefined;
    do {
      const page: any = await cloudwatchClient.send(
        new DescribeAlarmsCommand({ AlarmNamePrefix: `${alarmPrefix}-`, NextToken: token })
      );
      alarms.push(...(page.MetricAlarms ?? []));
      token = page.NextToken;
    } while (token);
  });

  describe('Alarm delivery path', () => {
    test('should_findAlarms_when_estateQueried', () => {
      // Guards every assertion below: an empty estate would make them all vacuously true.
      // That is exactly how the previous version of this file passed while proving nothing.
      expect(alarms.length).toBeGreaterThan(20);
    });

    test('should_haveAnAlarmAction_when_alarmDeclared', () => {
      const silent = alarms
        .filter((a) => (a.AlarmActions ?? []).length === 0)
        .map((a) => a.AlarmName);

      // #971: emails-rejected once fired into the void with no actions at all.
      expect(silent).toEqual([]);
    });

    test('should_haveActionsEnabled_when_alarmDeclared', () => {
      const disabled = alarms.filter((a) => a.ActionsEnabled !== true).map((a) => a.AlarmName);

      expect(disabled).toEqual([]);
    });

    test('should_routeToATopicWithSubscribers_when_alarmFires', async () => {
      const topics = [...new Set(alarms.flatMap((a) => a.AlarmActions ?? []))].filter((arn) =>
        arn.startsWith('arn:aws:sns:')
      );
      expect(topics.length).toBeGreaterThan(0);

      const unsubscribed: string[] = [];
      for (const arn of topics) {
        const subs = await snsClient.send(new ListSubscriptionsByTopicCommand({ TopicArn: arn }));
        if ((subs.Subscriptions ?? []).length === 0) {
          unsubscribed.push(arn);
        }
      }

      // #1005: batbern-user-sync-alarms-staging has zero subscriptions, so seven identity
      // alarms reach nobody. An alarm action that resolves to a silent topic is not delivery.
      expect(unsubscribed).toEqual([]);
    });

    test('should_reachTheTriageLambda_when_alarmPublishesToPrimaryTopic', async () => {
      const primary = alarms
        .flatMap((a) => a.AlarmActions ?? [])
        .find((arn) => arn.endsWith(`:${alarmPrefix}-alarms`));
      expect(primary).toBeDefined();

      const subs = await snsClient.send(new ListSubscriptionsByTopicCommand({ TopicArn: primary }));
      const protocols = (subs.Subscriptions ?? []).map((s) => s.Protocol);

      // The alarm -> SNS -> github-issues Lambda -> GitHub issue -> triage handoff.
      // Lambda subscriptions need no confirmation, which is why this path stays alive while
      // an unconfirmed email subscription silently expires.
      expect(protocols).toContain('lambda');
    });

    test('should_beReachable_when_primaryTopicInspected', async () => {
      const primary = alarms
        .flatMap((a) => a.AlarmActions ?? [])
        .find((arn) => arn.endsWith(`:${alarmPrefix}-alarms`))!;

      const attrs = await snsClient.send(new GetTopicAttributesCommand({ TopicArn: primary }));

      expect(attrs.Attributes).toBeDefined();
      expect(Number(attrs.Attributes!.SubscriptionsConfirmed)).toBeGreaterThan(0);
    });
  });

  describe('Alarm responsiveness', () => {
    // DELIBERATELY NOT ASSERTED: detection speed.
    //
    // The original AC asserted `EvaluationPeriods <= 3`. Measured against the estate, that is
    // wrong in both directions and cannot be repaired by picking a better number:
    //
    //   alb-latency-p95              5 of 6 periods = 25 min, ON PURPOSE (#982) so a deploy
    //                                warmup does not page
    //   Reconciliation-Orphaned-Users  24 h period — it watches a daily reconciliation job
    //   User-Sync-High-Drift, emails-rejected   1 h — slow-moving aggregates where the window
    //                                is the metric
    //
    // "How fast should this alarm detect" depends on what the metric measures, so any global
    // ceiling is either vacuous or needs a hand-maintained exception list. Tuning a threshold
    // until the estate goes green is how the unfalsifiable assertions removed from this file
    // were written in the first place. Per-alarm intent belongs in the CDK next to the alarm,
    // where alb-alarms.ts already documents it.

    test('should_closeTheIssue_when_alarmRecovers', () => {
      // #956: alarms carry an OK action so the GitHub issue closes on recovery. Without it an
      // operator has to close every issue by hand and the backlog fills with resolved incidents.
      const noOkAction = alarms
        .filter((a) => (a.OKActions ?? []).length === 0)
        .map((a) => a.AlarmName);

      expect(noOkAction).toEqual([]);
    });
  });
});
