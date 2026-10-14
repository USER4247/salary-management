// Deletes the local development database so the next start begins from scratch.
//
//   npm run db:reset                    delete backend/salary.db
//   npm run db:reset -- --db path.db    delete another database file
//
// Why this exists: the database file keeps everything, including any edits you made in the app and the demo data.
// To see the app start fresh (new tables, new demo accounts, freshly loaded employees) the simplest and safest way is to
// delete the file: the backend recreates the tables on its next start, and the demo data comes back from the seed.
// Stop the backend first, because a running backend keeps the file open.
//
// Fresh start in two steps:   npm run db:reset   then   npm run dev:local:seed
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function readDatabasePath(argv) {
  let file = path.join(root, 'backend', 'salary.db');
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--db') file = path.resolve(argv[++i] ?? '');
    else fail(`Unknown option ${argv[i]}`);
  }
  // A safety net: this script only ever deletes files that look like a database file.
  if (!file.endsWith('.db')) fail(`Refusing to delete ${file}: it does not end in .db`);
  return file;
}

function fail(message) {
  console.error(`[reset] ERROR: ${message}`);
  process.exit(1);
}

const file = readDatabasePath(process.argv.slice(2));
// SQLite in WAL mode keeps two companion files next to the database. They belong to it and must go with it.
const companions = [`${file}-wal`, `${file}-shm`];

if (![file, ...companions].some((f) => fs.existsSync(f))) {
  console.log(`[reset] Nothing to reset: ${file} does not exist.`);
  process.exit(0);
}

console.log(`[reset] Database: ${file}`);

// Delete the main file first. If it is locked (the backend is running) stop here without touching anything else:
// removing only the companion files of a database that is still in use could lose data that is not yet in the main file.
try {
  fs.rmSync(file, { force: true });
} catch (error) {
  if (['EBUSY', 'EPERM', 'EACCES'].includes(error.code)) {
    fail('The database is in use. Stop the backend first (Ctrl+C in the terminal running npm run dev:local), then run this again.');
  }
  throw error;
}
for (const companion of companions) {
  fs.rmSync(companion, { force: true });
}

console.log('[reset] Deleted. The next start creates the tables and the demo accounts again.');
console.log('[reset] To start fresh with demo employees:  npm run dev:local:seed');
