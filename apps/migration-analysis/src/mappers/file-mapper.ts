/**
 * File Mapper - Maps legacy file paths to S3 bucket structure and CDN URLs
 * Handles all file types: company logos, profile pictures, presentations, event photos
 */

import { randomUUID } from 'crypto';
import * as path from 'path';
import { S3FileMapping } from '../types/target-types';

/**
 * Company logo mapping parameters
 */
export interface CompanyLogoParams {
  companyName: string;
  filename: string;
  year: number;
}

/**
 * Profile picture mapping parameters
 */
export interface ProfilePictureParams {
  username: string;
  filename: string;
  year: number;
}

/**
 * Presentation mapping parameters
 */
export interface PresentationParams {
  eventNumber: number;
  filename: string;
}

/**
 * Event photo mapping parameters
 */
export interface EventPhotoParams {
  eventNumber: number;
  filename: string;
}

/**
 * Generate S3 file mapping for company logo
 * Pattern: logos/{year}/companies/{companyName}/logo-{fileId}.{ext}
 * Reference: GenericLogoService (CompanyService.java:369)
 *
 * @param params - Company logo parameters
 * @returns S3 file mapping
 */
export function generateCompanyLogoMapping(params: CompanyLogoParams): S3FileMapping {
  const { companyName, filename, year } = params;

  const fileId = randomUUID();
  const extension = path.extname(filename);

  const s3Key = `logos/${year}/companies/${companyName}/logo-${fileId}${extension}`;
  const cloudfrontUrl = generateCloudFrontUrl(s3Key);

  return {
    s3Key,
    cloudfrontUrl,
    fileId,
    originalFilename: filename
  };
}

/**
 * Generate S3 file mapping for profile picture
 * Pattern: profile-pictures/{year}/{username}/profile-{fileId}.{ext}
 * Reference: ProfilePictureService.java:161
 *
 * @param params - Profile picture parameters
 * @returns S3 file mapping
 */
export function generateProfilePictureMapping(params: ProfilePictureParams): S3FileMapping {
  const { username, filename, year } = params;

  const fileId = randomUUID();
  const extension = path.extname(filename);

  const s3Key = `profile-pictures/${year}/${username}/profile-${fileId}${extension}`;
  const cloudfrontUrl = generateCloudFrontUrl(s3Key);

  return {
    s3Key,
    cloudfrontUrl,
    fileId,
    originalFilename: filename
  };
}

/**
 * Generate S3 file mapping for presentation PDF
 * Pattern: presentations/{eventNumber}/{filename}
 * Note: No year subdirectory for presentations (simpler structure)
 *
 * @param params - Presentation parameters
 * @returns S3 file mapping
 */
export function generatePresentationMapping(params: PresentationParams): S3FileMapping {
  const { eventNumber, filename } = params;

  const fileId = randomUUID(); // For tracking, even though filename is preserved

  const s3Key = `presentations/${eventNumber}/${filename}`;
  const cloudfrontUrl = generateCloudFrontUrl(s3Key);

  return {
    s3Key,
    cloudfrontUrl,
    fileId,
    originalFilename: filename
  };
}

/**
 * Generate S3 file mapping for event photo
 * Pattern: photos/events/{eventNumber}/{filename}
 * Note: No year subdirectory for event photos (simpler structure)
 *
 * @param params - Event photo parameters
 * @returns S3 file mapping
 */
export function generateEventPhotoMapping(params: EventPhotoParams): S3FileMapping {
  const { eventNumber, filename } = params;

  const fileId = randomUUID(); // For tracking

  const s3Key = `photos/events/${eventNumber}/${filename}`;
  const cloudfrontUrl = generateCloudFrontUrl(s3Key);

  return {
    s3Key,
    cloudfrontUrl,
    fileId,
    originalFilename: filename
  };
}

/**
 * Generate CloudFront URL from S3 key
 * Production/Staging: https://cdn.batbern.ch/{s3Key}
 *
 * @param s3Key - S3 object key
 * @returns CloudFront CDN URL
 */
export function generateCloudFrontUrl(s3Key: string): string {
  return `https://cdn.batbern.ch/${s3Key}`;
}

/**
 * Generate MinIO URL from S3 key (for local development)
 * Local: http://localhost:8450/{bucketName}/{s3Key}
 *
 * @param s3Key - S3 object key
 * @param bucketName - S3 bucket name (e.g., 'batbern-development-company-logos')
 * @returns MinIO URL for local development
 */
export function generateMinIOUrl(s3Key: string, bucketName: string): string {
  return `http://localhost:8450/${bucketName}/${s3Key}`;
}
