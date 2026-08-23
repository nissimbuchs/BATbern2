import { App, Stack } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as logs from 'aws-cdk-lib/aws-logs';
import { AlbAlarms } from '../../lib/constructs/alb-alarms';
import { EcsServiceAlarms } from '../../lib/constructs/ecs-service-alarms';

/**
 * Repo-wide invariant on AWS::Logs::MetricFilter.
 *
 * WHY THIS FILE EXISTS. `AlbAlarms` shipped two metric filters carrying both `dimensions` and
 * `defaultValue`. Every local check passed — `tsc --noEmit`, the CDK `Template.fromStack`
 * assertions, and a full `cdk synth` — and CloudFormation then rejected both resources at
 * deploy time, rolling `BATbern-staging-ApiGatewayService` back to UPDATE_ROLLBACK_COMPLETE:
 *
 *   Invalid metric transformation: dimensions and default value are mutually exclusive properties
 *
 * That is the same lesson coding-standards.md already records for Lambda handlers, on a
 * different resource: a CloudFormation template assertion proves the DECLARATION is well
 * formed, never that the service will accept it. Synth cannot catch a service-side semantic
 * constraint because synth never talks to the service.
 *
 * Probed against the live CloudWatch Logs API on 2026-08-23 with a throwaway log group:
 *
 *   metricValue + defaultValue                 -> accepted
 *   metricValue + defaultValue + dimensions    -> "dimensions and default value are mutually
 *                                                  exclusive properties"
 *   metricValue + dimensions                   -> "The specified filter pattern does not
 *                                                  support dimensions"
 *
 * The second error is the one that decides this. Dimensions need a STRUCTURED filter pattern
 * (JSON, or space-delimited with named fields). Every pattern in this repository is built with
 * `FilterPattern.allTerms(...)`, i.e. literal text matching, and has to be: the api-gateway
 * log group receives each event twice, once as LogstashEncoder JSON and once as a plain-text
 * PatternLayout line, so a JSON pattern would see only half the events.
 *
 * Hence the flat rule below. It is stricter than the API — a structured pattern could legally
 * carry dimensions — and deliberately so: nothing here uses one, and a test that encodes the
 * simple rule catches the mistake, where a test that faithfully models the API's conditional
 * would need to parse filter patterns to decide which branch applies.
 */
describe('AWS::Logs::MetricFilter validity (regression guard)', () => {
  const templatesWithMetricFilters = (): Array<{ name: string; template: Template }> => {
    const results: Array<{ name: string; template: Template }> = [];

    {
      const app = new App();
      const stack = new Stack(app, 'AlbAlarmsStack', {
        env: { account: '123456789012', region: 'eu-central-1' },
      });
      new AlbAlarms(stack, 'AlbAlarms', {
        environment: 'staging',
        loadBalancerFullName: 'app/BATber-Servi-XlmJ3ScSHcvA/1234567890abcdef',
        targetGroupFullName: 'targetgroup/BATbe-Targe-ABCDEF/abcdef1234567890',
        alarmTopic: new sns.Topic(stack, 'Topic'),
        apiGatewayLogGroup: new logs.LogGroup(stack, 'GatewayLogGroup', {
          logGroupName: '/aws/ecs/BATbern-staging/api-gateway',
        }),
      });
      results.push({ name: 'AlbAlarms', template: Template.fromStack(stack) });
    }

    {
      // containerInsightsEnabled is what creates the OOM filter. No stack passes it today
      // (cluster-stack.ts has Insights DISABLED), so this branch is dead in production and
      // its invalid shape would have surfaced only on the first deploy after someone turned
      // Insights back on. Exercised here precisely because nothing else exercises it.
      const app = new App();
      const stack = new Stack(app, 'EcsAlarmsStack', {
        env: { account: '123456789012', region: 'eu-central-1' },
      });
      new EcsServiceAlarms(stack, 'ServiceAlarms', {
        environment: 'staging',
        clusterName: 'batbern-staging',
        serviceName: 'BATbern-staging-EventManagement-ServiceD69D759B-abc',
        serviceDisplayName: 'EventManagement',
        alarmTopic: new sns.Topic(stack, 'Topic'),
        containerInsightsEnabled: true,
      });
      results.push({ name: 'EcsServiceAlarms', template: Template.fromStack(stack) });
    }

    return results;
  };

  type Transformation = { MetricName?: string; Dimensions?: unknown; DefaultValue?: unknown };

  const allTransformations = () =>
    templatesWithMetricFilters().flatMap(({ name, template }) =>
      Object.entries(template.findResources('AWS::Logs::MetricFilter')).flatMap(
        ([logicalId, resource]) =>
          ((resource.Properties?.MetricTransformations ?? []) as Transformation[]).map((t) => ({
            construct: name,
            logicalId,
            transformation: t,
          }))
      )
    );

  test('should_findMetricFiltersToCheck_when_constructsSynthesised', () => {
    // Guards the guard. If the constructs stop producing metric filters this suite would pass
    // vacuously, which is how a test quietly stops testing anything.
    expect(allTransformations().length).toBeGreaterThanOrEqual(3);
  });

  test('should_neverCombineDimensionsWithDefaultValue_when_metricFilterSynthesised', () => {
    // The exact rejection that rolled back BATbern-staging-ApiGatewayService.
    const offenders = allTransformations()
      .filter((t) => t.transformation.Dimensions && t.transformation.DefaultValue !== undefined)
      .map((t) => `${t.construct}/${t.logicalId} (${t.transformation.MetricName})`);

    expect(offenders).toEqual([]);
  });

  test('should_neverSetDimensions_when_metricFilterUsesLiteralTermPattern', () => {
    // Broader and the one that actually matters: dimensions require a structured filter
    // pattern, and every pattern here is literal terms. See the file header.
    const offenders = allTransformations()
      .filter((t) => t.transformation.Dimensions)
      .map((t) => `${t.construct}/${t.logicalId} (${t.transformation.MetricName})`);

    expect(offenders).toEqual([]);
  });

  test('should_publishAZeroWhenNothingMatches_when_metricFilterFeedsARatioAlarm', () => {
    // defaultValue is what keeps the metric continuous. Without it a healthy system publishes
    // a metric with gaps, the ratio produces no datapoint, and the alarm becomes
    // indistinguishable from one watching a metric nobody publishes (#970).
    const gateway = allTransformations().filter((t) => t.construct === 'AlbAlarms');

    expect(gateway.length).toBe(2);
    for (const t of gateway) {
      expect(t.transformation.DefaultValue).toBe(0);
    }
  });

  test('should_keepAlarmMetricsUndimensioned_when_theyReadAMetricFilterMetric', () => {
    // The other half of the same contract: an alarm that queries a dimensioned metric while
    // the filter publishes an undimensioned one finds no data and stays permanently green.
    const [{ template }] = templatesWithMetricFilters();
    const alarms = Object.values(template.findResources('AWS::CloudWatch::Alarm'));

    const gatewayMetricRefs = alarms
      .flatMap((a) => (a.Properties?.Metrics ?? []) as Array<Record<string, any>>)
      .map((m) => m.MetricStat?.Metric)
      .filter((m) => m?.Namespace === 'BATbern/Gateway');

    expect(gatewayMetricRefs.length).toBe(2);
    for (const metric of gatewayMetricRefs) {
      expect(metric.Dimensions).toBeUndefined();
    }
  });
});
