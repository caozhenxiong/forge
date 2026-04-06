import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { access } from 'node:fs/promises';
import path from 'node:path';

const projectPath = process.argv[2];
const entryCandidate = process.argv[3] || 'index.html';
const port = Number(process.argv[4] || 41731);

if (!projectPath) {
  console.error('Missing project path');
  process.exit(2);
}

const normalizedProjectPath = path.resolve(projectPath);
const entryPath = path.resolve(normalizedProjectPath, entryCandidate);
try {
  await access(entryPath);
} catch {
  console.error(`Missing entry file: ${entryCandidate}`);
  process.exit(2);
}

const server = spawn('python3', ['-m', 'http.server', String(port), '--bind', '127.0.0.1'], {
  cwd: normalizedProjectPath,
  stdio: ['ignore', 'ignore', 'pipe']
});

let serverReady = false;
let serverError = '';
server.stderr.on('data', chunk => {
  serverError += chunk.toString();
});

const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
for (let i = 0; i < 20; i++) {
  if (server.exitCode !== null) {
    console.error(`HTTP server exited early: ${serverError}`);
    process.exit(2);
  }
  await wait(150);
  serverReady = true;
}

if (!serverReady) {
  console.error('HTTP server did not become ready');
  process.exit(2);
}

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage();
const consoleErrors = [];
const pageErrors = [];

page.on('console', message => {
  if (message.type() === 'error') {
    consoleErrors.push(message.text());
  }
});
page.on('pageerror', error => {
  pageErrors.push(error.stack || String(error));
});

const url = `http://127.0.0.1:${port}/${entryCandidate.replace(/^\/+/, '')}`;

try {
  await page.goto(url, { waitUntil: 'load', timeout: 15000 });
  await page.waitForTimeout(1000);
  const canvasCount = await page.locator('canvas').count();
  const bodyTextLength = ((await page.locator('body').textContent()) || '').trim().length;
  const result = {
    url,
    canvasCount,
    bodyTextLength,
    consoleErrors,
    pageErrors
  };

  if (consoleErrors.length > 0 || pageErrors.length > 0) {
    console.error(JSON.stringify(result, null, 2));
    process.exit(1);
  }

  console.log(JSON.stringify(result, null, 2));
} finally {
  await browser.close();
  server.kill('SIGTERM');
}
