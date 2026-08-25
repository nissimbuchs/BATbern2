const fs = require('fs');
const os = require('os');
const path = require('path');

/**
 * Delete the `cdk.out*` temp directories this run leaked.
 *
 * `Template.fromStack()` / `new App()` without an explicit `outdir` calls
 * `mkdtempSync(os.tmpdir() + '/cdk.out')` and never removes the result. One full `test/unit` run
 * leaks ~196 directories / ~1.4 GB.
 *
 * That is merely untidy on a laptop. On rack it is a session-killer: `/tmp` there is a 15 GB
 * **tmpfs**, so those directories are RAM, and they are charged as `shmem` to a
 * `user.slice` cgroup capped at `MemoryMax=14G` (/etc/systemd/system/user.slice.d/50-memory.conf).
 * tmpfs pages cannot be reclaimed, only swapped, and swap is capped at 6 GB. Measured 2026-08-25:
 * 1805 leaked directories held 13 GB, so 6.7 GB of the 14 GB budget was gone before a single test
 * started, against 1.0 GB of actual process memory. jest was then OOM-killed (exit 137,
 * `constraint=CONSTRAINT_MEMCG`) and took the terminal session with it — while the machine still
 * had 12 GB free. Reaching for `--runInBand` or `--max-old-space-size` treats the symptom; the
 * cheap diagnosis is `df -h /tmp`.
 *
 * Two sweeps, deliberately:
 *   - directories touched during THIS run, identified by mtime against a stamp taken in
 *     global-setup, and
 *   - anything older than STALE_MS, which recovers what a crashed or killed run left behind —
 *     the case that produced the 1805.
 *
 * The gap between them is the safety margin: a `cdk` CLI invocation running concurrently in
 * another shell has a fresh mtime and is left alone.
 */
const STALE_MS = 60 * 60 * 1000;

module.exports = async () => {
  const tmp = os.tmpdir();
  const startedAt = Number(process.env.__CDK_TMP_SWEEP_START) || Date.now();
  const now = Date.now();

  let entries;
  try {
    entries = fs.readdirSync(tmp, { withFileTypes: true });
  } catch {
    return; // an unreadable tmpdir is not worth failing a green test run over
  }

  let removed = 0;
  let bytes = 0;

  for (const entry of entries) {
    // Exact prefix only. Never widen this to something like /^cdk/.
    if (!entry.isDirectory() || !entry.name.startsWith('cdk.out')) {
      continue;
    }

    const full = path.join(tmp, entry.name);
    try {
      const { mtimeMs } = fs.statSync(full);
      const isOurs = mtimeMs >= startedAt;
      const isStale = now - mtimeMs > STALE_MS;
      if (!isOurs && !isStale) {
        continue;
      }

      for (const f of fs.readdirSync(full)) {
        try {
          bytes += fs.statSync(path.join(full, f)).size;
        } catch {
          /* raced with something else removing it; the size is only for the log line */
        }
      }

      fs.rmSync(full, { recursive: true, force: true });
      removed += 1;
    } catch {
      /* another run may have removed it first — never fail the suite on cleanup */
    }
  }

  if (removed > 0) {
    const mb = (bytes / 1024 / 1024).toFixed(0);
    console.log(`\ncdk.out sweep: removed ${removed} temp dirs (~${mb} MB) from ${tmp}`);
  }
};
