import * as cdk from 'aws-cdk-lib';
import * as apigatewayv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as apigatewayv2_integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as apigatewayv2_authorizers from 'aws-cdk-lib/aws-apigatewayv2-authorizers';
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
 * API Gateway Stack - Simplified HTTP API (Backend Controls Authentication)
 *
 * Architecture:
 * Client → HTTP API (CORS, Throttling) → Spring Boot API Gateway (ECS) → Microservices (ECS)
 *
 * Migrated from REST API to HTTP API for:
 * - ✓ OAuth2 compliance - validates ACCESS TOKENS (not ID tokens)
 * - ✓ 60% cost reduction compared to REST API
 * - ✓ Better performance and lower latency
 * - ✓ Automatic CORS handling
 * - ✓ Native HTTP/2 support
 *
 * Security Model (ADR-008 - Simplified API Gateway):
 * - Infrastructure: Handles edge concerns (CORS, throttling, DDoS protection)
 * - Backend Services: Validate JWT tokens and enforce route-level authentication
 * - Benefit: Adding routes never requires infrastructure deployment
 * - All route security controlled by microservices via Spring Security
 */
export class ApiGatewayStack extends cdk.Stack {
  public readonly api: apigatewayv2.HttpApi;
  public readonly authorizer: apigatewayv2_authorizers.HttpJwtAuthorizer;

  constructor(scope: Construct, id: string, props: ApiGatewayStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;
    const region = props.config.region;

    // JWT Authorizer - validates ACCESS TOKENS (OAuth2 best practice)
    // Validates: signature, issuer, audience (client_id), expiration, cognito:groups
    const issuerUrl = `https://cognito-idp.${region}.amazonaws.com/${props.userPool.userPoolId}`;

    this.authorizer = new apigatewayv2_authorizers.HttpJwtAuthorizer(
      'JwtAuthorizer',
      issuerUrl,
      {
        jwtAudience: [props.userPoolClient.userPoolClientId],
        authorizerName: `${envName}-jwt-authorizer`,
        identitySource: ['$request.header.Authorization'],
      }
    );

    // Determine allowed origins based on environment
    const isProdTraffic = props.config.isProduction ?? (envName === 'production');
    const allowOrigins = isProdTraffic
      ? [
          `https://${props.config.domain?.frontendDomain ?? 'www.batbern.ch'}`,
          `https://${props.config.domain?.zoneName ?? 'batbern.ch'}`,
        ]
      : envName === 'staging'
      ? ['https://staging.batbern.ch']
      : ['http://localhost:3000'];

    // Create HTTP API Gateway (v2)
    this.api = new apigatewayv2.HttpApi(this, 'BATbernAPI', {
      apiName: `BATbern Platform API - ${envName}`,
      description: `HTTP API Gateway for BATbern Platform - ${envName} (OAuth2 compliant)`,
      // Automatic CORS configuration
      corsPreflight: {
        allowOrigins,
        allowMethods: [
          apigatewayv2.CorsHttpMethod.GET,
          apigatewayv2.CorsHttpMethod.POST,
          apigatewayv2.CorsHttpMethod.PUT,
          apigatewayv2.CorsHttpMethod.DELETE,
          apigatewayv2.CorsHttpMethod.PATCH,
          apigatewayv2.CorsHttpMethod.OPTIONS,
        ],
        // Use wildcard to allow all headers (case-insensitive per RFC 7230)
        // Prevents CORS rejections due to case variations (x-correlation-id vs X-Correlation-ID)
        allowHeaders: ['*'],
        allowCredentials: true,
        maxAge: cdk.Duration.hours(1),
      },
      // Disable default stage - we'll create explicit v1 stage
      createDefaultStage: false,
    });

    // Create explicit v1 stage with throttling
    const v1Stage = new apigatewayv2.HttpStage(this, 'V1Stage', {
      httpApi: this.api,
      stageName: 'v1',
      autoDeploy: true,
      throttle: {
        rateLimit: 1000, // requests per second
        burstLimit: 2000,
      },
    });

    // Spring Boot API Gateway service URL
    const apiGatewayServiceUrl = props.apiGatewayServiceUrl ||
      `http://api-gateway-${envName}.internal`;

    // HTTP Proxy Integration to Spring Boot API Gateway
    // Uses greedy path matching {proxy+} which captures full path (e.g., /api/v1/companies)
    // CDK automatically forwards the captured path when using /{proxy} in the URL template
    const httpIntegration = new apigatewayv2_integrations.HttpUrlIntegration(
      'SpringBootApiGatewayIntegration',
      `${apiGatewayServiceUrl}/{proxy}`,
      {
        method: apigatewayv2.HttpMethod.ANY,
      }
    );

    /**
     * Catch-all route - forwards ALL requests to backend
     *
     * ADR-008: Simplified API Gateway Routing
     * - Infrastructure: Just proxies all requests (no JWT validation here)
     * - Backend: Validates JWT tokens and enforces authentication/authorization
     * - Benefit: Adding new routes never requires infrastructure deployment
     *
     * Security is enforced by:
     * - Spring Boot API Gateway SecurityConfig
     * - Individual microservice SecurityConfig files
     * - @PreAuthorize annotations on controller methods
     */
    this.api.addRoutes({
      path: '/{proxy+}',
      methods: [
        apigatewayv2.HttpMethod.GET,
        apigatewayv2.HttpMethod.POST,
        apigatewayv2.HttpMethod.PUT,
        apigatewayv2.HttpMethod.DELETE,
        apigatewayv2.HttpMethod.PATCH,
      ],
      integration: httpIntegration,
      // No authorizer - backend handles all authentication
    });

    // Custom domain (if provided)
    if (props.domainName && props.certificateArn) {
      const certificate = certificatemanager.Certificate.fromCertificateArn(
        this, 'ApiCertificate', props.certificateArn
      );

      const domainName = new apigatewayv2.DomainName(this, 'ApiDomainName', {
        domainName: props.domainName,
        certificate,
      });

      new apigatewayv2.ApiMapping(this, 'ApiMapping', {
        api: this.api,
        domainName,
        stage: v1Stage,
      });

      // Create Route 53 record (if hosted zone provided)
      if (props.hostedZoneId && props.config.domain?.zoneName) {
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
            new route53targets.ApiGatewayv2DomainProperties(
              domainName.regionalDomainName,
              domainName.regionalHostedZoneId
            )
          ),
        });
      }

      // Output custom domain URL
      new cdk.CfnOutput(this, 'ApiCustomDomainUrl', {
        value: `https://${props.domainName}`,
        description: 'HTTP API Gateway Custom Domain URL',
        exportName: `${envName}-ApiCustomDomainUrl`,
      });
    }

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'API-Gateway-HTTP');
    cdk.Tags.of(this).add('Project', 'BATbern');
    cdk.Tags.of(this).add('OAuth2Compliant', 'true');

    // Outputs
    new cdk.CfnOutput(this, 'ApiGatewayUrl', {
      value: this.api.apiEndpoint,
      description: 'HTTP API Gateway URL (base)',
      exportName: `${envName}-ApiGatewayUrl`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayV1Url', {
      value: `${this.api.apiEndpoint}/v1`,
      description: 'HTTP API Gateway V1 Stage URL',
      exportName: `${envName}-ApiGatewayV1Url`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayId', {
      value: this.api.apiId,
      description: 'HTTP API Gateway ID',
      exportName: `${envName}-ApiGatewayId`,
    });

    new cdk.CfnOutput(this, 'JwtIssuer', {
      value: issuerUrl,
      description: 'JWT Issuer URL (Cognito User Pool)',
      exportName: `${envName}-JwtIssuer`,
    });

    new cdk.CfnOutput(this, 'JwtAudience', {
      value: props.userPoolClient.userPoolClientId,
      description: 'JWT Audience (User Pool Client ID)',
      exportName: `${envName}-JwtAudience`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayServiceUrl', {
      value: apiGatewayServiceUrl,
      description: 'Spring Boot API Gateway Service URL (internal)',
    });
  }
}