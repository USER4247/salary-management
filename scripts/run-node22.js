// Runs a script with Node 22+ (needed for the built-in SQLite module), using NODE22_HOME from .env.local if set.
// Usage: node scripts/run-node22.js <script> [arguments]
const { spawnSync } = require('node:child_process');
const { node22Binary } = require('./env');

const result = spawnSync(
  node22Binary(),
  ['--disable-warning=ExperimentalWarning', ...process.argv.slice(2)],
  { stdio: 'inherit' },
);
process.exit(result.status ?? 1);
