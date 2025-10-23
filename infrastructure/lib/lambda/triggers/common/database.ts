/**
 * Shared Database Connection Module for Lambda Triggers
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * This module provides connection pooling for Lambda functions that persist across invocations
 * (Lambda container reuse optimization)
 */

import { Client, Pool, PoolClient } from 'pg';
import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';

// Connection pool persists across Lambda invocations (warm start optimization)
let pool: Pool | null = null;
let secretsCache: { username: string; password: string } | null = null;

/**
 * Get database credentials from Secrets Manager
 * Credentials are cached across Lambda invocations for performance
 */
async function getDbCredentials(): Promise<{ username: string; password: string }> {
  // Return cached credentials if available
  if (secretsCache) {
    return secretsCache;
  }

  // Check if using Secrets Manager (CDK-deployed) or environment variables (local dev)
  if (process.env.DB_SECRET_ARN) {
    const secretsManager = new SecretsManagerClient({ region: process.env.AWS_REGION });
    const response = await secretsManager.send(
      new GetSecretValueCommand({ SecretId: process.env.DB_SECRET_ARN })
    );

    if (!response.SecretString) {
      throw new Error('Database secret not found or empty');
    }

    secretsCache = JSON.parse(response.SecretString);
    if (!secretsCache) {
      throw new Error('Failed to parse database credentials from Secrets Manager');
    }
    return secretsCache;
  }

  // Fallback to environment variables (local development)
  return {
    username: process.env.DB_USER || 'batbern_app',
    password: process.env.DB_PASSWORD || '',
  };
}

/**
 * Get database configuration from environment variables and Secrets Manager
 */
async function getDatabaseConfig() {
  const credentials = await getDbCredentials();

  return {
    host: process.env.DB_HOST || 'localhost',
    database: process.env.DB_NAME || 'batbern',
    user: credentials.username,
    password: credentials.password,
    port: parseInt(process.env.DB_PORT || '5432', 10),
    ssl: process.env.DB_SSL === 'false' ? false : { rejectUnauthorized: true }, // Default to SSL enabled
    // Connection pool settings optimized for Lambda
    max: 2, // Low max for Lambda (one concurrent execution per container)
    idleTimeoutMillis: 30000, // 30 seconds
    connectionTimeoutMillis: 5000, // 5 second timeout
  };
}

/**
 * Get a database client from the connection pool
 * Pool is reused across Lambda invocations for performance
 *
 * @returns {Promise<PoolClient>} Database client from pool with release() method
 */
export async function getDbClient(): Promise<PoolClient> {
  // Create pool if it doesn't exist (cold start)
  if (!pool) {
    console.log('Creating new database connection pool');
    const config = await getDatabaseConfig();
    pool = new Pool(config);

    // Handle pool errors
    pool.on('error', (err) => {
      console.error('Unexpected error on idle database client', err);
    });
  }

  // Get client from pool
  const client = await pool.connect();
  return client;
}

/**
 * Close the connection pool
 * Used for cleanup in tests or when Lambda container is shutting down
 */
export async function closePool(): Promise<void> {
  if (pool) {
    console.log('Closing database connection pool');
    await pool.end();
    pool = null;
  }
}

/**
 * Execute a query with automatic error handling and logging
 *
 * @param client Database client (PoolClient or Client)
 * @param query SQL query string
 * @param params Query parameters
 * @returns Query result
 */
export async function executeQuery(
  client: PoolClient | Client,
  query: string,
  params: any[] = []
): Promise<any> {
  try {
    const startTime = Date.now();
    const result = await client.query(query, params);
    const duration = Date.now() - startTime;

    console.log('Query executed successfully', {
      duration,
      rowCount: result.rowCount,
    });

    return result;
  } catch (error) {
    console.error('Database query failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      query: query.substring(0, 100), // Log first 100 chars only
    });
    throw error;
  }
}

/**
 * Execute queries in a transaction
 *
 * @param queries Array of {query, params} objects
 * @returns Array of query results
 */
export async function executeTransaction(
  queries: Array<{ query: string; params: any[] }>
): Promise<any[]> {
  const client = await getDbClient();

  try {
    await client.query('BEGIN');

    const results = [];
    for (const { query, params } of queries) {
      const result = await executeQuery(client, query, params);
      results.push(result);
    }

    await client.query('COMMIT');
    console.log('Transaction committed successfully');

    return results;
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('Transaction rolled back due to error', error);
    throw error;
  } finally {
    client.release();
  }
}
