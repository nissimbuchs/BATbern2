/**
 * Records when the run started, so global-teardown can tell the temp directories THIS run
 * created from ones a concurrent process may still be using.
 *
 * See test/global-teardown.js for why this exists at all.
 */
module.exports = async () => {
  process.env.__CDK_TMP_SWEEP_START = String(Date.now());
};
