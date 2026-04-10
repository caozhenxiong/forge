#!/usr/bin/env node

import { chromium } from 'playwright';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { startStaticServer } from './static-server.mjs';

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
  const firstCase = snapshotMode
    ? { entry: plan.entry || (plan.cases || [])[0]?.entry }
    : (plan.cases || [])[0];
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
      exposedMetricKeys: await page.evaluate(() => {
        const root = window.__devflowMetrics;
        if (!root || typeof root !== 'object' || Array.isArray(root)) {
          return [];
        }
        return Object.keys(root).filter((key) => typeof key === 'string' && key.trim()).sort();
      }),
      consoleErrors,
      pageErrors
    };
  } finally {
    await page.close();
  }
}

async function runCase(browser, port, testCase) {
  const page = await browser.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  const measurements = {};
  const observations = new Map();
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
    const entry = testCase.entry;
    if (!entry) {
      throw new Error('missing entry in testcase');
    }
    await page.goto(`http://127.0.0.1:${port}/${entry}`, { waitUntil: 'load', timeout: 15000 });
    measurements.pageLoadMs = await readPageLoadMs(page);
    for (const step of testCase.steps || []) {
      try {
        const note = await runStep(page, step, consoleErrors, pageErrors, measurements, observations);
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

async function runStep(page, step, consoleErrors, pageErrors, measurements, observations) {
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
    case 'SNAPSHOT_CANVAS_HASH': {
      const key = step.text || 'canvas';
      const hash = await readCanvasHash(page, step.selector);
      observations.set(key, hash);
      return `snapshot=${key}`;
    }
    case 'ASSERT_CANVAS_HASH_CHANGED': {
      const key = step.text || 'canvas';
      const before = observations.get(key);
      if (!before) {
        throw new Error(`missing recorded canvas snapshot ${key}`);
      }
      const current = await readCanvasHash(page, step.selector);
      if (before === current) {
        throw new Error(`canvas snapshot ${key} did not change after interaction`);
      }
      return `snapshot=${key} changed`;
    }
    case 'SNAPSHOT_DOM_SIGNATURE': {
      const key = step.text || 'dom';
      const signature = await readDomSignature(page, step.selector);
      observations.set(key, signature);
      return `snapshot=${key}`;
    }
    case 'ASSERT_DOM_SIGNATURE_CHANGED': {
      const key = step.text || 'dom';
      const before = observations.get(key);
      if (!before) {
        throw new Error(`missing recorded DOM snapshot ${key}`);
      }
      const current = await readDomSignature(page, step.selector);
      if (before === current) {
        throw new Error(`DOM snapshot ${key} did not change after interaction`);
      }
      return `snapshot=${key} changed`;
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

async function readCanvasHash(page, selector) {
  const dataUrl = await page.evaluate((requestedSelector) => {
    const canvas = requestedSelector ? document.querySelector(requestedSelector) : document.querySelector('canvas');
    if (!(canvas instanceof HTMLCanvasElement)) {
      return null;
    }
    return canvas.toDataURL();
  }, selector || null);
  if (!dataUrl) {
    throw new Error(`canvas ${selector || 'canvas'} is unavailable`);
  }
  return crypto.createHash('sha256').update(dataUrl).digest('hex');
}

async function readDomSignature(page, selector) {
  const snapshot = await page.evaluate((requestedSelector) => {
    const element = requestedSelector ? document.querySelector(requestedSelector) : document.body;
    if (!element) {
      return null;
    }
    return {
      text: (element.innerText || '').replace(/\s+/g, ' ').trim(),
      childCount: element.childElementCount,
      className: element.className || '',
      html: element.outerHTML || ''
    };
  }, selector || null);
  if (!snapshot) {
    throw new Error(`DOM selector ${selector || 'body'} is unavailable`);
  }
  return crypto.createHash('sha256').update(JSON.stringify(snapshot)).digest('hex');
}

main().catch((error) => {
  console.error(JSON.stringify({ error: error.message }, null, 2));
  process.exit(1);
});
