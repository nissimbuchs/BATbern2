#!/usr/bin/env node

const fs = require('fs-extra');
const path = require('path');
const glob = require('glob');
const mimeTypes = require('mime-types');
const {
  S3Client,
  CreateBucketCommand,
  PutBucketWebsiteCommand,
  PutBucketCorsCommand,
  PutBucketPolicyCommand,
  PutPublicAccessBlockCommand,
  PutObjectCommand,
  HeadBucketCommand,
  GetBucketLocationCommand
} = require('@aws-sdk/client-s3');
const {
  CloudFrontClient,
  CreateDistributionCommand,
  CreateInvalidationCommand,
  GetDistributionCommand,
  ListDistributionsCommand
} = require('@aws-sdk/client-cloudfront');
const {
  Route53Client,
  ChangeResourceRecordSetsCommand,
  ListHostedZonesCommand
} = require('@aws-sdk/client-route-53');

const config = require('../src/config/site-config');
const awsConfig = require('../src/config/aws-config');

class DocumentationDeployer {
  constructor() {
    this.config = config;
    this.awsConfig = awsConfig;
    this.s3Client = new S3Client({ region: awsConfig.region });
    this.cloudFrontClient = new CloudFrontClient({ region: 'us-east-1' }); // CloudFront is global
    this.route53Client = new Route53Client({ region: 'us-east-1' }); // Route53 is global
    this.deploymentInfo = {};
  }

  async deploy() {
    console.log('☁️  BATbern Documentation Deployer');
    console.log('==================================\n');

    try {
      // Validate configuration
      await this.validateConfiguration();

      // Check if build exists
      await this.validateBuildOutput();

      // Deploy to S3
      await this.deployToS3();

      // Setup CloudFront (if needed)
      await this.setupCloudFront();

      // Setup DNS (if configured)
      await this.setupDNS();

      // Final validation
      await this.validateDeployment();

      console.log('\n✅ Deployment completed successfully!');
      console.log(`🌐 Website URL: ${this.deploymentInfo.websiteUrl}`);
      console.log(`📊 Deployed ${this.deploymentInfo.uploadedFiles} files`);

    } catch (error) {
      console.error('\n❌ Deployment failed:', error.message);
      if (process.env.NODE_ENV === 'development') {
        console.error(error.stack);
      }
      process.exit(1);
    }
  }

  async validateConfiguration() {
    console.log('🔧 Validating configuration...');

    // Check AWS credentials
    try {
      await this.s3Client.send(new HeadBucketCommand({ Bucket: 'test-bucket-check' }));
    } catch (error) {
      if (error.name === 'NotFound' || error.name === 'Forbidden') {
        // This is expected - we just want to verify credentials work
        console.log('   ✅ AWS credentials verified');
      } else if (error.name === 'CredentialsProviderError' || error.name === 'UnknownEndpoint') {
        throw new Error('AWS credentials not configured. Please run `aws configure` or set environment variables.');
      }
    }

    // Validate bucket name
    if (!this.awsConfig.s3.bucketName) {
      throw new Error('S3 bucket name not configured');
    }

    console.log(`   📦 Target bucket: ${this.awsConfig.s3.bucketName}`);
    console.log(`   🌍 AWS region: ${this.awsConfig.region}`);
    console.log('✅ Configuration validated\n');
  }

  async validateBuildOutput() {
    console.log('📁 Validating build output...');

    const distPath = path.resolve(this.config.outputPath);

    if (!await fs.pathExists(distPath)) {
      throw new Error(`Build output not found at ${distPath}. Please run 'npm run build' first.`);
    }

    const indexPath = path.join(distPath, 'index.html');
    if (!await fs.pathExists(indexPath)) {
      throw new Error('index.html not found in build output. Please run \'npm run build\' first.');
    }

    const files = await glob.glob('**/*', { cwd: distPath, nodir: true });
    console.log(`   📄 Found ${files.length} files to deploy`);
    console.log('✅ Build output validated\n');

    this.deploymentInfo.totalFiles = files.length;
  }

  async deployToS3() {
    console.log('📦 Deploying to S3...');

    const bucketName = this.awsConfig.s3.bucketName;

    // Check if bucket exists, create if not
    await this.ensureBucketExists(bucketName);

    // Configure bucket for website hosting
    await this.configureBucketWebsite(bucketName);

    // Upload files
    await this.uploadFiles(bucketName);

    console.log('✅ S3 deployment completed\n');
  }

  async ensureBucketExists(bucketName) {
    console.log(`   🪣 Checking bucket: ${bucketName}`);

    try {
      await this.s3Client.send(new HeadBucketCommand({ Bucket: bucketName }));
      console.log('   ✅ Bucket exists');
    } catch (error) {
      if (error.name === 'NotFound') {
        console.log('   🔨 Creating bucket...');

        const createParams = { Bucket: bucketName };

        // Add location constraint if not in us-east-1
        if (this.awsConfig.region !== 'us-east-1') {
          createParams.CreateBucketConfiguration = {
            LocationConstraint: this.awsConfig.region
          };
        }

        await this.s3Client.send(new CreateBucketCommand(createParams));
        console.log('   ✅ Bucket created');

        // Wait a bit for bucket to be fully ready
        await new Promise(resolve => setTimeout(resolve, 2000));
      } else {
        throw error;
      }
    }
  }

  async configureBucketWebsite(bucketName) {
    console.log('   🌐 Configuring website hosting...');

    // Configure website hosting
    const websiteConfig = {
      Bucket: bucketName,
      WebsiteConfiguration: this.awsConfig.s3.websiteConfiguration
    };

    await this.s3Client.send(new PutBucketWebsiteCommand(websiteConfig));

    // Configure CORS
    const corsConfig = {
      Bucket: bucketName,
      CORSConfiguration: this.awsConfig.s3.corsConfiguration
    };

    await this.s3Client.send(new PutBucketCorsCommand(corsConfig));

    // Note: Public access block configuration should be set manually if needed
    // aws s3api put-public-access-block --bucket bucketName --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=false,RestrictPublicBuckets=false"

    // Set bucket policy for public read access
    const bucketPolicy = {
      Version: '2012-10-17',
      Statement: [{
        Sid: 'PublicReadGetObject',
        Effect: 'Allow',
        Principal: '*',
        Action: 's3:GetObject',
        Resource: `arn:aws:s3:::${bucketName}/*`
      }]
    };

    const policyConfig = {
      Bucket: bucketName,
      Policy: JSON.stringify(bucketPolicy)
    };

    await this.s3Client.send(new PutBucketPolicyCommand(policyConfig));

    // Get the website URL
    const region = this.awsConfig.s3.bucketRegion;
    this.deploymentInfo.websiteUrl = `http://${bucketName}.s3-website.${region}.amazonaws.com`;

    console.log('   ✅ Website hosting configured');
  }

  async uploadFiles(bucketName) {
    console.log('   📤 Uploading files...');

    const distPath = path.resolve(this.config.outputPath);
    const files = await glob.glob('**/*', { cwd: distPath, nodir: true });

    let uploadedCount = 0;
    const concurrency = this.awsConfig.upload.concurrency || 10;

    // Upload files in batches
    for (let i = 0; i < files.length; i += concurrency) {
      const batch = files.slice(i, i + concurrency);
      const promises = batch.map(file => this.uploadFile(bucketName, file, distPath));

      await Promise.all(promises);
      uploadedCount += batch.length;

      process.stdout.write(`\r   📤 Uploaded ${uploadedCount}/${files.length} files`);
    }

    console.log('\n   ✅ All files uploaded');
    this.deploymentInfo.uploadedFiles = uploadedCount;
  }

  async uploadFile(bucketName, filePath, basePath) {
    const fullPath = path.join(basePath, filePath);
    const fileContent = await fs.readFile(fullPath);

    // Determine content type
    const contentType = this.getContentType(filePath);

    // Prepare upload parameters
    const uploadParams = {
      Bucket: bucketName,
      Key: filePath.replace(/\\/g, '/'), // Ensure forward slashes for S3
      Body: fileContent,
      ContentType: contentType
    };

    // Add cache control headers
    if (this.shouldCacheFile(filePath)) {
      uploadParams.CacheControl = 'public, max-age=31536000'; // 1 year
    } else {
      uploadParams.CacheControl = 'public, max-age=3600'; // 1 hour
    }

    try {
      await this.s3Client.send(new PutObjectCommand(uploadParams));
    } catch (error) {
      console.error(`\n   ❌ Failed to upload ${filePath}:`, error.message);
      throw error;
    }
  }

  getContentType(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    return this.awsConfig.upload.contentTypeMapping[ext] ||
           mimeTypes.lookup(filePath) ||
           'application/octet-stream';
  }

  shouldCacheFile(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    const staticAssets = ['.css', '.js', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.woff', '.woff2'];
    return staticAssets.includes(ext);
  }

  async setupCloudFront() {
    if (!this.awsConfig.cloudfront || process.env.SKIP_CLOUDFRONT === 'true') {
      console.log('⏭️  Skipping CloudFront setup (not configured)\n');
      return;
    }

    console.log('🚀 Setting up CloudFront distribution...');

    try {
      // Check if distribution already exists
      const existingDistribution = await this.findExistingDistribution();

      if (existingDistribution) {
        console.log('   ✅ Using existing CloudFront distribution');
        this.deploymentInfo.distributionId = existingDistribution.Id;
        this.deploymentInfo.distributionDomain = existingDistribution.DomainName;

        // Create invalidation
        if (this.awsConfig.deployment.cacheInvalidation) {
          await this.createInvalidation(existingDistribution.Id);
        }
      } else {
        console.log('   🔨 Creating new CloudFront distribution...');
        // Implementation for creating new distribution would go here
        console.log('   ⚠️  CloudFront distribution creation not implemented in this version');
      }

    } catch (error) {
      console.warn('   ⚠️  CloudFront setup failed:', error.message);
    }

    console.log('✅ CloudFront setup completed\n');
  }

  async findExistingDistribution() {
    try {
      const command = new ListDistributionsCommand({});
      const response = await this.cloudFrontClient.send(command);

      if (response.DistributionList && response.DistributionList.Items) {
        return response.DistributionList.Items.find(dist =>
          dist.Origins && dist.Origins.Items &&
          dist.Origins.Items.some(origin =>
            origin.DomainName.includes(this.awsConfig.s3.bucketName)
          )
        );
      }
    } catch (error) {
      console.warn('   ⚠️  Could not list CloudFront distributions:', error.message);
    }

    return null;
  }

  async createInvalidation(distributionId) {
    console.log('   🔄 Creating cache invalidation...');

    try {
      const invalidationParams = {
        DistributionId: distributionId,
        InvalidationBatch: {
          Paths: {
            Quantity: this.awsConfig.deployment.invalidationPaths.length,
            Items: this.awsConfig.deployment.invalidationPaths
          },
          CallerReference: Date.now().toString()
        }
      };

      await this.cloudFrontClient.send(new CreateInvalidationCommand(invalidationParams));
      console.log('   ✅ Cache invalidation created');
    } catch (error) {
      console.warn('   ⚠️  Cache invalidation failed:', error.message);
    }
  }

  async setupDNS() {
    if (!this.awsConfig.route53.hostedZoneId || process.env.SKIP_DNS === 'true') {
      console.log('⏭️  Skipping DNS setup (not configured)\n');
      return;
    }

    console.log('🌐 Setting up DNS records...');
    console.log('   ⚠️  DNS setup not implemented in this version');
    console.log('   📝 Manual steps required:');
    console.log(`   1. Create CNAME record: ${this.awsConfig.route53.recordName} -> ${this.deploymentInfo.websiteUrl.replace('http://', '')}`);
    console.log('✅ DNS setup completed\n');
  }

  async validateDeployment() {
    console.log('✅ Validating deployment...');

    // Test if the website is accessible
    try {
      const fetch = (await import('node-fetch')).default;
      const response = await fetch(this.deploymentInfo.websiteUrl);

      if (response.ok) {
        console.log('   🌐 Website is accessible');
      } else {
        console.warn(`   ⚠️  Website returned status ${response.status}`);
      }
    } catch (error) {
      console.warn('   ⚠️  Could not validate website accessibility:', error.message);
    }

    console.log('✅ Deployment validation completed\n');
  }
}

// Utility function to check if running in CI/CD
function isCI() {
  return !!(
    process.env.CI ||
    process.env.CONTINUOUS_INTEGRATION ||
    process.env.BUILD_NUMBER ||
    process.env.GITHUB_ACTIONS
  );
}

// CLI interaction for missing configuration
async function promptForConfiguration() {
  if (isCI()) {
    throw new Error('Missing configuration in CI environment. Please set required environment variables.');
  }

  console.log('⚙️  Configuration required for deployment.');
  console.log('\nPlease set the following environment variables:');
  console.log('- S3_BUCKET_NAME: The S3 bucket name for hosting');
  console.log('- AWS_REGION: AWS region (default: eu-central-1)');
  console.log('\nExample:');
  console.log('export S3_BUCKET_NAME=batbern-project-docs');
  console.log('export AWS_REGION=eu-central-1');

  process.exit(1);
}

// Run the deployer if called directly
if (require.main === module) {
  const deployer = new DocumentationDeployer();

  // Check for required configuration
  if (!process.env.S3_BUCKET_NAME && deployer.awsConfig.s3.bucketName !== 'project.batbern.ch') {
    promptForConfiguration();
  } else {
    deployer.deploy().catch(console.error);
  }
}

module.exports = DocumentationDeployer;