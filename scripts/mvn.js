// Runs the Maven wrapper for the current OS: node scripts/mvn.js <maven args>
const { spawnSync } = require('node:child_process');
const path = require('node:path');

const wrapper = `"${path.resolve(process.cwd(), process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')}"`;
const result = spawnSync(wrapper, process.argv.slice(2), { stdio: 'inherit', shell: true });
process.exit(result.status ?? 1);
