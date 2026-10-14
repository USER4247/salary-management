// Shared helpers for the dev scripts.
//
// Reads optional machine-specific settings from a gitignored `.env.local` at the repo root
// (see `.env.example`). NODE22_HOME, if set, is put first on the PATH for frontend processes
// so a newer Node can be used without changing the global one.
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function loadLocalEnv() {
  const file = path.join(root, '.env.local');
  if (!fs.existsSync(file)) return {};
  const values = {};
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (match && !line.trim().startsWith('#')) values[match[1]] = match[2].replace(/^["']|["']$/g, '');
  }
  return values;
}

function frontendEnv() {
  const env = { ...process.env };
  const node22 = env.NODE22_HOME || loadLocalEnv().NODE22_HOME;
  if (node22) {
    const pathKey = Object.keys(env).find((k) => k.toLowerCase() === 'path') ?? 'PATH';
    env[pathKey] = node22 + path.delimiter + env[pathKey];
  }
  return env;
}

module.exports = { root, frontendEnv };
