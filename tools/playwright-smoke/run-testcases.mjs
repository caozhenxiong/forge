#!/usr/bin/env node

import { chromium } from 'playwright';
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

async function main() {
  const snapshotMode = process.argv[2] === '--snapshot';
  const planPath = snapshotMode ? process.argv[3] : process.argv[2];
  const rootDirArg = snapshotMode ? process.argv[4] : process.argv[3];
  if (!planPath || !rootDirArg) {
    console.error(JSON.stringify({ error: 'usage: run-testcases.mjs <plan.json> <projectDir>' }));
    process.exit(2);
  }

  const raw = await readFile(planPath, 'utf8');
  const plan = JSON.parse(raw);
  const firstCase = snapshotMode ? { entry: plan.entry || 'index.html' } : (plan.cases || [])[0];
  if (!firstCase?.entry) {
    console.error(JSON.stringify({ error: 'missing entry in test cases' }));
    process.exit(2);
  }

  const rootDir = path.resolve(rootDirArg);
  const server = await startStaticServer(rootDir);
  const browser = await chromium.launch({ headless: true });

  try {
    if (snapshotMode) {
      const snapshot = await captureSnapshot(browser, server.port, firstCase.entry);
      console.log(JSON.stringify(snapshot, null, 2));
      process.exit(0);
    }
    const results = [];
    for (const testCase of plan.cases || []) {
      results.push(await runCase(browser, server.port, testCase));
    }
    const failed = results.some((item) => item.required && !item.passed);
    console.log(JSON.stringify({ cases: results }, null, 2));
    process.exit(failed ? 1 : 0);
  } finally {
    await browser.close();
    await new Promise((resolve) => server.server.close(resolve));
  }
}

async function captureSnapshot(browser, port, entry) {
  const page = await browser.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });
  page.on('pageerror', (error) => {
    pageErrors.push(String(error));
  });
  try {
    await page.goto(`http://127.0.0.1:${port}/${entry}`, { waitUntil: 'load', timeout: 15000 });
    return {
      pageTitle: await page.title(),
      pageLoadMs: await readPageLoadMs(page),
      canvasCount: await page.locator('canvas').count(),
      selectors: await page.evaluate(() => {
        const collected = new Set(['body']);
        for (const element of document.querySelectorAll('button, input, canvas, main, [id], [class]')) {
          const tag = element.tagName.toLowerCase();
          if (element.id) {
            collected.add(`#${element.id}`);
          }
          if (tag === 'button') {
            collected.add('button');
          }
          if (tag === 'input') {
            collected.add('input');
          }
          if (tag === 'canvas') {
            collected.add('canvas');
          }
          for (const className of Array.from(element.classList || []).slice(0, 2)) {
            collected.add(`.${className}`);
          }
        }
        return Array.from(collected).sort();
      }),
      consoleErrors,
      pageErrors
    };
  } finally {
    await page.close();
  }
}

function startStaticServer(rootDir) {
  return new Promise((resolve, reject) => {
    const server = http.createServer(async (req, res) => {
      try {
        const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
        const targetPath = path.resolve(rootDir, '.' + (urlPath === '/' ? '/index.html' : urlPath));
        if (!targetPath.startsWith(path.resolve(rootDir))) {
          res.writeHead(403);
          res.end('forbidden');
          return;
        }
        const body = await readFile(targetPath);
        res.writeHead(200, { 'Content-Type': contentType(targetPath) });
        res.end(body);
      } catch {
        res.writeHead(404);
        res.end('not found');
      }
    });
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      resolve({ server, port: address.port });
    });
    server.on('error', reject);
  });
}

async function runCase(browser, port, testCase) {
  const page = await browser.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  const measurements = {};
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });
  page.on('pageerror', (error) => {
    pageErrors.push(String(error));
  });

  const details = [];
  let passed = true;
  let status = 'PASSED';
  let failureReason = '';
  try {
    const entry = testCase.entry || 'index.html';
    await page.goto(`http://127.0.0.1:${port}/${entry}`, { waitUntil: 'load', timeout: 15000 });
    measurements.pageLoadMs = await readPageLoadMs(page);
    for (const step of testCase.steps || []) {
      try {
        const note = await runStep(page, step, consoleErrors, pageErrors, measurements);
        details.push(`PASS ${step.action}${step.selector ? ' ' + step.selector : ''}${step.key ? ' ' + step.key : ''}${note ? ' | ' + note : ''}`);
      } catch (error) {
        if (step.optional) {
          details.push(`SKIP ${step.action}: ${error.message}`);
          continue;
        }
        passed = false;
        failureReason = classifyFailure(step, error.message);
        status = failureReason === 'missing-runtime-element' || failureReason === 'setup-failure' ? 'BLOCKED' : 'FAILED';
        details.push(`FAIL ${step.action}: ${error.message}`);
        break;
      }
    }
    if (consoleErrors.length) {
      details.push(`consoleErrors: ${consoleErrors.join(' | ')}`);
    }
    if (pageErrors.length) {
      details.push(`pageErrors: ${pageErrors.join(' | ')}`);
    }
  } catch (error) {
    passed = false;
    failureReason = 'navigation-failure';
    status = 'BLOCKED';
    details.push(`FAIL case: ${error.message}`);
  } finally {
    await page.close();
  }

  return {
    id: testCase.id,
    title: testCase.title,
    required: Boolean(testCase.required),
    status,
    passed,
    details: details.join('\n'),
    failureReason,
    evidence: details.join('\n')
  };
}

function classifyFailure(step, message) {
  if ((step.action === 'ASSERT_SELECTOR' || step.action === 'CLICK') && /selector|waiting for/i.test(message)) {
    return 'missing-runtime-element';
  }
  if (step.action === 'ASSERT_NO_ERRORS') {
    return 'runtime-error';
  }
  if (step.action === 'PRESS_KEY' || step.action === 'CLICK') {
    return 'interaction-failure';
  }
  if (step.action === 'MEASURE_PAGE_LOAD_MAX_MS' || step.action === 'ASSERT_WINDOW_METRIC_MAX_MS') {
    return 'performance-threshold-exceeded';
  }
  return 'assertion-failure';
}

async function runStep(page, step, consoleErrors, pageErrors, measurements) {
  switch (step.action) {
    case 'ASSERT_SELECTOR': {
      await page.waitForSelector(step.selector, { timeout: 5000, state: 'attached' });
      return '';
    }
    case 'ASSERT_CANVAS_MIN': {
      const count = await page.locator('canvas').count();
      if (count < (step.count || 1)) {
        throw new Error(`expected at least ${(step.count || 1)} canvas, got ${count}`);
      }
      return `canvas=${count}`;
    }
    case 'CLICK': {
      await page.click(step.selector, { timeout: 5000 });
      return '';
    }
    case 'PRESS_KEY': {
      await page.keyboard.press(step.key, { delay: 20 });
      return '';
    }
    case 'WAIT': {
      await page.waitForTimeout(step.ms || 150);
      return `waited=${step.ms || 150}ms`;
    }
    case 'ASSERT_NO_ERRORS': {
      if (consoleErrors.length || pageErrors.length) {
        throw new Error(`runtime errors detected: ${consoleErrors.concat(pageErrors).join(' | ')}`);
      }
      return '';
    }
    case 'ASSERT_TEXT_CONTAINS': {
      if (step.selector) {
        const text = await page.locator(step.selector).innerText({ timeout: 5000 });
        if (!text.includes(step.text || '')) {
          throw new Error(`selector text does not contain "${step.text}"`);
        }
      } else {
        const bodyText = await page.locator('body').innerText({ timeout: 5000 });
        if (!bodyText.includes(step.text || '')) {
          throw new Error(`body text does not contain "${step.text}"`);
        }
      }
      return `contains="${step.text || ''}"`;
    }
    case 'MEASURE_PAGE_LOAD_MAX_MS': {
      const measured = measurements.pageLoadMs ?? await readPageLoadMs(page);
      if (measured == null) {
        throw new Error('page load timing unavailable');
      }
      measurements.pageLoadMs = measured;
      if (step.ms != null && measured > step.ms) {
        throw new Error(`page load ${measured}ms exceeds limit ${step.ms}ms`);
      }
      return `pageLoad=${measured}ms limit=${step.ms ?? 'n/a'}ms`;
    }
    case 'ASSERT_WINDOW_METRIC_MAX_MS': {
      if (!step.text) {
        throw new Error('metric key is required in step.text');
      }
      const measured = await readWindowMetric(page, step.text);
      if (measured == null || Number.isNaN(measured)) {
        throw new Error(`window.__devflowMetrics.${step.text} is unavailable or not numeric`);
      }
      if (step.ms != null && measured > step.ms) {
        throw new Error(`metric ${step.text}=${measured}ms exceeds limit ${step.ms}ms`);
      }
      return `${step.text}=${measured}ms limit=${step.ms ?? 'n/a'}ms`;
    }
    default:
      throw new Error(`unsupported action ${step.action}`);
  }
}

async function readPageLoadMs(page) {
  return page.evaluate(() => {
    const nav = performance.getEntriesByType('navigation')[0];
    if (nav && Number.isFinite(nav.loadEventEnd) && nav.loadEventEnd > 0) {
      return Math.round(nav.loadEventEnd);
    }
    const timing = performance.timing;
    if (timing && timing.loadEventEnd && timing.navigationStart) {
      return Math.max(0, timing.loadEventEnd - timing.navigationStart);
    }
    return null;
  });
}

async function readWindowMetric(page, metricPath) {
  return page.evaluate((requestedPath) => {
    const root = window.__devflowMetrics;
    if (!root || typeof requestedPath !== 'string' || !requestedPath.trim()) {
      return null;
    }
    const value = requestedPath
      .split('.')
      .filter(Boolean)
      .reduce((current, part) => (current == null ? undefined : current[part]), root);
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
  }, metricPath);
}

function contentType(filePath) {
  if (filePath.endsWith('.html')) return 'text/html; charset=utf-8';
  if (filePath.endsWith('.js')) return 'application/javascript; charset=utf-8';
  if (filePath.endsWith('.css')) return 'text/css; charset=utf-8';
  return 'application/octet-stream';
}

main().catch((error) => {
  console.error(JSON.stringify({ error: error.message }, null, 2));
  process.exit(1);
});
