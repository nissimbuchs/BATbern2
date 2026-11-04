import { spawnSync } from 'child_process';

/**
 * Synchronous command runner. Injected into the fetchers so tests run offline.
 * Never goes through a shell: arguments are passed verbatim.
 * @returns {{status: number|null, stdout: string, stderr: string, error?: Error}}
 */
export function defaultRunner(cmd, args = [], opts = {}) {
  const res = spawnSync(cmd, args, {
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
    ...opts
  });
  return {
    status: res.status,
    stdout: res.stdout || '',
    stderr: res.stderr || '',
    error: res.error
  };
}

/** Short, single-line reason for a failed command, for notes on the dashboard. */
export function failureReason(res) {
  if (res.error) return res.error.message;
  const text = `${res.stderr || ''} ${res.stdout || ''}`.trim().split('\n').filter(Boolean).pop() || '';
  return `exit ${res.status}${text ? `: ${text.slice(0, 200)}` : ''}`;
}
