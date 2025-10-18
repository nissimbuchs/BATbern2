import * as cdk from 'aws-cdk-lib';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53targets from 'aws-cdk-lib/aws-route53-targets';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface ApiGatewayStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  userPool: cognito.UserPool;
  userPoolClient: cognito.UserPoolClient;
  domainName?: string;
  hostedZoneId?: string;
  certificateArn?: string;
  apiGatewayServiceUrl?: string; // Internal ALB URL for Spring Boot API Gateway
}

/**
 * API Gateway Stack - AWS API Gateway proxy to Spring Boot API Gateway
 *
 * Architecture:
 * Client → AWS API Gateway (Cognito auth) → Spring Boot API Gateway (ECS) → Microservices (ECS)
 *
 * The Spring Boot API Gateway handles all routing logic to domain microservices.
 * This AWS API Gateway provides:
 * - AC16: Cognito authorization at the edge
 * - AC4: CORS, rate limiting, and TLS termination
 * - Custom domain mapping
 */
export class ApiGatewayStack extends cdk.Stack {
  public readonly api: apigateway.RestApi;
  public readonly authorizer: apigateway.CognitoUserPoolsAuthorizer;

  constructor(scope: Construct, id: string, props: ApiGatewayStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // Cognito authorizer - validates JWT tokens
    this.authorizer = new apigateway.CognitoUserPoolsAuthorizer(this, 'CognitoAuthorizer', {
      cognitoUserPools: [props.userPool],
      identitySource: 'method.request.header.Authorization',
      authorizerName: `${envName}-CognitoAuthorizer`,
    });

    // Determine allowed origins based on environment
    const allowOrigins = envName === 'production'
      ? ['https://www.batbern.ch']
      : envName === 'staging'
      ? ['https://staging.batbern.ch']
      : ['http://localhost:3000'];

    // Create API Gateway
    this.api = new apigateway.RestApi(this, 'BATbernAPI', {
      restApiName: `BATbern Platform API - ${envName}`,
      description: `API Gateway for BATbern Platform - ${envName}`,
      defaultCorsPreflightOptions: {
        allowOrigins,
        allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
        allowHeaders: [
          'Content-Type',
          'X-Amz-Date',
          'Authorization',
          'X-Api-Key',
          'X-Amz-Security-Token',
          'X-Amz-User-Agent',
          'X-Correlation-ID',
          'Accept-Language',
          'Accept',
        ],
        allowCredentials: true,
      },
      deployOptions: {
        stageName: 'v1',
        throttlingRateLimit: 1000,
        throttlingBurstLimit: 2000,
        metricsEnabled: true,
        tracingEnabled: true,
        dataTraceEnabled: true,
        loggingLevel: apigateway.MethodLoggingLevel.INFO,
      },
      endpointConfiguration: {
        types: [apigateway.EndpointType.REGIONAL],
      },
    });

    // Spring Boot API Gateway service URL
    // In development: will be set after microservices stack is deployed
    // In production: internal ALB endpoint for API Gateway service
    const apiGatewayServiceUrl = props.apiGatewayServiceUrl ||
      `http://api-gateway-${envName}.internal`;

    // HTTP Proxy Integration to Spring Boot API Gateway
    // Note: Using INTERNET connection type for now
    // TODO: Add VPC Link for private integration once microservices are deployed
    const httpIntegration = new apigateway.HttpIntegration(apiGatewayServiceUrl, {
      httpMethod: 'ANY',
      proxy: true,
      options: {
        connectionType: apigateway.ConnectionType.INTERNET,
        requestParameters: {
          'integration.request.path.proxy': 'method.request.path.proxy',
          'integration.request.header.X-Forwarded-For': 'context.identity.sourceIp',
          'integration.request.header.X-Amz-User-Agent': 'context.identity.userAgent',
        },
        integrationResponses: [{
          statusCode: '200',
        }],
      },
    });

    // Root resource - proxy all requests to Spring Boot API Gateway
    const proxyResource = this.api.root.addResource('{proxy+}');

    // Add methods for all HTTP verbs with Cognito authorization
    ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].forEach(method => {
      proxyResource.addMethod(method, httpIntegration, {
        authorizer: this.authorizer,
        authorizationType: apigateway.AuthorizationType.COGNITO,
        requestParameters: {
          'method.request.path.proxy': true,
        },
        methodResponses: [{
          statusCode: '200',
        }],
      });
    });

    // Public config endpoint (no auth required) for frontend bootstrap
    const configIntegration = new apigateway.HttpIntegration(`${apiGatewayServiceUrl}/api/v1/config`, {
      httpMethod: 'GET',
      options: {
        connectionType: apigateway.ConnectionType.INTERNET,
        requestParameters: {
          'integration.request.header.X-Forwarded-For': 'context.identity.sourceIp',
        },
        integrationResponses: [{
          statusCode: '200',
        }],
      },
    });

    const apiResource = this.api.root.addResource('api');
    const v1Resource = apiResource.addResource('v1');
    const configResource = v1Resource.addResource('config');
    configResource.addMethod('GET', configIntegration, {
      authorizationType: apigateway.AuthorizationType.NONE,
      methodResponses: [{
        statusCode: '200',
      }],
    });

    // Health check endpoint (no auth required)
    const healthResource = this.api.root.addResource('health');
    healthResource.addMethod('GET', new apigateway.MockIntegration({
      integrationResponses: [{
        statusCode: '200',
        responseTemplates: {
          'application/json': JSON.stringify({
            status: 'healthy',
            timestamp: '$context.requestTime',
            requestId: '$context.requestId'
          })
        }
      }],
      requestTemplates: {
        'application/json': '{"statusCode": 200}'
      }
    }), {
      methodResponses: [{
        statusCode: '200',
        responseModels: {
          'application/json': apigateway.Model.EMPTY_MODEL
        }
      }]
    });

    // Custom domain (if provided)
    if (props.domainName && props.certificateArn) {
      const certificate = certificatemanager.Certificate.fromCertificateArn(
        this, 'ApiCertificate', props.certificateArn
      );

      const domainName = new apigateway.DomainName(this, 'ApiDomainName', {
        domainName: props.domainName,
        certificate,
        endpointType: apigateway.EndpointType.REGIONAL,
      });

      new apigateway.BasePathMapping(this, 'ApiBasePathMapping', {
        domainName,
        restApi: this.api,
        stage: this.api.deploymentStage,
      });

      // Create Route 53 record (if hosted zone provided)
      if (props.hostedZoneId && props.domainName && props.config.domain?.zoneName) {
        const hostedZone = route53.HostedZone.fromHostedZoneAttributes(
          this, 'HostedZone', {
            hostedZoneId: props.hostedZoneId,
            zoneName: props.config.domain.zoneName,
          }
        );

        new route53.ARecord(this, 'ApiARecord', {
          zone: hostedZone,
          recordName: props.domainName,
          target: route53.RecordTarget.fromAlias(
            new route53targets.ApiGatewayDomain(domainName)
          ),
        });
      }

      // Output custom domain URL
      new cdk.CfnOutput(this, 'ApiCustomDomainUrl', {
        value: `https://${props.domainName}`,
        description: 'API Gateway Custom Domain URL',
        exportName: `${envName}-ApiCustomDomainUrl`,
      });
    }

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'API-Gateway');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'ApiGatewayUrl', {
      value: this.api.url,
      description: 'API Gateway URL',
      exportName: `${envName}-ApiGatewayUrl`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayId', {
      value: this.api.restApiId,
      description: 'API Gateway ID',
      exportName: `${envName}-ApiGatewayId`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayServiceUrl', {
      value: apiGatewayServiceUrl,
      description: 'Spring Boot API Gateway Service URL (internal)',
    });
  }
}