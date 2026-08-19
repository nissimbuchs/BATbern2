import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { MonitoringStack } from '../../lib/stacks/monitoring-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';

/**
 * Alert configuration for the alarms MonitoringStack still owns, plus regression guards
 * for issue #970.
 *
 * This file used to assert that MonitoringStack contained availability / error / latency /
 * CPU / memory / disk alarms. It did — nine of them, and **not one had ever evaluated a
 * datapoint**, because each queried a resource that does not exist in this account. Every
 * assertion here passed against alarms that were permanently INSUFFICIENT_DATA, which is
 * precisely the problem: `hasResourceProperties` sees a name and a threshold, and is blind
 * to whether the metric resolves to anything at all.
 *
 * Those alarms now live next to the resources they watch and are covered by
 * `platform-alarms.test.ts`:
 *   - availability / 5xx / 4xx / latency / unhealthy targets → `AlbAlarms`
 *   - CPU and memory, per service                            → `EcsServiceAlarms`
 *
 * What remains here is what MonitoringStack can legitimately own (the RDS alarms, whose
 * instance identifier is deterministic) and — more usefully — a set of guards asserting the
 * *absence* of the specific mistakes that made the old alarms dead. A named-and-thresholded
 * alarm proves nothing; a correct dimension is the thing worth testing.
 */
describe('Alert Rules Configuration', () => {
  const templateFor = (config: typeof prodConfig) => {
    const app = new App();
    const stack = new MonitoringStack(app, 'TestMonitoringStack', {
      config,
      env: { account: '123456789012', region: 'eu-central-1' },
    });
    return Template.fromStack(stack);
  };

  const alarmsOf = (template: Template) =>
    Object.values(template.findResources('AWS::CloudWatch::Alarm')).map((r) => r.Properties ?? {});

  describe('#970 regression guards — dimensions that resolve to a real resource', () => {
    test('should_neverQueryApiGatewayNamespace_when_alarmsCreated', () => {
      // Four alarms queried AWS/ApiGateway with ApiName=batbern-{env}. Traffic is served
      // by an ALB; no REST API Gateway exists in this account. They never evaluated once.
      const offenders = alarmsOf(templateFor(prodConfig))
        .filter((p) => p.Namespace === 'AWS/ApiGateway')
        .map((p) => p.AlarmName);
      expect(offenders).toEqual([]);
    });

    test('should_alwaysPairClusterNameWithServiceName_when_ecsAlarmCreated', () => {
      // high-cpu and high-memory set only { ServiceName: 'batbern-{env}' } — which is the
      // CLUSTER name. AWS/ECS needs both dimensions or the metric resolves to nothing.
      const offenders = alarmsOf(templateFor(prodConfig))
        .filter((p) => p.Namespace === 'AWS/ECS')
        .filter((p) => {
          const names = (p.Dimensions ?? []).map((d: { Name: string }) => d.Name);
          return !(names.includes('ClusterName') && names.includes('ServiceName'));
        })
        .map((p) => p.AlarmName);
      expect(offenders).toEqual([]);
    });

    test('should_useDbInstanceIdentifier_when_rdsAlarmCreated', () => {
      // database-connections used DBClusterIdentifier. RDS here is a single
      // db.t4g.micro instance, never an Aurora cluster.
      const rdsAlarms = alarmsOf(templateFor(prodConfig)).filter((p) => p.Namespace === 'AWS/RDS');
      expect(rdsAlarms.length).toBeGreaterThan(0);

      const offenders = rdsAlarms
        .filter((p) => {
          const names = (p.Dimensions ?? []).map((d: { Name: string }) => d.Name);
          return !names.includes('DBInstanceIdentifier') || names.includes('DBClusterIdentifier');
        })
        .map((p) => p.AlarmName);
      expect(offenders).toEqual([]);
    });

    test('should_notQueryBillingOutsideUsEast1_when_alarmsCreated', () => {
      // budget-overage used AWS/Billing in eu-central-1. That namespace is only ever
      // published in us-east-1, so it could not have produced a datapoint anywhere else.
      const offenders = alarmsOf(templateFor(prodConfig))
        .filter((p) => p.Namespace === 'AWS/Billing')
        .map((p) => p.AlarmName);
      expect(offenders).toEqual([]);
    });

    test('should_notQueryEbsVolumeMetrics_when_alarmsCreated', () => {
      // high-disk used AWS/EBS VolumeUtilization — not a real EBS metric name, against
      // volumes that do not exist. Storage that can actually fill up is the RDS instance's.
      const offenders = alarmsOf(templateFor(prodConfig))
        .filter((p) => p.Namespace === 'AWS/EBS')
        .map((p) => p.AlarmName);
      expect(offenders).toEqual([]);
    });

    test('should_neverTreatMissingDataAsBreaching_when_alarmsCreated', () => {
      // high-availability used BREACHING, so with a metric that never produced data it sat
      // in ALARM continuously from 2025-10-04 — about ten months. A permanently red alarm
      // is worse than no alarm: it trains everyone to ignore the channel.
      const offenders = alarmsOf(templateFor(prodConfig))
        .filter((p) => p.TreatMissingData === 'breaching')
        .map((p) => p.AlarmName);
      expect(offenders).toEqual([]);
    });

    test('should_scopeResourceMetricsToAResource_when_alarmsCreated', () => {
      // A metric alarm with no dimensions aggregates across every resource in the
      // namespace — high-disk had none at all. This only applies to namespaces whose
      // metrics are per-resource: AWS/SES `Reputation.*` is account-level and correctly
      // carries no dimensions, as do the custom BATbern/* user-sync metrics.
      const RESOURCE_SCOPED = ['AWS/ECS', 'AWS/RDS', 'AWS/ApplicationELB', 'AWS/SQS', 'AWS/EBS'];
      const offenders = alarmsOf(templateFor(prodConfig))
        .filter((p) => RESOURCE_SCOPED.includes(p.Namespace))
        .filter((p) => (p.Dimensions ?? []).length === 0)
        .map((p) => p.AlarmName);
      expect(offenders).toEqual([]);
    });
  });

  describe('Database Alarms (RDS — owned by MonitoringStack)', () => {
    test('should_createDatabaseConnectionAlarm_when_rdsMonitoringEnabled', () => {
      templateFor(prodConfig).hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*database-connections'),
        Namespace: 'AWS/RDS',
        MetricName: 'DatabaseConnections',
        Dimensions: [{ Name: 'DBInstanceIdentifier', Value: 'batbern-production-postgres' }],
        ComparisonOperator: 'GreaterThanThreshold',
      });
    });

    test('should_alarmWhenFreeStorageFalls_when_rdsMonitoringEnabled', () => {
      // Replaces the bogus AWS/EBS disk alarm. FreeStorageSpace is in BYTES and — unlike a
      // utilization percentage — less is worse, hence LessThanThreshold.
      templateFor(prodConfig).hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*database-storage-low'),
        Namespace: 'AWS/RDS',
        MetricName: 'FreeStorageSpace',
        ComparisonOperator: 'LessThanThreshold',
        Threshold: 5 * 1024 * 1024 * 1024,
      });
    });

    test('should_createDatabaseCpuAlarm_when_rdsMonitoringEnabled', () => {
      templateFor(prodConfig).hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmName: Match.stringLikeRegexp('.*database-cpu'),
        Namespace: 'AWS/RDS',
        MetricName: 'CPUUtilization',
        Threshold: 80,
        ComparisonOperator: 'GreaterThanThreshold',
      });
    });
  });

  describe('Alarm Actions and Notifications', () => {
    test('should_attachSNSActions_when_productionAlarmsCreated', () => {
      templateFor(prodConfig).hasResourceProperties('AWS::CloudWatch::Alarm', {
        AlarmActions: Match.anyValue(),
      });
    });

    test('should_notCreateAlarmActions_when_devEnvironment', () => {
      templateFor(devConfig).resourceCountIs('AWS::SNS::Topic', 0);
    });

    test('should_configureOKActions_onEveryAlarm_when_alarmTopicExists', () => {
      // #956: the github-issues Lambda closes an alarm's issue on the OK transition. An
      // alarm with no OK action leaves its issue open forever (#484 sat open ~2 months).
      const missing = alarmsOf(templateFor(prodConfig))
        .filter((p) => !p.OKActions || p.OKActions.length === 0)
        .map((p) => p.AlarmName);
      expect(missing).toEqual([]);
    });
  });

  describe('Alarm Consistency and Standards', () => {
    test('should_useConsistentNaming_when_alarmsCreated', () => {
      const badlyNamed = alarmsOf(templateFor(prodConfig))
        .map((p) => p.AlarmName)
        .filter((name: string) => typeof name === 'string' && !name.startsWith('batbern-'));
      expect(badlyNamed).toEqual([]);
    });

    test('should_haveAlarmDescriptions_when_alarmsCreated', () => {
      const undescribed = alarmsOf(templateFor(prodConfig))
        .filter((p) => !p.AlarmDescription)
        .map((p) => p.AlarmName);
      expect(undescribed).toEqual([]);
    });

    test('should_configureEvaluationPeriods_when_alarmsCreated', () => {
      const missing = alarmsOf(templateFor(prodConfig))
        .filter((p) => p.EvaluationPeriods === undefined)
        .map((p) => p.AlarmName);
      expect(missing).toEqual([]);
    });

    test('should_configureTreatMissingData_when_alarmsCreated', () => {
      const missing = alarmsOf(templateFor(prodConfig))
        .filter((p) => p.TreatMissingData === undefined)
        .map((p) => p.AlarmName);
      expect(missing).toEqual([]);
    });
  });
});
