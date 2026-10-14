// One-time setup: checks that the tools are installed, then installs what the project needs.
//
//   npm run setup
//
// What each part needs:
//   Backend  (Java + Spring Boot): nothing to install by hand. The Maven wrapper (backend/mvnw) downloads Maven itself,
//            and Maven downloads every library listed in backend/pom.xml. This script triggers that once.
//   Frontend (Angular): `npm ci` installs exactly the packages recorded in frontend/package-lock.json.
//   Root scripts: no packages at all.
//
// `npm run dev:local` also installs the frontend packages if they are missing, so setup is optional but makes the first
// start quick and shows any problem early.
const { spawnSync } = require('node:child_process');
const path = require('node:path');
const { root, frontendEnv, node22Binary } = require('./env');

const isWin = process.platform === 'win32';
let problems = 0;

// ---------- 1. check the tools ----------

/** Angular 22 needs Node 22.22.3+, or 24.15.0+, or 26+. */
function nodeIsNewEnough(version) {
  const [major, minor, patch] = version.replace(/^v/, '').split('.').map(Number);
  if (major === 22) return minor > 22 || (minor === 22 && patch >= 3);
  if (major === 24) return minor >= 15;
  return major >= 26;
}

function check(label, ok, found, fix) {
  console.log(`  ${ok ? 'OK ' : 'MISSING'}  ${label}: ${found}`);
  if (!ok) {
    problems++;
    console.log(`           -> ${fix}`);
  }
}

function javaVersion() {
  const result = spawnSync('java', ['-version'], { encoding: 'utf8', shell: true });
  const match = /version "(\d+)[."]?/.exec(`${result.stderr}${result.stdout}`);   // e.g. version "21.0.2" or "25.0.2"
  return match ? Number(match[1]) : null;
}

console.log('[setup] Checking the tools you need...');
const java = javaVersion();
check('Java (JDK 21 or newer)', java !== null && java >= 21, java === null ? 'not found' : `version ${java}`,
  'Install a JDK 21 or newer (for example Eclipse Temurin) and make sure `java -version` works.');

const node = spawnSync(node22Binary(), ['--version'], { encoding: 'utf8' }).stdout?.trim() ?? '';
check('Node.js (22.22.3 or newer)', nodeIsNewEnough(node), node || 'not found',
  'Install Node 22.22.3+ or, to keep your current Node, copy .env.example to .env.local and set NODE22_HOME to a newer Node folder.');

if (problems > 0) {
  console.error(`\n[setup] ${problems} tool(s) missing. Fix them and run \`npm run setup\` again.`);
  process.exit(1);
}

// ---------- 2. install ----------

function run(title, command, args, cwd, env = process.env) {
  console.log(`\n[setup] ${title}`);
  const started = Date.now();
  const result = spawnSync(command, args, { cwd, env, stdio: 'inherit', shell: true });
  if (result.status !== 0) {
    console.error(`\n[setup] FAILED: ${title}. See the messages above.`);
    process.exit(result.status ?? 1);
  }
  console.log(`[setup] done in ${Math.round((Date.now() - started) / 1000)} s`);
}

run('Frontend: installing Angular packages (npm ci)', 'npm', ['ci'], path.join(root, 'frontend'), frontendEnv());

// Building the backend once downloads Maven, the Spring libraries and the build plugins, and proves the code compiles.
const wrapper = path.join(root, 'backend', isWin ? 'mvnw.cmd' : 'mvnw');
run('Backend: downloading Maven and the Spring libraries, and building once (skipping tests)',
  `"${wrapper}"`, ['-B', '-q', '-DskipTests', 'package'], path.join(root, 'backend'));

console.log('\n[setup] All set. Next:');
console.log('  npm run dev:local:seed   start the app with 10,000 demo employees  ->  http://localhost:4200');
console.log('  npm test                 run all tests');
