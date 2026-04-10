import { chromium } from 'playwright';
import { access } from 'node:fs/promises';
import path from 'node:path';
import { startStaticServer } from './static-server.mjs';

const projectPath = process.argv[2];
const entryCandidate = process.argv[3] || 'index.html';

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

const server = await startStaticServer(normalizedProjectPath);

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

const url = `http://127.0.0.1:${server.port}/${entryCandidate.replace(/^\/+/, '')}`;

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
  await new Promise(resolve => server.server.close(resolve));
}
