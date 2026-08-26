import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { ClusterStack } from '../../lib/stacks/cluster-stack';
import { NetworkStack } from '../../lib/stacks/network-stack';
import { stagingConfig } from '../../lib/config/staging-config';

/**
 * Pins the cost decisions from `docs/plans/aws-cost-reduction.md` (tiers 1 and 2).
 *
 * Spend was $309.73/month against a $150 target, and June (the conference month) cost
 * $1.07 LESS than July, so there is no usage component to trim — every dollar is
 * always-on infrastructure. These assertions exist so a later change cannot quietly
 * restore the spend without someone deciding to.
 *
 * The subtlety worth pinning: `desiredCount` only sets the count at service creation.
 * Application Auto Scaling governs steady state, so `minCapacity` is what actually
 * decides how many tasks run a month from now. EventManagement already demonstrated
 * this — it was created with `desiredCount: 2` and has been running 1 task for months,
 * because its `minCapacity: 1` scaled it down. Setting one without the other achieves
 * nothing.
 */
describe('Cost footprint (docs/plans/aws-cost-reduction.md)', () => {
  const buildCluster = () => {
    const app = new App();
    const env = { account: '123456789012', region: 'eu-central-1' };
    const network = new NetworkStack(app, 'TestNetwork', { config: stagingConfig, env });
    const cluster = new ClusterStack(app, 'TestCluster', {
      config: stagingConfig,
      vpc: network.vpc,
      env,
    });
    return Template.fromStack(cluster);
  };

  describe('Tier 1 — Container Insights', () => {
    test('should_disableContainerInsights_when_clusterCreated', () => {
      // $46.07/month of the $51.68 CloudWatch bill was Container Insights publishing 163
      // custom metrics at $0.30 each — 15% of the entire AWS bill.
      //
      // It was enabled deliberately, with a comment claiming the RunningTaskCount and
      // DesiredTaskCount metrics were "essential for diagnosing stuck deployments". That
      // intent was never wired up: no alarm in the repo references either metric, no
      // deployed alarm uses the ECS/ContainerInsights namespace, and the one code path
      // that does consume it — the OOM-kill alarm — is gated on a
      // `containerInsightsEnabled` prop that no stack ever passes.
      //
      // The console dashboards it powers remain a real if unmeasured benefit; that trade
      // is recorded in the plan.
      buildCluster().hasResourceProperties('AWS::ECS::Cluster', {
        ClusterSettings: Match.arrayWith([{ Name: 'containerInsights', Value: 'disabled' }]),
      });
    });
  });

  describe('Tier 1 — the empty service', () => {
    test('should_documentAttendeeExperienceAsScaledToZero_when_configured', () => {
      // attendee-experience-service/src/main/java contains exactly one file,
      // AttendeeExperienceApplication.java. No controllers, no endpoints. It ran 2 Fargate
      // tasks 24/7 for ~$17/month.
      //
      // Scaled to zero rather than deleted: Epic 7 will need this service, and a
      // desiredCount change is reversible in one deploy where a stack deletion is not.
      //
      // Asserted through the config module rather than by synthesising the stack, because
      // AttendeeExperienceStack requires a Cognito user pool and a database secret that
      // are not worth constructing to check two numbers.
      const source = require('fs').readFileSync(
        `${__dirname}/../../lib/stacks/attendee-experience-stack.ts`,
        'utf8'
      );
      expect(source).toMatch(/desiredCount:\s*0/);
      expect(source).toMatch(/disableAutoScaling:\s*true/);
    });
  });

  describe('Tier 2 — task counts', () => {
    const singleTaskServices = [
      'partner-coordination-stack.ts',
      'speaker-coordination-stack.ts',
      'company-management-stack.ts',
    ];

    test.each(singleTaskServices)(
      'should_runOneSteadyStateTask_when_%s_configured',
      (stackFile) => {
        // minCapacity is the control that matters — see the note at the top of this file.
        // 2 tasks were never real high availability here: the VPC is single-AZ, and
        // services run 70% on Fargate Spot, which already produces 4-5 minute silent
        // replacements. ApiGatewayService deliberately stays at 2 — it is the public
        // entry point and the one place a gap is visible to visitors.
        const source = require('fs').readFileSync(
          `${__dirname}/../../lib/stacks/${stackFile}`,
          'utf8'
        );
        expect(source).toMatch(/minCapacity:\s*1/);
        // desiredCount too, or every deploy creates 2 tasks and the scaler pulls one back
        // in — converges, but churns tasks (and warms a cold JVM, see #982) for nothing.
        expect(source).toMatch(/desiredCount:\s*1/);
      }
    );

    test('should_keepApiGatewayAtTwoTasks_when_configured', () => {
      // Explicitly NOT reduced. Asserted so a future cost sweep has to argue with this
      // comment rather than silently take the public entry point down to one task.
      const source = require('fs').readFileSync(
        `${__dirname}/../../lib/stacks/api-gateway-service-stack.ts`,
        'utf8'
      );
      expect(source).toMatch(/desiredCount:\s*isProd\s*\?\s*2\s*:\s*1/);
    });
  });
});
