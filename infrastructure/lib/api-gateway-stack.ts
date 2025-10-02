import * as cdk from 'aws-cdk-lib';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53targets from 'aws-cdk-lib/aws-route53-targets';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';

export interface ApiGatewayStackProps extends cdk.StackProps {
  userPool: cognito.UserPool;
  userPoolClient: cognito.UserPoolClient;
  domainName?: string;
  hostedZoneId?: string;
  certificateArn?: string;
}

export class ApiGatewayStack extends cdk.Stack {
  public readonly api: apigateway.RestApi;
  public readonly authorizer: apigateway.CognitoUserPoolsAuthorizer;

  constructor(scope: Construct, id: string, props: ApiGatewayStackProps) {
    super(scope, id, props);

    // Create Lambda authorizer for custom authorization logic
    const authorizerLambda = new lambda.Function(this, 'AuthorizerLambda', {
      functionName: `${id}-AuthorizerLambda`,
      runtime: lambda.Runtime.NODEJS_18_X,
      handler: 'authorizer.handler',
      code: lambda.Code.fromInline(`
        const jwt = require('jsonwebtoken');
        const jwksClient = require('jwks-rsa');

        const client = jwksClient({
          jwksUri: \`https://cognito-idp.\${process.env.AWS_REGION}.amazonaws.com/\${process.env.USER_POOL_ID}/.well-known/jwks.json\`
        });

        function getKey(header, callback) {
          client.getSigningKey(header.kid, (err, key) => {
            const signingKey = key.publicKey || key.rsaPublicKey;
            callback(null, signingKey);
          });
        }

        exports.handler = async (event) => {
          try {
            const token = event.authorizationToken?.replace('Bearer ', '');

            if (!token) {
              throw new Error('No token provided');
            }

            // Verify and decode JWT
            const decoded = await new Promise((resolve, reject) => {
              jwt.verify(token, getKey, {
                audience: process.env.USER_POOL_CLIENT_ID,
                issuer: \`https://cognito-idp.\${process.env.AWS_REGION}.amazonaws.com/\${process.env.USER_POOL_ID}\`,
                algorithms: ['RS256']
              }, (err, decoded) => {
                if (err) reject(err);
                else resolve(decoded);
              });
            });

            // Extract user context
            const userContext = {
              userId: decoded.sub,
              email: decoded.email,
              role: decoded['custom:role'],
              companyId: decoded['custom:companyId']
            };

            // Role-based authorization
            const path = event.methodArn.split('/').slice(3).join('/');
            const method = event.httpMethod;

            const isAuthorized = checkPermission(userContext.role, path, method);

            if (!isAuthorized) {
              throw new Error('Insufficient permissions');
            }

            // Return policy
            return {
              principalId: decoded.sub,
              policyDocument: {
                Version: '2012-10-17',
                Statement: [{
                  Action: 'execute-api:Invoke',
                  Effect: 'Allow',
                  Resource: event.methodArn
                }]
              },
              context: {
                userId: decoded.sub,
                email: decoded.email,
                role: decoded['custom:role'],
                companyId: decoded['custom:companyId'] || ''
              }
            };
          } catch (error) {
            console.error('Authorization failed:', error);
            throw new Error('Unauthorized');
          }
        };

        function checkPermission(role, path, method) {
          const permissions = {
            organizer: {
              'events': ['GET', 'POST', 'PUT', 'DELETE'],
              'speakers': ['GET', 'POST', 'PUT', 'DELETE'],
              'partners': ['GET', 'POST', 'PUT', 'DELETE'],
              'content': ['GET'],
              'analytics': ['GET']
            },
            speaker: {
              'events': ['GET'],
              'speakers': ['GET', 'PUT'],
              'content': ['GET', 'POST', 'PUT']
            },
            partner: {
              'events': ['GET'],
              'partners': ['GET', 'PUT'],
              'analytics': ['GET'],
              'content': ['GET']
            },
            attendee: {
              'events': ['GET'],
              'content': ['GET'],
              'speakers': ['GET']
            }
          };

          const rolePermissions = permissions[role];
          if (!rolePermissions) return false;

          // Extract resource from path
          const resource = path.split('/')[0];
          const allowedMethods = rolePermissions[resource];

          return allowedMethods && allowedMethods.includes(method);
        }
      `),
      environment: {
        USER_POOL_ID: props.userPool.userPoolId,
        USER_POOL_CLIENT_ID: props.userPoolClient.userPoolClientId,
      },
      timeout: cdk.Duration.seconds(10),
    });

    // Grant necessary permissions to the authorizer Lambda
    authorizerLambda.addToRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cognito-idp:GetUser',
        'cognito-idp:ListUsers',
      ],
      resources: [props.userPool.userPoolArn],
    }));

    // Create Cognito authorizer
    this.authorizer = new apigateway.CognitoUserPoolsAuthorizer(this, 'CognitoAuthorizer', {
      cognitoUserPools: [props.userPool],
      identitySource: 'method.request.header.Authorization',
      authorizerName: 'CognitoAuthorizer',
    });

    // Create API Gateway
    this.api = new apigateway.RestApi(this, 'BATbernAPI', {
      restApiName: 'BATbern Platform API',
      description: 'API Gateway for BATbern Platform',
      defaultCorsPreflightOptions: {
        allowOrigins: [
          'https://www.batbern.ch',
          'https://staging.batbern.ch',
          'http://localhost:3000',
        ],
        allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
        allowHeaders: [
          'Content-Type',
          'X-Amz-Date',
          'Authorization',
          'X-Api-Key',
          'X-Amz-Security-Token',
          'X-Amz-User-Agent',
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

    // Create Lambda for request transformation and routing
    const routingLambda = new lambda.Function(this, 'RoutingLambda', {
      functionName: `${id}-RoutingLambda`,
      runtime: lambda.Runtime.NODEJS_18_X,
      handler: 'router.handler',
      code: lambda.Code.fromInline(`
        const https = require('https');
        const url = require('url');

        const serviceEndpoints = {
          'events': process.env.EVENT_SERVICE_URL,
          'speakers': process.env.SPEAKER_SERVICE_URL,
          'partners': process.env.PARTNER_SERVICE_URL,
          'content': process.env.ATTENDEE_SERVICE_URL
        };

        exports.handler = async (event) => {
          try {
            const path = event.pathParameters?.proxy || '';
            const method = event.httpMethod;
            const headers = event.headers || {};
            const body = event.body;

            // Extract service from path
            const pathParts = path.split('/');
            const service = pathParts[0];
            const servicePath = '/' + pathParts.slice(1).join('/');

            const serviceUrl = serviceEndpoints[service];
            if (!serviceUrl) {
              return {
                statusCode: 404,
                headers: {
                  'Content-Type': 'application/json',
                  'Access-Control-Allow-Origin': '*'
                },
                body: JSON.stringify({
                  success: false,
                  error: {
                    code: 'SERVICE_NOT_FOUND',
                    message: 'Service not found'
                  },
                  metadata: {},
                  requestId: event.requestContext.requestId,
                  timestamp: new Date().toISOString()
                })
              };
            }

            // Forward request to microservice
            const result = await forwardRequest(serviceUrl + servicePath, method, headers, body);

            // Standardize response
            const standardResponse = {
              success: result.statusCode < 400,
              data: result.body ? JSON.parse(result.body) : null,
              error: result.statusCode >= 400 ? {
                code: 'SERVICE_ERROR',
                message: 'Service returned an error'
              } : null,
              metadata: {
                service,
                statusCode: result.statusCode
              },
              requestId: event.requestContext.requestId,
              timestamp: new Date().toISOString()
            };

            return {
              statusCode: result.statusCode,
              headers: {
                'Content-Type': 'application/json',
                'Access-Control-Allow-Origin': '*',
                'X-Request-ID': event.requestContext.requestId
              },
              body: JSON.stringify(standardResponse)
            };
          } catch (error) {
            console.error('Routing error:', error);

            return {
              statusCode: 500,
              headers: {
                'Content-Type': 'application/json',
                'Access-Control-Allow-Origin': '*'
              },
              body: JSON.stringify({
                success: false,
                error: {
                  code: 'INTERNAL_ERROR',
                  message: 'Internal server error'
                },
                metadata: {},
                requestId: event.requestContext?.requestId || 'unknown',
                timestamp: new Date().toISOString()
              })
            };
          }
        };

        function forwardRequest(targetUrl, method, headers, body) {
          return new Promise((resolve, reject) => {
            const parsedUrl = url.parse(targetUrl);

            const options = {
              hostname: parsedUrl.hostname,
              port: parsedUrl.port || 443,
              path: parsedUrl.path,
              method: method,
              headers: {
                ...headers,
                'Host': parsedUrl.hostname
              }
            };

            const req = https.request(options, (res) => {
              let data = '';
              res.on('data', (chunk) => {
                data += chunk;
              });
              res.on('end', () => {
                resolve({
                  statusCode: res.statusCode,
                  headers: res.headers,
                  body: data
                });
              });
            });

            req.on('error', (error) => {
              reject(error);
            });

            if (body) {
              req.write(body);
            }
            req.end();
          });
        }
      `),
      environment: {
        EVENT_SERVICE_URL: 'https://events.batbern.ch',
        SPEAKER_SERVICE_URL: 'https://speakers.batbern.ch',
        PARTNER_SERVICE_URL: 'https://partners.batbern.ch',
        ATTENDEE_SERVICE_URL: 'https://content.batbern.ch',
      },
      timeout: cdk.Duration.seconds(30),
    });

    // Create API resources and methods
    const apiResource = this.api.root.addResource('api');

    // Proxy resource for all service routes
    const proxyResource = apiResource.addResource('{proxy+}');

    // Add methods for all HTTP verbs
    ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].forEach(method => {
      proxyResource.addMethod(method, new apigateway.LambdaIntegration(routingLambda), {
        authorizer: this.authorizer,
        authorizationType: apigateway.AuthorizationType.COGNITO,
        requestParameters: {
          'method.request.path.proxy': true,
        },
      });
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
      if (props.hostedZoneId) {
        const hostedZone = route53.HostedZone.fromHostedZoneId(
          this, 'HostedZone', props.hostedZoneId
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
        exportName: `${id}-ApiCustomDomainUrl`,
      });
    }

    // Outputs
    new cdk.CfnOutput(this, 'ApiGatewayUrl', {
      value: this.api.url,
      description: 'API Gateway URL',
      exportName: `${id}-ApiGatewayUrl`,
    });

    new cdk.CfnOutput(this, 'ApiGatewayId', {
      value: this.api.restApiId,
      description: 'API Gateway ID',
      exportName: `${id}-ApiGatewayId`,
    });

    new cdk.CfnOutput(this, 'AuthorizerArn', {
      value: authorizerLambda.functionArn,
      description: 'Lambda Authorizer ARN',
      exportName: `${id}-AuthorizerArn`,
    });
  }
}