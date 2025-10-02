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
  ListDistributionsCommand,
  UpdateDistributionCommand
} = require('@aws-sdk/client-cloudfront');
const {
  Route53Client,
  ChangeResourceRecordSetsCommand,
  ListHostedZonesCommand,
  ListResourceRecordSetsCommand
} = require('@aws-sdk/client-route-53');
const {
  ACMClient,
  RequestCertificateCommand,
  DescribeCertificateCommand,
  ListCertificatesCommand
} = require('@aws-sdk/client-acm');

const config = require('../src/config/site-config');
const awsConfig = require('../src/config/aws-config');

class DocumentationDeployer {
  constructor() {
    this.config = config;
    this.awsConfig = awsConfig;
    this.s3Client = new S3Client({ region: awsConfig.region });
    this.cloudFrontClient = new CloudFrontClient({ region: 'us-east-1' }); // CloudFront is global
    this.route53Client = new Route53Client({ region: 'us-east-1' }); // Route53 is global
    this.acmClient = new ACMClient({ region: 'us-east-1' }); // ACM for CloudFront must be in us-east-1
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

      // Setup SSL Certificate
      await this.setupSSLCertificate();

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

  async setupSSLCertificate() {
    console.log('🔒 Setting up SSL certificate...');

    try {
      // Check if certificate already exists or use environment variable
      let certificateArn = this.awsConfig.ssl.certificateArn;

      if (!certificateArn) {
        // Look for existing certificate
        certificateArn = await this.findExistingCertificate();

        if (!certificateArn) {
          console.log('   🔨 Requesting new SSL certificate...');
          certificateArn = await this.requestCertificate();
        }
        // Note: findExistingCertificate already logs when it finds a certificate
      } else {
        console.log('   ✅ Using configured SSL certificate');
      }

      this.deploymentInfo.certificateArn = certificateArn;

      // Check certificate status
      try {
        const describeCommand = new DescribeCertificateCommand({
          CertificateArn: certificateArn
        });
        const certDetails = await this.acmClient.send(describeCommand);

        if (certDetails.Certificate.Status === 'PENDING_VALIDATION') {
          console.log('   ⚠️  Certificate is pending validation - DNS validation may be required');
          console.log('   📝 CloudFront distribution will be configured but will not work until certificate is validated');
        }
      } catch (error) {
        // Don't fail if we can't check status
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
      // Check for certificates in any status (ISSUED, PENDING_VALIDATION, etc.)
      const listCommand = new ListCertificatesCommand({
        CertificateStatuses: ['ISSUED', 'PENDING_VALIDATION']
      });

      const response = await this.acmClient.send(listCommand);

      if (response.CertificateSummaryList) {
        // Find certificate matching our domain
        for (const cert of response.CertificateSummaryList) {
          if (cert.DomainName === this.awsConfig.ssl.certificateDomain ||
              cert.DomainName === '*.batbern.ch') {
            // Verify certificate status
            const describeCommand = new DescribeCertificateCommand({
              CertificateArn: cert.CertificateArn
            });
            const certDetails = await this.acmClient.send(describeCommand);

            if (certDetails.Certificate.Status === 'ISSUED') {
              console.log(`   ✅ Found issued certificate for ${cert.DomainName}`);
              return cert.CertificateArn;
            } else if (certDetails.Certificate.Status === 'PENDING_VALIDATION') {
              console.log(`   ⏳ Found pending certificate for ${cert.DomainName} (awaiting validation)`);
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

    const requestParams = {
      DomainName: domain,
      ValidationMethod: this.awsConfig.ssl.validationMethod,
      SubjectAlternativeNames: [domain, ...this.awsConfig.ssl.alternativeNames],
      DomainValidationOptions: [{
        DomainName: domain,
        ValidationDomain: 'batbern.ch'
      }]
    };

    let certificateArn;
    try {
      const response = await this.acmClient.send(new RequestCertificateCommand(requestParams));
      certificateArn = response.CertificateArn;
    } catch (error) {
      // If certificate request fails, try to find existing certificate again
      if (error.name === 'LimitExceededException' || error.message.includes('already exists')) {
        console.warn('   ⚠️  Certificate request failed (may already exist):', error.message);
        console.log('   🔍 Searching for existing certificate...');

        // Try to find any existing certificate (including pending ones)
        certificateArn = await this.findExistingCertificate();

        if (!certificateArn) {
          throw new Error(`Unable to request or find existing certificate: ${error.message}`);
        }

        console.log('   ✅ Found existing certificate to use');
      } else {
        throw error;
      }
    }

    console.log('   ⏳ Waiting for certificate validation...');
    console.log('   📝 Certificate must be validated through DNS');

    // Wait for certificate to be ready for validation
    await new Promise(resolve => setTimeout(resolve, 5000));

    // Get validation records
    const describeCommand = new DescribeCertificateCommand({ CertificateArn: certificateArn });
    const certDetails = await this.acmClient.send(describeCommand);

    // Automatically create Route 53 validation records if we have access
    if (this.awsConfig.route53.hostedZoneId && certDetails.Certificate.DomainValidationOptions) {
      await this.createCertificateValidationRecords(certDetails.Certificate.DomainValidationOptions);
    }

    // Wait for certificate to be issued (with timeout)
    const maxWaitTime = 300000; // 5 minutes
    const startTime = Date.now();

    while (Date.now() - startTime < maxWaitTime) {
      const certStatus = await this.acmClient.send(describeCommand);

      if (certStatus.Certificate.Status === 'ISSUED') {
        console.log('   ✅ Certificate issued successfully');
        return certificateArn;
      } else if (certStatus.Certificate.Status === 'FAILED') {
        throw new Error('Certificate validation failed');
      }

      console.log(`   ⏳ Certificate status: ${certStatus.Certificate.Status}`);
      await new Promise(resolve => setTimeout(resolve, 10000)); // Wait 10 seconds
    }

    throw new Error('Certificate validation timeout - please complete DNS validation manually');
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
      const changeParams = {
        HostedZoneId: this.awsConfig.route53.hostedZoneId,
        ChangeBatch: {
          Comment: 'ACM certificate validation records',
          Changes: changes
        }
      };

      try {
        await this.route53Client.send(new ChangeResourceRecordSetsCommand(changeParams));
        console.log('   ✅ DNS validation records created');
      } catch (error) {
        console.warn('   ⚠️  Could not create validation records:', error.message);
        console.log('   📝 Please add the following DNS records manually:');

        for (const option of validationOptions) {
          if (option.ResourceRecord) {
            console.log(`      ${option.ResourceRecord.Name} -> ${option.ResourceRecord.Value}`);
          }
        }
      }
    }
  }

  async setupCloudFront() {
    if (!this.awsConfig.cloudfront.enabled || process.env.SKIP_CLOUDFRONT === 'true') {
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

        // Update distribution if certificate changed
        await this.updateDistributionCertificate(existingDistribution);

        // Create invalidation
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

  async createCloudFrontDistribution() {
    // Prepare the distribution configuration
    const config = JSON.parse(JSON.stringify(this.awsConfig.cloudfront.distributionConfig));

    // Set the S3 website endpoint as origin
    const s3WebsiteEndpoint = `${this.awsConfig.s3.bucketName}.s3-website.${this.awsConfig.s3.bucketRegion}.amazonaws.com`;
    config.Origins.Items[0].DomainName = s3WebsiteEndpoint;

    // Set the certificate ARN
    if (this.deploymentInfo.certificateArn) {
      config.ViewerCertificate.ACMCertificateArn = this.deploymentInfo.certificateArn;
    } else {
      // Use CloudFront default certificate if no custom certificate
      config.ViewerCertificate = {
        CloudFrontDefaultCertificate: true
      };
      // Remove custom domain aliases if using default certificate
      config.Aliases = { Quantity: 0, Items: [] };
    }

    // Set unique caller reference
    config.CallerReference = `batbern-docs-${Date.now()}`;

    const createCommand = new CreateDistributionCommand({
      DistributionConfig: config
    });

    const response = await this.cloudFrontClient.send(createCommand);
    const distribution = response.Distribution;

    console.log('   ⏳ Waiting for distribution to deploy (this may take 15-20 minutes)...');

    // Return immediately but note that distribution is still deploying
    this.deploymentInfo.distributionStatus = 'Deploying';

    return distribution;
  }

  async updateDistributionCertificate(distribution) {
    // Check if certificate needs updating
    const currentCertArn = distribution.ViewerCertificate?.ACMCertificateArn;
    const newCertArn = this.deploymentInfo.certificateArn;

    if (currentCertArn !== newCertArn && newCertArn) {
      console.log('   🔄 Updating distribution certificate...');

      try {
        // Get the current distribution config
        const getCommand = new GetDistributionCommand({ Id: distribution.Id });
        const distResponse = await this.cloudFrontClient.send(getCommand);

        const config = distResponse.Distribution.DistributionConfig;
        const etag = distResponse.ETag;

        // Update certificate
        config.ViewerCertificate = {
          ACMCertificateArn: newCertArn,
          SSLSupportMethod: 'sni-only',
          MinimumProtocolVersion: 'TLSv1.2_2021'
        };

        // Ensure aliases are set
        if (!config.Aliases || config.Aliases.Quantity === 0) {
          config.Aliases = {
            Quantity: 1,
            Items: ['project.batbern.ch']
          };
        }

        // Update the distribution
        const updateCommand = new UpdateDistributionCommand({
          Id: distribution.Id,
          DistributionConfig: config,
          IfMatch: etag
        });

        await this.cloudFrontClient.send(updateCommand);
        console.log('   ✅ Distribution certificate updated');
      } catch (error) {
        console.warn('   ⚠️  Could not update distribution certificate:', error.message);
      }
    }
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

    if (!this.deploymentInfo.distributionDomain) {
      console.log('⏭️  Skipping DNS setup (CloudFront distribution not available)\n');
      return;
    }

    console.log('🌐 Setting up DNS records...');

    try {
      // Check if record already exists
      const existingRecord = await this.checkExistingDNSRecord();

      if (existingRecord) {
        console.log('   ✅ DNS record already exists');

        // Update if pointing to wrong target
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
      console.log('   📝 Manual DNS configuration required:');
      console.log(`   1. Create A record (alias): ${this.awsConfig.route53.recordName} -> ${this.deploymentInfo.distributionDomain}`);
      console.log(`   2. Alias Hosted Zone ID: ${this.awsConfig.route53.aliasHostedZoneId}`);
    }
  }

  async checkExistingDNSRecord() {
    try {
      const listCommand = new ListResourceRecordSetsCommand({
        HostedZoneId: this.awsConfig.route53.hostedZoneId,
        StartRecordName: this.awsConfig.route53.recordName,
        StartRecordType: 'A',
        MaxItems: '1'
      });

      const response = await this.route53Client.send(listCommand);

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

    // Test if the website is accessible
    try {
      const fetch = (await import('node-fetch')).default;

      // Test S3 website endpoint
      const s3Response = await fetch(this.deploymentInfo.websiteUrl);
      if (s3Response.ok) {
        console.log('   ✅ S3 website endpoint is accessible');
      } else {
        console.warn(`   ⚠️  S3 website returned status ${s3Response.status}`);
      }

      // Test CloudFront distribution if available
      if (this.deploymentInfo.distributionDomain) {
        const cfUrl = `https://${this.deploymentInfo.distributionDomain}`;
        try {
          const cfResponse = await fetch(cfUrl);
          if (cfResponse.ok) {
            console.log('   ✅ CloudFront distribution is accessible');
          } else {
            console.warn(`   ⚠️  CloudFront returned status ${cfResponse.status}`);
          }
        } catch (error) {
          console.warn('   ⚠️  CloudFront not yet available (may still be deploying)');
        }
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