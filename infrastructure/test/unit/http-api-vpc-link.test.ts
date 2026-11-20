import { App, Stack } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as sns from 'aws-cdk-lib/aws-sns';
import { ApiGatewayServiceStack } from '../../lib/stacks/api-gateway-service-stack';
import { ApiGatewayStack, CLIENT_IP_HEADER } from '../../lib/stacks/api-gateway-stack';
import { prodConfig } from '../../lib/config/prod-config';

/**
 * #1152: the gateway never saw the visitor's address. The HTTP API called an internet-facing ALB
 * over its public URL, so the ALB recorded the HTTP API's egress node (measured 2026-10-28: 802
 * anonymous rate-limit hits keyed on 3.71.12x.x / 3.127.7x.x, both API_GATEWAY eu-central-1).
 *
 * The fix: the HTTP API reaches an INTERNAL ALB through a VPC link and overwrites
 * X-BATbern-Client-IP with $context.identity.sourceIp. Nothing on the internet can reach that ALB,
 * so the gateway can trust the header. The public ALB stays for this one deploy (the switch needs
 * no outage and rolls back by pointing the HTTP API at it again) and is locked in the next step.
 */
describe('HTTP API reaches the gateway through a VPC link (#1152)', () => {
  const build = () => {
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
      const alarmTopic = new sns.Topic(support, 'Alarms');

      const service = new ApiGatewayServiceStack(app, 'TestApiGatewayService', {
        config: prodConfig,
        env,
        cluster,
        vpc,
        databaseSecurityGroup: dbSg,
        userPool,
        userPoolClient,
        alarmTopic,
      });
      const api = new ApiGatewayStack(app, 'TestApiGateway', {
        config: prodConfig,
        env,
        userPool,
        userPoolClient,
        apiGatewayServiceUrl: service.apiGatewayUrl,
        apiGatewayListener: service.internalListener,
        vpcLink: service.vpcLink,
      });

      return { service: Template.fromStack(service), api: Template.fromStack(api) };
    } finally {
      if (previousTag === undefined) {
        delete process.env.IMAGE_TAG;
      } else {
        process.env.IMAGE_TAG = previousTag;
      }
    }
  };

  const { service, api } = build();

  const logicalId = (template: Template, type: string, props: object) => {
    const ids = Object.keys(template.findResources(type, { Properties: props }));
    expect(ids).toHaveLength(1);
    return ids[0];
  };

  test('should_createAnInternalAlb_when_serviceStackBuilt', () => {
    service.hasResourceProperties('AWS::ElasticLoadBalancingV2::LoadBalancer', {
      Scheme: 'internal',
    });
  });

  test('should_admitOnlyTheVpcLink_when_internalAlbReceivesTraffic', () => {
    const albId = logicalId(service, 'AWS::ElasticLoadBalancingV2::LoadBalancer', {
      Scheme: 'internal',
    });
    const alb = service.findResources('AWS::ElasticLoadBalancingV2::LoadBalancer')[albId];
    const albSgRef = alb.Properties.SecurityGroups[0]['Fn::GetAtt'][0];

    const vpcLink = Object.values(service.findResources('AWS::ApiGatewayV2::VpcLink'))[0];
    const vpcLinkSgRef = vpcLink.Properties.SecurityGroupIds[0]['Fn::GetAtt'][0];

    // Inline and standalone ingress rules on the ALB's security group: only the VPC link, port 80.
    const sg = service.findResources('AWS::EC2::SecurityGroup')[albSgRef];
    expect(sg.Properties.SecurityGroupIngress ?? []).toEqual([]);
    const ingress = Object.values(service.findResources('AWS::EC2::SecurityGroupIngress')).filter(
      (r) => JSON.stringify(r.Properties.GroupId).includes(albSgRef)
    );
    expect(ingress).toHaveLength(1);
    expect(ingress[0].Properties).toMatchObject({
      IpProtocol: 'tcp',
      FromPort: 80,
      ToPort: 80,
      SourceSecurityGroupId: { 'Fn::GetAtt': [vpcLinkSgRef, 'GroupId'] },
    });
    expect(ingress[0].Properties.CidrIp).toBeUndefined();
  });

  test('should_registerTheServiceOnBothAlbs_when_switchingOver', () => {
    const ecsService = Object.values(service.findResources('AWS::ECS::Service'))[0];
    expect(ecsService.Properties.LoadBalancers).toHaveLength(2);
  });

  test('should_pointTheAlbAlarmsAtTheInternalAlb_when_trafficMoves', () => {
    const albId = logicalId(service, 'AWS::ElasticLoadBalancingV2::LoadBalancer', {
      Scheme: 'internal',
    });
    const albAlarms = Object.values(service.findResources('AWS::CloudWatch::Alarm')).filter((a) =>
      JSON.stringify(a.Properties).includes('"LoadBalancer"')
    );
    expect(albAlarms.length).toBeGreaterThan(0);
    for (const alarm of albAlarms) {
      expect(JSON.stringify(alarm.Properties)).toContain(albId);
    }
  });

  test('should_keepExportingThePublicAlbName_when_theDeployedApiStackStillImportsIt', () => {
    const outputs = service.findOutputs('*');
    const exportsDnsName = Object.values(outputs).some((o) =>
      JSON.stringify(o.Value).includes('DNSName')
    );
    expect(exportsDnsName).toBe(true);
  });

  test('should_integrateThroughTheVpcLink_when_vpcLinkGiven', () => {
    api.hasResourceProperties('AWS::ApiGatewayV2::Integration', {
      IntegrationType: 'HTTP_PROXY',
      ConnectionType: 'VPC_LINK',
      IntegrationMethod: 'ANY',
    });
  });

  test('should_overwriteTheClientIpHeaderWithTheSourceIp_when_requestPasses', () => {
    api.hasResourceProperties('AWS::ApiGatewayV2::Integration', {
      RequestParameters: {
        [`overwrite:header.${CLIENT_IP_HEADER}`]: '$context.identity.sourceIp',
        'overwrite:path': '$request.path',
      },
    });
  });
});
