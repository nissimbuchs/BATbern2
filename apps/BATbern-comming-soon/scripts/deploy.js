#!/usr/bin/env node

import fs from 'fs-extra';
import path from 'path';
import { glob } from 'glob';
import mimeTypes from 'mime-types';
import { fromIni } from '@aws-sdk/credential-providers';
import {
  S3Client,
  CreateBucketCommand,
  PutBucketWebsiteCommand,
  PutBucketCorsCommand,
  PutBucketPolicyCommand,
  PutObjectCommand,
  HeadBucketCommand
} from '@aws-sdk/client-s3';
import {
  CloudFrontClient,
  CreateDistributionCommand,
  CreateInvalidationCommand,
  GetDistributionCommand,
  ListDistributionsCommand,
  UpdateDistributionCommand
} from '@aws-sdk/client-cloudfront';
import {
  Route53Client,
  ChangeResourceRecordSetsCommand,
  ListResourceRecordSetsCommand
} from '@aws-sdk/client-route-53';
import {
  ACMClient,
  RequestCertificateCommand,
  DescribeCertificateCommand,
  ListCertificatesCommand
} from '@aws-sdk/client-acm';

import awsConfig from '../src/config/aws-config.js';

// Set AWS profile for deployment
const AWS_PROFILE = process.env.AWS_PROFILE || 'batbern-prod';

class ComingSoonDeployer {
  constructor() {
    this.awsConfig = awsConfig;
    this.outputPath = 'out'; // Next.js static export output directory

    // Configure AWS credentials from profile
    const credentials = fromIni({ profile: AWS_PROFILE });

    this.s3Client = new S3Client({
      region: awsConfig.region,
      credentials
    });
    this.cloudFrontClient = new CloudFrontClient({
      region: 'us-east-1', // CloudFront is global
      credentials
    });
    this.route53Client = new Route53Client({
      region: 'us-east-1', // Route53 is global
      credentials
    });
    this.acmClient = new ACMClient({
      region: 'us-east-1', // ACM for CloudFront must be in us-east-1
      credentials
    });
    this.deploymentInfo = {};
  }

  async deploy() {
    console.log('🚀 BATbern Coming Soon Deployer');
    console.log('=================================\n');
    console.log(`🔑 Using AWS Profile: ${AWS_PROFILE}\n`);

    try {
      // Validate configuration
      await this.validateConfiguration();

      // Check if build exists
      await this.validateBuildOutput();

      // Deploy to S3
      await this.deployToS3();

      // Setup SSL Certificate
      await this.setupSSLCertificate();

      // Setup CloudFront
      await this.setupCloudFront();

      // Setup DNS
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
        console.log('   ✅ AWS credentials verified');
      } else if (error.name === 'CredentialsProviderError' || error.name === 'UnknownEndpoint') {
        throw new Error('AWS credentials not configured. Please run `aws configure` or set environment variables.');
      }
    }

    if (!this.awsConfig.s3.bucketName) {
      throw new Error('S3 bucket name not configured');
    }

    console.log(`   📦 Target bucket: ${this.awsConfig.s3.bucketName}`);
    console.log(`   🌍 AWS region: ${this.awsConfig.region}`);
    console.log('✅ Configuration validated\n');
  }

  async validateBuildOutput() {
    console.log('📁 Validating build output...');

    const distPath = path.resolve(this.outputPath);

    if (!await fs.pathExists(distPath)) {
      throw new Error(`Build output not found at ${distPath}. Please run 'npm run build' first.`);
    }

    const indexPath = path.join(distPath, 'index.html');
    if (!await fs.pathExists(indexPath)) {
      throw new Error('index.html not found in build output. Please run \'npm run build\' first.');
    }

    const files = await glob('**/*', { cwd: distPath, nodir: true });
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

        // Wait for bucket to be ready
        await new Promise(resolve => setTimeout(resolve, 2000));
      } else {
        throw error;
      }
    }
  }

  async configureBucketWebsite(bucketName) {
    console.log('   🌐 Configuring website hosting...');

    // Configure website hosting
    await this.s3Client.send(new PutBucketWebsiteCommand({
      Bucket: bucketName,
      WebsiteConfiguration: this.awsConfig.s3.websiteConfiguration
    }));

    // Configure CORS
    await this.s3Client.send(new PutBucketCorsCommand({
      Bucket: bucketName,
      CORSConfiguration: this.awsConfig.s3.corsConfiguration
    }));

    // Try to set bucket policy for public read access (will be skipped if Block Public Access is enabled)
    try {
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

      await this.s3Client.send(new PutBucketPolicyCommand({
        Bucket: bucketName,
        Policy: JSON.stringify(bucketPolicy)
      }));

      console.log('   ✅ Public bucket policy configured');
    } catch (error) {
      if (error.message && error.message.includes('BlockPublicPolicy')) {
        console.log('   ⚠️  Skipping public bucket policy (Block Public Access is enabled)');
        console.log('   ℹ️  CloudFront will access the bucket directly');
      } else {
        throw error;
      }
    }

    // Get the website URL
    const region = this.awsConfig.s3.bucketRegion;
    this.deploymentInfo.websiteUrl = `http://${bucketName}.s3-website.${region}.amazonaws.com`;

    console.log('   ✅ Website hosting configured');
  }

  async uploadFiles(bucketName) {
    console.log('   📤 Uploading files...');

    const distPath = path.resolve(this.outputPath);
    const files = await glob('**/*', { cwd: distPath, nodir: true });

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
      Key: filePath.replace(/\\/g, '/'),
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

  async setupSSLCertificate() {
    console.log('🔒 Setting up SSL certificate...');

    try {
      let certificateArn = this.awsConfig.ssl.certificateArn;

      if (!certificateArn) {
        certificateArn = await this.findExistingCertificate();

        if (!certificateArn) {
          console.log('   🔨 Requesting new SSL certificate...');
          certificateArn = await this.requestCertificate();
        }
      } else {
        console.log('   ✅ Using configured SSL certificate');
      }

      this.deploymentInfo.certificateArn = certificateArn;

      // Check certificate status
      try {
        const certDetails = await this.acmClient.send(
          new DescribeCertificateCommand({ CertificateArn: certificateArn })
        );

        if (certDetails.Certificate.Status === 'PENDING_VALIDATION') {
          console.log('   ⚠️  Certificate is pending validation');
          console.log('   📝 CloudFront distribution will be configured but will not work until certificate is validated');
        }
      } catch (error) {
        console.warn('   ⚠️  Could not verify certificate status:', error.message);
      }

      console.log(`   📜 Certificate ARN: ${certificateArn}`);
      console.log('✅ SSL certificate setup completed\n');

    } catch (error) {
      console.error('❌ SSL certificate setup failed:', error.message);
      throw error;
    }
  }

  async findExistingCertificate() {
    try {
      const response = await this.acmClient.send(
        new ListCertificatesCommand({
          CertificateStatuses: ['ISSUED', 'PENDING_VALIDATION']
        })
      );

      if (response.CertificateSummaryList) {
        for (const cert of response.CertificateSummaryList) {
          if (cert.DomainName === this.awsConfig.ssl.certificateDomain ||
              cert.DomainName === '*.batbern.ch') {
            const certDetails = await this.acmClient.send(
              new DescribeCertificateCommand({ CertificateArn: cert.CertificateArn })
            );

            if (certDetails.Certificate.Status === 'ISSUED') {
              console.log(`   ✅ Found issued certificate for ${cert.DomainName}`);
              return cert.CertificateArn;
            } else if (certDetails.Certificate.Status === 'PENDING_VALIDATION') {
              console.log(`   ⏳ Found pending certificate for ${cert.DomainName}`);
              return cert.CertificateArn;
            }
          }
        }
      }
    } catch (error) {
      console.warn('   ⚠️  Could not list certificates:', error.message);
    }

    return null;
  }

  async requestCertificate() {
    const domain = this.awsConfig.ssl.certificateDomain;

    const response = await this.acmClient.send(new RequestCertificateCommand({
      DomainName: domain,
      ValidationMethod: this.awsConfig.ssl.validationMethod,
      SubjectAlternativeNames: [domain, ...this.awsConfig.ssl.alternativeNames],
      DomainValidationOptions: [{
        DomainName: domain,
        ValidationDomain: 'batbern.ch'
      }]
    }));

    const certificateArn = response.CertificateArn;

    console.log('   ⏳ Waiting for certificate validation...');
    await new Promise(resolve => setTimeout(resolve, 5000));

    // Get validation records
    const certDetails = await this.acmClient.send(
      new DescribeCertificateCommand({ CertificateArn: certificateArn })
    );

    // Create Route 53 validation records
    if (this.awsConfig.route53.hostedZoneId && certDetails.Certificate.DomainValidationOptions) {
      await this.createCertificateValidationRecords(certDetails.Certificate.DomainValidationOptions);
    }

    return certificateArn;
  }

  async createCertificateValidationRecords(validationOptions) {
    console.log('   🔗 Creating DNS validation records...');

    const changes = [];

    for (const option of validationOptions) {
      if (option.ResourceRecord) {
        changes.push({
          Action: 'UPSERT',
          ResourceRecordSet: {
            Name: option.ResourceRecord.Name,
            Type: option.ResourceRecord.Type,
            TTL: 300,
            ResourceRecords: [{
              Value: option.ResourceRecord.Value
            }]
          }
        });
      }
    }

    if (changes.length > 0) {
      try {
        await this.route53Client.send(new ChangeResourceRecordSetsCommand({
          HostedZoneId: this.awsConfig.route53.hostedZoneId,
          ChangeBatch: {
            Comment: 'ACM certificate validation records',
            Changes: changes
          }
        }));
        console.log('   ✅ DNS validation records created');
      } catch (error) {
        console.warn('   ⚠️  Could not create validation records:', error.message);
      }
    }
  }

  async setupCloudFront() {
    if (!this.awsConfig.cloudfront.enabled) {
      console.log('⏭️  Skipping CloudFront setup\n');
      return;
    }

    console.log('🚀 Setting up CloudFront distribution...');

    try {
      const existingDistribution = await this.findExistingDistribution();

      if (existingDistribution) {
        console.log('   ✅ Using existing CloudFront distribution');
        this.deploymentInfo.distributionId = existingDistribution.Id;
        this.deploymentInfo.distributionDomain = existingDistribution.DomainName;

        await this.updateDistributionCertificate(existingDistribution);

        if (this.awsConfig.deployment.cacheInvalidation) {
          await this.createInvalidation(existingDistribution.Id);
        }
      } else {
        console.log('   🔨 Creating new CloudFront distribution...');
        const distribution = await this.createCloudFrontDistribution();
        this.deploymentInfo.distributionId = distribution.Id;
        this.deploymentInfo.distributionDomain = distribution.DomainName;
        console.log(`   ✅ CloudFront distribution created: ${distribution.DomainName}`);
      }

    } catch (error) {
      console.warn('   ⚠️  CloudFront setup failed:', error.message);
    }

    console.log('✅ CloudFront setup completed\n');
  }

  async findExistingDistribution() {
    try {
      const response = await this.cloudFrontClient.send(new ListDistributionsCommand({}));

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

  async createCloudFrontDistribution() {
    const config = JSON.parse(JSON.stringify(this.awsConfig.cloudfront.distributionConfig));

    // Set the S3 website endpoint as origin
    const s3WebsiteEndpoint = `${this.awsConfig.s3.bucketName}.s3-website.${this.awsConfig.s3.bucketRegion}.amazonaws.com`;
    config.Origins.Items[0].DomainName = s3WebsiteEndpoint;

    // Set the certificate ARN
    if (this.deploymentInfo.certificateArn) {
      config.ViewerCertificate.ACMCertificateArn = this.deploymentInfo.certificateArn;
    } else {
      config.ViewerCertificate = {
        CloudFrontDefaultCertificate: true
      };
      config.Aliases = { Quantity: 0, Items: [] };
    }

    const response = await this.cloudFrontClient.send(
      new CreateDistributionCommand({ DistributionConfig: config })
    );

    console.log('   ⏳ Distribution is deploying (may take 15-20 minutes)...');
    this.deploymentInfo.distributionStatus = 'Deploying';

    return response.Distribution;
  }

  async updateDistributionCertificate(distribution) {
    const currentCertArn = distribution.ViewerCertificate?.ACMCertificateArn;
    const newCertArn = this.deploymentInfo.certificateArn;

    if (currentCertArn !== newCertArn && newCertArn) {
      console.log('   🔄 Updating distribution certificate...');

      try {
        const distResponse = await this.cloudFrontClient.send(
          new GetDistributionCommand({ Id: distribution.Id })
        );

        const config = distResponse.Distribution.DistributionConfig;
        const etag = distResponse.ETag;

        config.ViewerCertificate = {
          ACMCertificateArn: newCertArn,
          SSLSupportMethod: 'sni-only',
          MinimumProtocolVersion: 'TLSv1.2_2021'
        };

        if (!config.Aliases || config.Aliases.Quantity === 0) {
          config.Aliases = {
            Quantity: 1,
            Items: ['batbern.ch']
          };
        }

        await this.cloudFrontClient.send(new UpdateDistributionCommand({
          Id: distribution.Id,
          DistributionConfig: config,
          IfMatch: etag
        }));

        console.log('   ✅ Distribution certificate updated');
      } catch (error) {
        console.warn('   ⚠️  Could not update distribution certificate:', error.message);
      }
    }
  }

  async createInvalidation(distributionId) {
    console.log('   🔄 Creating cache invalidation...');

    try {
      await this.cloudFrontClient.send(new CreateInvalidationCommand({
        DistributionId: distributionId,
        InvalidationBatch: {
          Paths: {
            Quantity: this.awsConfig.deployment.invalidationPaths.length,
            Items: this.awsConfig.deployment.invalidationPaths
          },
          CallerReference: Date.now().toString()
        }
      }));
      console.log('   ✅ Cache invalidation created');
    } catch (error) {
      console.warn('   ⚠️  Cache invalidation failed:', error.message);
    }
  }

  async setupDNS() {
    if (!this.awsConfig.route53.hostedZoneId) {
      console.log('⏭️  Skipping DNS setup\n');
      return;
    }

    if (!this.deploymentInfo.distributionDomain) {
      console.log('⏭️  Skipping DNS setup (CloudFront not available)\n');
      return;
    }

    console.log('🌐 Setting up DNS records...');

    try {
      const existingRecord = await this.checkExistingDNSRecord();

      if (existingRecord) {
        console.log('   ✅ DNS record already exists');

        if (existingRecord.AliasTarget?.DNSName !== `${this.deploymentInfo.distributionDomain}.`) {
          await this.updateDNSRecord();
        }
      } else {
        await this.createDNSRecord();
      }

      console.log('✅ DNS setup completed\n');
      console.log(`   🌐 Your site will be available at: https://${this.awsConfig.route53.recordName}`);

    } catch (error) {
      console.warn('   ⚠️  DNS setup failed:', error.message);
    }
  }

  async checkExistingDNSRecord() {
    try {
      const response = await this.route53Client.send(new ListResourceRecordSetsCommand({
        HostedZoneId: this.awsConfig.route53.hostedZoneId,
        StartRecordName: this.awsConfig.route53.recordName,
        StartRecordType: 'A',
        MaxItems: '1'
      }));

      if (response.ResourceRecordSets && response.ResourceRecordSets.length > 0) {
        const record = response.ResourceRecordSets[0];
        if (record.Name === `${this.awsConfig.route53.recordName}.`) {
          return record;
        }
      }
    } catch (error) {
      console.warn('   ⚠️  Could not check existing DNS records:', error.message);
    }

    return null;
  }

  async createDNSRecord() {
    console.log('   🔨 Creating DNS A record (alias)...');

    const changeParams = {
      HostedZoneId: this.awsConfig.route53.hostedZoneId,
      ChangeBatch: {
        Comment: 'Create alias record for CloudFront distribution',
        Changes: [{
          Action: 'CREATE',
          ResourceRecordSet: {
            Name: this.awsConfig.route53.recordName,
            Type: 'A',
            AliasTarget: {
              HostedZoneId: this.awsConfig.route53.aliasHostedZoneId,
              DNSName: this.deploymentInfo.distributionDomain,
              EvaluateTargetHealth: false
            }
          }
        }]
      }
    };

    // Also create AAAA record for IPv6
    const ipv6Change = JSON.parse(JSON.stringify(changeParams.ChangeBatch.Changes[0]));
    ipv6Change.ResourceRecordSet.Type = 'AAAA';
    changeParams.ChangeBatch.Changes.push(ipv6Change);

    await this.route53Client.send(new ChangeResourceRecordSetsCommand(changeParams));
    console.log('   ✅ DNS records created successfully');
  }

  async updateDNSRecord() {
    console.log('   🔄 Updating DNS A record (alias)...');

    const changeParams = {
      HostedZoneId: this.awsConfig.route53.hostedZoneId,
      ChangeBatch: {
        Comment: 'Update alias record for CloudFront distribution',
        Changes: [{
          Action: 'UPSERT',
          ResourceRecordSet: {
            Name: this.awsConfig.route53.recordName,
            Type: 'A',
            AliasTarget: {
              HostedZoneId: this.awsConfig.route53.aliasHostedZoneId,
              DNSName: this.deploymentInfo.distributionDomain,
              EvaluateTargetHealth: false
            }
          }
        }]
      }
    };

    // Also update AAAA record for IPv6
    const ipv6Change = JSON.parse(JSON.stringify(changeParams.ChangeBatch.Changes[0]));
    ipv6Change.ResourceRecordSet.Type = 'AAAA';
    changeParams.ChangeBatch.Changes.push(ipv6Change);

    await this.route53Client.send(new ChangeResourceRecordSetsCommand(changeParams));
    console.log('   ✅ DNS records updated successfully');
  }

  async validateDeployment() {
    console.log('✅ Validating deployment...');

    try {
      const fetch = (await import('node-fetch')).default;

      // Test S3 website endpoint
      const s3Response = await fetch(this.deploymentInfo.websiteUrl);
      if (s3Response.ok) {
        console.log('   ✅ S3 website endpoint is accessible');
      } else {
        console.warn(`   ⚠️  S3 website returned status ${s3Response.status}`);
      }

      // Show final URLs
      console.log('\n📌 Deployment URLs:');
      console.log(`   S3 Website: ${this.deploymentInfo.websiteUrl}`);
      if (this.deploymentInfo.distributionDomain) {
        console.log(`   CloudFront: https://${this.deploymentInfo.distributionDomain}`);
        console.log(`   Custom Domain: https://${this.awsConfig.route53.recordName}`);
      }

    } catch (error) {
      console.warn('   ⚠️  Could not validate website accessibility:', error.message);
    }

    console.log('\n✅ Deployment validation completed');
  }
}

// Run the deployer
if (import.meta.url === `file://${process.argv[1]}`) {
  const deployer = new ComingSoonDeployer();
  deployer.deploy().catch(console.error);
}

export default ComingSoonDeployer;
