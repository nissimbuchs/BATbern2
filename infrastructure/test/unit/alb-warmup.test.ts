import { App, Stack } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import { ApiGatewayServiceStack } from '../../lib/stacks/api-gateway-service-stack';
import { prodConfig } from '../../lib/config/prod-config';

/**
 * Issue #982: every ECS task replacement produced ~10-15 minutes of multi-second response
 * times — roughly 800 real requests per replacement against a 2-25 ms steady-state p95.
 * It had presumably always happened and only became visible on 2026-08-19, when #977 added
 * the first latency alarm that actually evaluated a datapoint.
 *
 * Measured over two replacements that evening, ALB TargetResponseTime p95 per 5-min window:
 *
 *     requests   p95            requests   p95
 *           25   3.7081 s             35   4.7058 s
 *          116   1.9862 s            220   1.1722 s
 *          256   0.8424 s            533   0.4328 s
 *          386   0.5252 s
 *           45   0.0057 s
 *
 * Volume RAMPS while latency DECAYS. That is a JVM warming up under load, not a slow
 * dependency — and it correlates directly with task registration (18:48 target registered,
 * 19:00 p95 = 4.71 s over 35 requests, 19:10 p95 = 0.43 s over 533).
 *
 * A target enters service the moment `/actuator/health` passes, and passing a liveness probe
 * is not the same as being warm, so the ALB sends it a full round-robin share immediately.
 * ALB slow-start is the standard mitigation and needs no application change: it ramps a newly
 * registered target's share of traffic linearly over the configured window instead of
 * switching it on at full rate.
 *
 * This matters beyond deploy windows. Tasks are replaced on every deploy — and on this repo a
 * PR to `develop` is a deploy — plus whenever Fargate Spot reclaims capacity, which is 70% of
 * the capacity mix and happens unannounced during ordinary traffic.
 *
 * `alb-latency-p95` now needs 5 breaching periods out of 6 specifically so this
 * self-resolving condition does not page. That was right for the alarm and wrong to leave as
 * the whole answer: the latency is real and user-visible. These tests make the mitigation
 * explicit so it cannot be dropped silently.
 */
describe('ALB target warmup (#982)', () => {
  const build = () => {
    // createContainerImage() throws in CI when IMAGE_TAG is unset, and takes the
    // ContainerImage.fromAsset() path otherwise — which would try to build the api-gateway
    // Dockerfile. Setting a tag pins it to the cheap ECR-reference path in both places.
    const previousTag = process.env.IMAGE_TAG;
    process.env.IMAGE_TAG = 'test-tag';
    try {
      const app = new App();
      const env = { account: '123456789012', region: 'eu-central-1' };

      const support = new Stack(app, 'Support', { env });
      const vpc = new ec2.Vpc(support, 'Vpc', { maxAzs: 2 });
      const cluster = new ecs.Cluster(support, 'Cluster', { vpc });
      const dbSg = new ec2.SecurityGroup(support, 'DbSg', { vpc });
      const userPool = new cognito.UserPool(support, 'Pool');
      const userPoolClient = userPool.addClient('Client');

      const stack = new ApiGatewayServiceStack(app, 'TestApiGatewayService', {
        config: prodConfig,
        env,
        cluster,
        vpc,
        databaseSecurityGroup: dbSg,
        userPool,
        userPoolClient,
      });

      return Template.fromStack(stack);
    } finally {
      if (previousTag === undefined) {
        delete process.env.IMAGE_TAG;
      } else {
        process.env.IMAGE_TAG = previousTag;
      }
    }
  };

  test('should_enableSlowStartOnTheTargetGroup_when_apiGatewayServiceCreated', () => {
    build().hasResourceProperties('AWS::ElasticLoadBalancingV2::TargetGroup', {
      TargetGroupAttributes: Match.arrayWith([
        { Key: 'slow_start.duration_seconds', Value: '300' },
      ]),
    });
  });

  test('should_keepSlowStartWithinTheAwsAcceptedRange_when_configured', () => {
    // AWS accepts 30-900 s and rejects anything else at deploy time, not at synth time — so
    // a bad value here would fail the CDK deploy of the gateway stack in production rather
    // than the build. Assert the bound in the build instead.
    const attrs = Object.values(
      build().findResources('AWS::ElasticLoadBalancingV2::TargetGroup')
    ).flatMap(
      (tg) => (tg.Properties?.TargetGroupAttributes ?? []) as Array<{ Key: string; Value: string }>
    );

    const slowStart = attrs.find((a) => a.Key === 'slow_start.duration_seconds');
    expect(slowStart).toBeDefined();

    const seconds = Number(slowStart!.Value);
    expect(Number.isInteger(seconds)).toBe(true);
    expect(seconds).toBeGreaterThanOrEqual(30);
    expect(seconds).toBeLessThanOrEqual(900);
  });

  test('should_keepRoundRobinRouting_when_slowStartEnabled', () => {
    // AWS refuses slow start on a target group using least_outstanding_requests. The default
    // is round_robin, so the invariant is that nothing has switched the algorithm — if
    // someone does, slow start stops working and this test says why rather than leaving a
    // silently inert attribute.
    const attrs = Object.values(
      build().findResources('AWS::ElasticLoadBalancingV2::TargetGroup')
    ).flatMap(
      (tg) => (tg.Properties?.TargetGroupAttributes ?? []) as Array<{ Key: string; Value: string }>
    );

    const algorithm = attrs.find((a) => a.Key === 'load_balancing.algorithm.type');
    // Unset means round_robin, which is what slow start requires.
    expect(algorithm?.Value ?? 'round_robin').toBe('round_robin');
  });
});
