// Fills the employees table with realistic dummy data.
//
//   npm run db:seed                     10,000 employees into backend/salary.db
//   npm run db:seed -- --count 500      a different number
//   npm run db:seed -- --reset          delete existing employees first
//   npm run db:seed -- --db path.db     a different database file
//   npm run db:seed -- --emit-sql f.sql write the same data as an SQL file instead (used to refresh the file the
//                                       backend bundles to load the demo data on first start when deployed)
//
// The backend must have started once before (it creates the tables). The same command always produces the
// same people, because the random numbers come from a fixed seed. This script is for development and the demo.
const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const path = require('node:path');

// ---------- options ----------

function readOptions(argv) {
  const options = { count: 10_000, db: path.resolve(__dirname, '..', 'backend', 'salary.db'), reset: false, emitSql: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--emit-sql') options.emitSql = path.resolve(argv[++i]);
    else if (argv[i] === '--count') options.count = Number(argv[++i]);
    else if (argv[i] === '--db') options.db = path.resolve(argv[++i]);
    else if (argv[i] === '--reset') options.reset = true;
    else fail(`Unknown option ${argv[i]}`);
  }
  if (!Number.isInteger(options.count) || options.count < 1) fail('--count must be a positive whole number');
  return options;
}

function fail(message) {
  console.error(`[seed] ERROR: ${message}`);
  process.exit(1);
}

// ---------- reference data ----------

// fx = local currency units per US dollar; payFactor = how pay levels compare with the US (1 = same).
const COUNTRIES = [
  { code: 'US', currency: 'USD', fx: 1, payFactor: 1.0, weight: 25, names: 'western', roundTo: 500 },
  { code: 'IN', currency: 'INR', fx: 83, payFactor: 0.3, weight: 20, names: 'indian', roundTo: 10_000 },
  { code: 'GB', currency: 'GBP', fx: 0.79, payFactor: 0.85, weight: 10, names: 'western', roundTo: 500 },
  { code: 'DE', currency: 'EUR', fx: 0.92, payFactor: 0.9, weight: 9, names: 'european', roundTo: 500 },
  { code: 'CA', currency: 'CAD', fx: 1.36, payFactor: 0.9, weight: 7, names: 'western', roundTo: 500 },
  { code: 'FR', currency: 'EUR', fx: 0.92, payFactor: 0.8, weight: 6, names: 'european', roundTo: 500 },
  { code: 'AU', currency: 'AUD', fx: 1.52, payFactor: 0.9, weight: 5, names: 'western', roundTo: 500 },
  { code: 'JP', currency: 'JPY', fx: 150, payFactor: 0.75, weight: 5, names: 'eastAsian', roundTo: 100_000 },
  { code: 'SG', currency: 'SGD', fx: 1.34, payFactor: 0.95, weight: 4, names: 'eastAsian', roundTo: 500 },
  { code: 'BR', currency: 'BRL', fx: 5.0, payFactor: 0.35, weight: 4, names: 'european', roundTo: 1_000 },
];

// Typical US salary in dollars for each job; local pay is worked out from this.
const DEPARTMENTS = [
  { name: 'Engineering', weight: 35, jobs: [
    ['Software Engineer', 95_000], ['Senior Software Engineer', 130_000], ['Staff Engineer', 170_000],
    ['Engineering Manager', 160_000], ['QA Engineer', 80_000], ['DevOps Engineer', 110_000], ['Data Engineer', 115_000] ] },
  { name: 'Sales', weight: 15, jobs: [
    ['Sales Representative', 60_000], ['Account Executive', 85_000], ['Sales Manager', 110_000] ] },
  { name: 'Operations', weight: 12, jobs: [
    ['Operations Analyst', 65_000], ['Operations Manager', 105_000], ['Logistics Coordinator', 50_000] ] },
  { name: 'Customer Support', weight: 10, jobs: [['Support Agent', 45_000], ['Support Team Lead', 65_000]] },
  { name: 'Marketing', weight: 8, jobs: [
    ['Marketing Specialist', 65_000], ['Content Strategist', 70_000], ['Marketing Manager', 100_000] ] },
  { name: 'Finance', weight: 8, jobs: [['Accountant', 65_000], ['Financial Analyst', 80_000], ['Finance Manager', 115_000]] },
  { name: 'Product', weight: 7, jobs: [['Product Manager', 125_000], ['Product Designer', 100_000], ['UX Researcher', 90_000]] },
  { name: 'Human Resources', weight: 5, jobs: [['HR Coordinator', 55_000], ['HR Business Partner', 85_000], ['Recruiter', 65_000]] },
];

const NAMES = {
  western: {
    first: ['James', 'Emma', 'Liam', 'Olivia', 'Noah', 'Sophia', 'Ethan', 'Ava', 'Lucas', 'Mia', 'Henry', 'Grace', 'Jack', 'Chloe'],
    last: ['Smith', 'Johnson', 'Brown', 'Taylor', 'Wilson', 'Clark', 'Walker', 'Hall', 'Young', 'King', 'Wright', 'Scott', 'Green', 'Baker'],
  },
  indian: {
    first: ['Aarav', 'Ananya', 'Vihaan', 'Diya', 'Arjun', 'Isha', 'Rohan', 'Priya', 'Kabir', 'Meera', 'Sai', 'Neha', 'Karan', 'Riya'],
    last: ['Sharma', 'Patel', 'Gupta', 'Reddy', 'Iyer', 'Nair', 'Singh', 'Kumar', 'Joshi', 'Mehta', 'Rao', 'Das', 'Bose', 'Verma'],
  },
  european: {
    first: ['Lukas', 'Marie', 'Hugo', 'Lea', 'Matteo', 'Camille', 'Felix', 'Julia', 'Thiago', 'Beatriz', 'Jonas', 'Chiara', 'Pedro', 'Sofia'],
    last: ['Muller', 'Schmidt', 'Dubois', 'Martin', 'Silva', 'Santos', 'Weber', 'Bernard', 'Costa', 'Fischer', 'Moreau', 'Pereira', 'Richter', 'Lopes'],
  },
  eastAsian: {
    first: ['Hiroshi', 'Yuki', 'Kenji', 'Sakura', 'Wei', 'Mei', 'Jun', 'Hana', 'Daichi', 'Lin', 'Haruto', 'Aiko', 'Ming', 'Rin'],
    last: ['Tanaka', 'Sato', 'Suzuki', 'Takahashi', 'Tan', 'Lim', 'Wong', 'Chen', 'Watanabe', 'Ito', 'Ng', 'Lee', 'Yamamoto', 'Goh'],
  },
};

const STATUSES = [['ACTIVE', 92], ['ON_LEAVE', 5], ['TERMINATED', 3]];

const TODAY = new Date('2026-10-01T00:00:00Z');   // fixed, so the same command always gives the same data
const FIRST_HIRE_DAY = new Date('2012-01-02T00:00:00Z');

// ---------- random numbers (fixed seed = repeatable data) ----------

/** mulberry32: a tiny, well-known seeded random number generator. */
function createRandom(seed) {
  let state = seed;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const random = createRandom(42);
const pick = (list) => list[Math.floor(random() * list.length)];

/** Picks one item, where each item has a weight (a bigger weight is picked more often). */
function pickWeighted(items, weightOf) {
  let ticket = random() * items.reduce((sum, item) => sum + weightOf(item), 0);
  for (const item of items) {
    ticket -= weightOf(item);
    if (ticket < 0) return item;
  }
  return items[items.length - 1];
}

// ---------- building one employee ----------

function buildEmployee(n) {
  const country = pickWeighted(COUNTRIES, (c) => c.weight);
  const department = pickWeighted(DEPARTMENTS, (d) => d.weight);
  const [jobTitle, usSalary] = pick(department.jobs);

  const pool = NAMES[country.names];
  const first = pick(pool.first);
  const last = pick(pool.last);

  const hireDate = new Date(FIRST_HIRE_DAY.getTime() + random() * (TODAY - FIRST_HIRE_DAY));
  const yearsEmployed = (TODAY - hireDate) / (365.25 * 24 * 3600 * 1000);

  // Pay = typical US pay, adjusted for the country, a little growth per year of service (up to 10), and some spread.
  let salary = usSalary * country.payFactor * country.fx;
  salary *= 1 + Math.min(yearsEmployed, 10) * 0.025;
  salary *= 0.9 + random() * 0.22;
  // About 2% are deliberate outliers (far too high or too low), so the insights have something to find.
  if (random() < 0.02) salary *= random() < 0.5 ? 1.6 : 0.65;

  return {
    fullName: `${first} ${last}`,
    email: `${first}.${last}${n}@acme.test`.toLowerCase(),   // the number keeps every email unique
    country: country.code,
    department: department.name,
    jobTitle,
    annualSalary: Math.max(country.roundTo, Math.round(salary / country.roundTo) * country.roundTo),
    currency: country.currency,
    hireDate: hireDate.toISOString().slice(0, 10),
    status: pickWeighted(STATUSES, (s) => s[1])[0],
  };
}

// ---------- database ----------

function openDatabase(file) {
  if (!fs.existsSync(file)) {
    fail(`Database file not found: ${file}\nStart the backend once first (npm run dev:local): it creates the tables.`);
  }
  const db = new DatabaseSync(file);
  // The same settings the backend uses. WAL lets the backend keep reading while this script writes.
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA busy_timeout = 5000');      // wait up to 5 s if the backend is writing at this moment
  db.exec('PRAGMA synchronous = NORMAL');
  db.exec('PRAGMA foreign_keys = ON');

  const table = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'employees'").get();
  if (!table) {
    fail('The employees table does not exist yet. Start the backend once (npm run dev:local) so it runs the migrations.');
  }
  return db;
}

/** Writes the employees as INSERT statements (500 rows each) so the backend can load them without Node. */
function emitSql(file, count) {
  const quote = (text) => `'${String(text).replace(/'/g, "''")}'`;
  const lines = [
    '-- Generated by scripts/seed-employees.js --emit-sql. Do not edit by hand; regenerate instead.',
    `-- ${count} employees, the same every time (fixed random seed).`,
  ];
  for (let start = 1; start <= count; start += 500) {
    const values = [];
    for (let n = start; n < start + 500 && n <= count; n++) {
      const e = buildEmployee(n);
      values.push(`(${[quote(e.fullName), quote(e.email), quote(e.country), quote(e.department), quote(e.jobTitle),
        e.annualSalary, quote(e.currency), quote(e.hireDate), quote(e.status)].join(', ')})`);
    }
    lines.push('INSERT INTO employees (full_name, email, country, department, job_title, annual_salary, currency, hire_date, status) VALUES',
      values.join(',\n') + ';');
  }
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, lines.join('\n') + '\n', 'utf8');
  console.log(`[seed] Wrote ${count} employees to ${file} (${Math.round(fs.statSync(file).size / 1024)} KB)`);
}

function main() {
  const { count, db: file, reset, emitSql: sqlFile } = readOptions(process.argv.slice(2));
  if (sqlFile) {
    emitSql(sqlFile, count);
    return;
  }
  console.log(`[seed] Database: ${file}`);
  const db = openDatabase(file);

  const existing = db.prepare('SELECT COUNT(*) AS n FROM employees').get().n;
  if (existing > 0 && !reset) {
    fail(`The table already has ${existing} employees. Use --reset to replace them.`);
  }

  const insert = db.prepare(`
    INSERT INTO employees (full_name, email, country, department, job_title, annual_salary, currency, hire_date, status)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`);

  const started = Date.now();
  // One transaction for all rows: much faster than 10,000 separate commits, and all-or-nothing.
  db.exec('BEGIN IMMEDIATE');
  try {
    if (reset) {
      console.log(`[seed] Removing ${existing} existing employees`);
      db.exec('DELETE FROM employees');
      db.exec("DELETE FROM sqlite_sequence WHERE name = 'employees'");   // ids start from 1 again
    }
    for (let n = 1; n <= count; n++) {
      const e = buildEmployee(n);
      insert.run(e.fullName, e.email, e.country, e.department, e.jobTitle, e.annualSalary, e.currency, e.hireDate, e.status);
      if (n % 2_500 === 0) console.log(`[seed] ... ${n} of ${count}`);
    }
    db.exec('COMMIT');
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
  console.log(`[seed] Inserted ${count} employees in ${Date.now() - started} ms`);

  summarise(db);
  db.close();
}

/** Prints a short summary so you can see the data looks sensible. */
function summarise(db) {
  console.log('[seed] Employees per country:');
  for (const row of db.prepare('SELECT country, currency, COUNT(*) AS n, MIN(annual_salary) AS low, MAX(annual_salary) AS high FROM employees GROUP BY country ORDER BY n DESC').all()) {
    console.log(`[seed]   ${row.country}  ${String(row.n).padStart(5)}  salaries ${row.low.toLocaleString('en-US')} to ${row.high.toLocaleString('en-US')} ${row.currency}`);
  }
  const mode = db.prepare('PRAGMA journal_mode').get().journal_mode;
  console.log(`[seed] Database journal mode: ${mode}`);
}

main();
