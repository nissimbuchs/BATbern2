import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as path from 'path';
import { Construct } from 'constructs';

/**
 * Creates a container image using a hybrid approach:
 * - If IMAGE_TAG environment variable is set (CI/CD): uses pre-built ECR image
 * - Otherwise (bootstrap, local dev): builds from Dockerfile
 *
 * This solves the bootstrap problem where images don't exist yet on first deployment.
 *
 * @param scope - CDK construct scope
 * @param id - Unique ID for the ECR repository construct (e.g., 'ServiceRepository', 'ApiGatewayRepository')
 * @param serviceName - Name of the service (e.g., 'company-user-management', 'api-gateway')
 * @param envName - Environment name (e.g., 'staging', 'production')
 * @param dockerfilePath - Path to Dockerfile relative to project root (e.g., 'services/company-user-management-service/Dockerfile', 'api-gateway/Dockerfile')
 * @returns ECS ContainerImage
 */
export function createContainerImage(
  scope: Construct,
  id: string,
  serviceName: string,
  envName: string,
  dockerfilePath: string
): ecs.ContainerImage {
  const imageTag = process.env.IMAGE_TAG;

  if (imageTag) {
    // CI/CD mode: Use pre-built image from ECR
    console.log(`Using pre-built ECR image for ${serviceName}: tag=${imageTag}`);
    const repository = ecr.Repository.fromRepositoryName(
      scope,
      id,
      `batbern/${envName}/${serviceName}`
    );
    return ecs.ContainerImage.fromEcrRepository(repository, imageTag);
  } else {
    // Bootstrap/local mode: Build from Dockerfile
    console.log(`Building Docker image for ${serviceName} from source`);
    // Path goes from utils/ up to lib/, then up to infrastructure/, then up to project root
    return ecs.ContainerImage.fromAsset(path.join(__dirname, '../../..'), {
      file: dockerfilePath,
    });
  }
}
