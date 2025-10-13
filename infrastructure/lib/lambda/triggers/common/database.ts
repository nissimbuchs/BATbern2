/**
 * Shared Database Connection Module for Lambda Triggers
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * This module provides connection pooling for Lambda functions that persist across invocations
 * (Lambda container reuse optimization)
 */

import { Client, Pool, PoolClient } from 'pg';

// Connection pool persists across Lambda invocations (warm start optimization)
let pool: Pool | null = null;

/**
 * Get database configuration from environment variables
 */
function getDatabaseConfig() {
  return {
    host: process.env.DB_HOST || 'localhost',
    database: process.env.DB_NAME || 'batbern',
    user: process.env.DB_USER || 'batbern_app',
    password: process.env.DB_PASSWORD || '',
    port: parseInt(process.env.DB_PORT || '5432', 10),
    ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: true } : false,
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
    pool = new Pool(getDatabaseConfig());

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
