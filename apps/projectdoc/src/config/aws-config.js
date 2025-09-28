module.exports = {
  // AWS Configuration
  region: process.env.AWS_REGION || 'eu-central-1',

  // S3 Configuration
  s3: {
    bucketName: process.env.S3_BUCKET_NAME || 'project.batbern.ch',
    bucketRegion: process.env.S3_BUCKET_REGION || 'eu-central-1',
    websiteConfiguration: {
      IndexDocument: { Suffix: 'index.html' },
      ErrorDocument: { Key: 'error.html' }
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
    distributionConfig: {
      CallerReference: `batbern-docs-${Date.now()}`,
      Comment: 'BATbern Project Documentation Distribution',
      DefaultCacheBehavior: {
        TargetOriginId: 'S3-project.batbern.ch',
        ViewerProtocolPolicy: 'redirect-to-https',
        Compress: true,
        CachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad', // Managed-CachingOptimized
        OriginRequestPolicyId: '88a5eaf4-2fd4-4709-b370-b4c650ea3fcf' // Managed-CORS-S3Origin
      },
      Origins: {
        Quantity: 1,
        Items: [{
          Id: 'S3-project.batbern.ch',
          DomainName: '',  // Will be set dynamically
          S3OriginConfig: {
            OriginAccessIdentity: ''
          }
        }]
      },
      Enabled: true,
      PriceClass: 'PriceClass_100', // Use only US and Europe edge locations
      DefaultRootObject: 'index.html'
    }
  },

  // Route 53 Configuration
  route53: {
    hostedZoneId: process.env.ROUTE53_HOSTED_ZONE_ID || 'Z04921951F6B818JF0POD',
    recordName: 'project.batbern.ch',
    recordType: 'CNAME'
  },

  // SSL Certificate Configuration
  ssl: {
    certificateArn: process.env.SSL_CERTIFICATE_ARN || '',
    certificateDomain: '*.batbern.ch'
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
      '.md': 'text/markdown'
    }
  }
};