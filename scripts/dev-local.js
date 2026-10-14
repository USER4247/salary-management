// Starts the backend (Spring Boot) and the frontend (Angular dev server) together.
//
// If your default Node is older than 22.22.3, set NODE22_HOME in a gitignored `.env.local`
// (see `.env.example`); it is used for frontend processes only.
const { spawn, spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const { root, frontendEnv, backendEnv } = require('./env');

// `npm run dev:local:seed` passes --seed: also load the demo employees if the database is empty.
const seed = process.argv.includes('--seed');

const isWin = process.platform === 'win32';
const backendDir = path.join(root, 'backend');
const frontendDir = path.join(root, 'frontend');

// Prefix every output line with the process label, buffering partial lines between chunks.
function forward(label, stream, out) {
  let pending = '';
  stream.on('data', (chunk) => {
    const lines = (pending + chunk.toString()).split(/\r?\n/);
    pending = lines.pop();
    lines.filter(Boolean).forEach((line) => out.write(`[${label}] ${line}\n`));
  });
  stream.on('end', () => {
    if (pending) out.write(`[${label}] ${pending}\n`);
  });
}

function run(label, command, args, cwd, env = process.env) {
  const child = spawn(command, args, { cwd, env, shell: true });
  forward(label, child.stdout, process.stdout);
  forward(label, child.stderr, process.stderr);
  return child;
}

function kill(child) {
  if (!child.pid || child.exitCode !== null) return;
  if (isWin) spawnSync('taskkill', ['/pid', String(child.pid), '/t', '/f']);
  else child.kill('SIGTERM');
}

if (!fs.existsSync(path.join(frontendDir, 'node_modules'))) {
  console.log('[setup] Installing frontend dependencies (npm ci)...');
  const install = spawnSync('npm', ['ci'], { cwd: frontendDir, env: frontendEnv(), stdio: 'inherit', shell: true });
  if (install.status !== 0) process.exit(install.status ?? 1);
}

const wrapper = path.join(backendDir, isWin ? 'mvnw.cmd' : 'mvnw');
const backend = run('backend', `"${wrapper}"`, ['spring-boot:run'], backendDir, backendEnv({ seed }));
const frontend = run('frontend', 'npm', ['start'], frontendDir, frontendEnv());
const children = [backend, frontend];

console.log('Backend:  http://localhost:8080/actuator/health');
console.log('Frontend: http://localhost:4200');
if (seed) console.log('Demo employees will be loaded now if the database is empty (watch for "Seeded" in the backend log).');
console.log('Press Ctrl+C to stop both.');

let stopping = false;
function stopAll(code) {
  if (stopping) return;
  stopping = true;
  children.forEach(kill);
  process.exit(code);
}

children.forEach((child) => child.on('exit', (code) => stopAll(code ?? 0)));
process.on('SIGINT', () => stopAll(0));
process.on('SIGTERM', () => stopAll(0));
