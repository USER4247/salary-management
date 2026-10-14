// Runs an npm command inside frontend/ with the right Node: node scripts/frontend.js <npm args>
const { spawnSync } = require('node:child_process');
const path = require('node:path');
const { root, frontendEnv } = require('./env');

const result = spawnSync('npm', process.argv.slice(2), {
  cwd: path.join(root, 'frontend'),
  env: frontendEnv(),
  stdio: 'inherit',
  shell: true,
});
process.exit(result.status ?? 1);
