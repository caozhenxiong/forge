#!/usr/bin/env node

import { chromium } from 'playwright';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { startStaticServer } from './static-server.mjs';

async function main() {
  const probeMode = process.argv[2] === '--probe';
  const planPath = probeMode ? process.argv[3] : process.argv[2];
  const rootDirArg = probeMode ? process.argv[4] : process.argv[3];
  if (!planPath || !rootDirArg) {
    emitAndExit(2, {
      status: 'usage_error',
      probe: null,
      errors: ['usage: run-testcases.mjs [--probe] <plan.json> <projectDir>'],
      cases: [],
    });
    return;
  }

  let raw;
  try {
    raw = await readFile(planPath, 'utf8');
  } catch (error) {
    emitAndExit(1, {
      status: 'plan_read_failed',
      probe: null,
      errors: [String(error?.message || error)],
      cases: [],
    });
    return;
  }

  let plan;
  try {
    plan = JSON.parse(raw);
  } catch (error) {
    emitAndExit(1, {
      status: 'plan_invalid',
      probe: null,
      errors: [String(error?.message || error)],
      cases: [],
    });
    return;
  }

  const firstCase = probeMode
    ? { entry: plan.entry || (plan.cases || [])[0]?.entry }
    : (plan.cases || [])[0];
  if (!firstCase?.entry) {
    emitAndExit(2, {
      status: 'missing_entry',
      probe: null,
      errors: ['missing entry in test cases'],
      cases: [],
    });
    return;
  }

  const rootDir = path.resolve(rootDirArg);
  const server = await startStaticServer(rootDir);
  const browser = await chromium.launch({ headless: true });

  try {
    const probe = await probePage(browser, server.port, firstCase.entry);
    if (probeMode) {
      emitAndExit(0, {
        status: 'ok',
        probe,
        errors: [],
        cases: [],
      });
      return;
    }

    const results = [];
    for (const testCase of plan.cases || []) {
      results.push(await runCase(browser, server.port, testCase));
    }
    const failed = results.some((item) => item.required && !item.passed);
    emitAndExit(failed ? 1 : 0, {
      status: failed ? 'required_cases_failed' : 'ok',
      probe,
      errors: [],
      cases: results,
    });
  } catch (error) {
    emitAndExit(1, {
      status: 'probe_failed',
      probe: null,
      errors: [String(error?.message || error)],
      cases: [],
    });
  } finally {
    await browser.close();
    await new Promise((resolve) => server.server.close(resolve));
  }
}

function emitAndExit(code, payload) {
  console.log(JSON.stringify(payload, null, 2));
  process.exit(code);
}

async function probePage(browser, port, entry) {
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
    await page.waitForTimeout(1000);
    const domProbe = await page.evaluate(() => {
      function selectorForElement(element) {
        if (element === document.body) {
          return 'body';
        }
        if (element.id) {
          return `#${element.id}`;
        }
        if (element.classList && element.classList.length > 0) {
          return `.${Array.from(element.classList)[0]}`;
        }
        if (element instanceof HTMLCanvasElement) {
          return 'canvas';
        }
        if (element.tagName && element.tagName.toLowerCase() === 'main') {
          return 'main';
        }
        return '';
      }

      function captureSurfaceCandidates() {
        const seen = new Set();
        const candidates = [];
        const all = [document.body, ...document.querySelectorAll('canvas, main, [id], [class]')];
        for (const element of all) {
          if (!(element instanceof HTMLElement)) {
            continue;
          }
          if (element.tagName && ['BUTTON', 'INPUT', 'TEXTAREA', 'SCRIPT', 'STYLE', 'LINK', 'META'].includes(element.tagName)) {
            continue;
          }
          const selector = selectorForElement(element);
          if (!selector || seen.has(selector)) {
            continue;
          }
          const rect = element.getBoundingClientRect();
          const area = Math.max(0, Math.round(rect.width * rect.height));
          if (area <= 0) {
            continue;
          }
          const visible = rect.width > 0 && rect.height > 0 && window.getComputedStyle(element).display !== 'none';
          if (!visible) {
            continue;
          }
          const mode = element instanceof HTMLCanvasElement ? 'CANVAS_HASH' : 'DOM_SIGNATURE';
          seen.add(selector);
          candidates.push({ selector, mode, area });
        }
        return candidates.sort((left, right) => right.area - left.area);
      }

      function captureControlCandidates() {
        const seen = new Set();
        const candidates = [];
        for (const element of document.querySelectorAll('button, input[type="button"], input[type="submit"], [role="button"]')) {
          if (!(element instanceof HTMLElement)) {
            continue;
          }
          const selector = selectorForElement(element);
          if (!selector || seen.has(selector)) {
            continue;
          }
          const rect = element.getBoundingClientRect();
          if (rect.width <= 0 || rect.height <= 0) {
            continue;
          }
          seen.add(selector);
          candidates.push({
            selector,
            text: (element.innerText || element.getAttribute('value') || '').replace(/\s+/g, ' ').trim(),
          });
        }
        return candidates;
      }

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

      const root = window.__devflowMetrics;
      const exposedMetricKeys = !root || typeof root !== 'object' || Array.isArray(root)
        ? []
        : Object.keys(root).filter((key) => typeof key === 'string' && key.trim()).sort();

      return {
        selectors: Array.from(collected).sort(),
        surfaceCandidates: captureSurfaceCandidates(),
        controlCandidates: captureControlCandidates(),
        exposedMetricKeys,
      };
    });

    return {
      pageTitle: await page.title(),
      pageLoadMs: await readPageLoadMs(page),
      bodyTextLength: ((await page.locator('body').textContent()) || '').trim().length,
      canvasCount: await page.locator('canvas').count(),
      surfaceCandidates: domProbe.surfaceCandidates,
      controlCandidates: domProbe.controlCandidates,
      selectors: domProbe.selectors,
      exposedMetricKeys: domProbe.exposedMetricKeys,
      consoleErrors,
      pageErrors,
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
    evidence: details.join('\n'),
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
      html: element.outerHTML || '',
    };
  }, selector || null);
  if (!snapshot) {
    throw new Error(`DOM selector ${selector || 'body'} is unavailable`);
  }
  return crypto.createHash('sha256').update(JSON.stringify(snapshot)).digest('hex');
}

main().catch((error) => {
  emitAndExit(1, {
    status: 'executor_failed',
    probe: null,
    errors: [String(error?.message || error)],
    cases: [],
  });
});
