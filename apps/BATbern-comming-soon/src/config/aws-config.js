export default {
  // AWS Configuration
  region: process.env.AWS_REGION || 'eu-central-1',

  // S3 Configuration
  s3: {
    bucketName: process.env.S3_BUCKET_NAME || 'batbern.ch',
    bucketRegion: process.env.S3_BUCKET_REGION || 'eu-central-1',
    websiteConfiguration: {
      IndexDocument: { Suffix: 'index.html' },
      ErrorDocument: { Key: 'index.html' } // SPA routing - redirect errors to index
    },
    corsConfiguration: {
      CORSRules: [{
        AllowedHeaders: ['*'],
        AllowedMethods: ['GET', 'HEAD'],
        AllowedOrigins: ['*'],
        MaxAgeSeconds: 3000
      }]
    }
  },

  // CloudFront Configuration
  cloudfront: {
    enabled: process.env.CLOUDFRONT_ENABLED !== 'false',
    distributionConfig: {
      CallerReference: `batbern-coming-soon-${Date.now()}`,
      Comment: 'BATbern Coming Soon Page Distribution',
      Aliases: {
        Quantity: 1,
        Items: ['batbern.ch']
      },
      DefaultCacheBehavior: {
        TargetOriginId: 'S3-batbern.ch',
        ViewerProtocolPolicy: 'redirect-to-https',
        Compress: true,
        AllowedMethods: {
          Quantity: 2,
          Items: ['GET', 'HEAD'],
          CachedMethods: {
            Quantity: 2,
            Items: ['GET', 'HEAD']
          }
        },
        CachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad', // Managed-CachingOptimized
        OriginRequestPolicyId: '88a5eaf4-2fd4-4709-b370-b4c650ea3fcf' // Managed-CORS-S3Origin
      },
      Origins: {
        Quantity: 1,
        Items: [{
          Id: 'S3-batbern.ch',
          DomainName: '',  // Will be set dynamically to S3 website endpoint
          CustomOriginConfig: {
            HTTPPort: 80,
            HTTPSPort: 443,
            OriginProtocolPolicy: 'http-only',
            OriginSslProtocols: {
              Quantity: 3,
              Items: ['TLSv1', 'TLSv1.1', 'TLSv1.2']
            }
          }
        }]
      },
      CustomErrorResponses: {
        Quantity: 2,
        Items: [
          {
            ErrorCode: 403,
            ResponseCode: 200,
            ResponsePagePath: '/index.html',
            ErrorCachingMinTTL: 0
          },
          {
            ErrorCode: 404,
            ResponseCode: 200,
            ResponsePagePath: '/index.html',
            ErrorCachingMinTTL: 0
          }
        ]
      },
      Enabled: true,
      PriceClass: 'PriceClass_100', // Use only US and Europe edge locations
      DefaultRootObject: 'index.html',
      ViewerCertificate: {
        ACMCertificateArn: '', // Will be set dynamically
        SSLSupportMethod: 'sni-only',
        MinimumProtocolVersion: 'TLSv1.2_2021'
      }
    }
  },

  // Route 53 Configuration
  route53: {
    hostedZoneId: process.env.ROUTE53_HOSTED_ZONE_ID || 'Z003987919RPX23XXEU48', // Production hosted zone
    recordName: 'batbern.ch',
    recordType: 'A', // A record for CloudFront alias
    createAlias: true,
    aliasHostedZoneId: 'Z2FDTNDATAQYW2' // CloudFront hosted zone ID (constant for all distributions)
  },

  // SSL Certificate Configuration
  ssl: {
    certificateArn: process.env.SSL_CERTIFICATE_ARN || '',
    certificateDomain: 'batbern.ch',
    alternativeNames: ['www.batbern.ch'],
    validationMethod: 'DNS',
    region: 'us-east-1' // ACM certificates for CloudFront must be in us-east-1
  },

  // Deployment settings
  deployment: {
    cacheInvalidation: true,
    invalidationPaths: ['/*'],
    waitForDeployment: true,
    retryAttempts: 3,
    retryDelay: 5000 // 5 seconds
  },

  // File upload settings
  upload: {
    concurrency: 10,
    excludePatterns: [
      '.DS_Store',
      'Thumbs.db',
      '*.tmp',
      '*.temp'
    ],
    contentTypeMapping: {
      '.html': 'text/html',
      '.css': 'text/css',
      '.js': 'application/javascript',
      '.json': 'application/json',
      '.png': 'image/png',
      '.jpg': 'image/jpeg',
      '.jpeg': 'image/jpeg',
      '.gif': 'image/gif',
      '.svg': 'image/svg+xml',
      '.ico': 'image/x-icon',
      '.pdf': 'application/pdf',
      '.woff': 'font/woff',
      '.woff2': 'font/woff2',
      '.ttf': 'font/ttf',
      '.eot': 'application/vnd.ms-fontobject'
    }
  }
};
