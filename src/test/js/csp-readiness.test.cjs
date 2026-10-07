const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/resources');
const resourceFiles = [];
function collect(directory) {
  for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) collect(full);
    else resourceFiles.push(full);
  }
}
collect(root);

const textFiles = resourceFiles.filter(file => /\.(html|js|css|json)$/.test(file));
const htmlFiles = resourceFiles.filter(file => /\.html$/.test(file));
const read = file => fs.readFileSync(file, 'utf8');

test('production resources contain no strict-CSP blockers', () => {
  const violations = [];
  for (const file of textFiles) {
    const source = read(file);
    const label = path.relative(root, file);
    if (/<script\b(?![^>]*\bsrc\s*=)[^>]*>/i.test(source)) violations.push(`${label}: inline script`);
    if (/<style\b[^>]*>/i.test(source)) violations.push(`${label}: inline style`);
    if (/\.\s*on[a-z][\w:-]*\s*=/i.test(source)) violations.push(`${label}: DOM0 handler`);
    if (/\.html$/.test(file) && /\bon[a-z][\w:-]*\s*=/i.test(source)) violations.push(`${label}: inline event handler`);
    if (/\bjavascript\s*:/i.test(source)) violations.push(`${label}: javascript URL`);
    if (/\beval\s*\(|\bnew\s+Function\s*\(/.test(source)) violations.push(`${label}: eval/new Function`);
    if (/\bset(?:Timeout|Interval)\s*\(\s*['"`]/.test(source)) violations.push(`${label}: string timer`);
    if (/\.html$/.test(file) && /\bstyle\s*=\s*["']/i.test(source)) violations.push(`${label}: style attribute`);
    if (/\bsetAttribute\s*\(\s*['"]style['"]\s*,/i.test(source)) violations.push(`${label}: setAttribute(style)`);
    if (/\bstyle\.cssText\b/i.test(source)) violations.push(`${label}: style.cssText`);
  }
  assert.deepEqual(violations, []);
});

test('external browser resources are same-origin and not data/blob URLs', () => {
  const violations = [];
  for (const file of htmlFiles) {
    const source = read(file);
    for (const match of source.matchAll(/<(?:script|link|img|iframe|frame|object|embed)\b[^>]*(?:src|href|data)\s*=\s*["']([^"']+)["'][^>]*>/gi)) {
      const value = match[1].trim();
      if (/^(?:data|blob|javascript):/i.test(value) || /^[a-z][a-z\d+.-]*:/i.test(value)) {
        violations.push(`${path.relative(root, file)}: ${value}`);
      }
    }
  }
  assert.deepEqual(violations, []);
});

test('login resources are public, externalized, and versioned', () => {
  const login = read(path.join(root, 'templates/html/login.html'));
  const paths = JSON.parse(read(path.join(root, 'meta/paths/get_paths.json')));
  assert.doesNotMatch(login, /<style\b/i);
  assert.doesNotMatch(login, /<script\b(?![^>]*\bsrc\s*=)[^>]*>/i);
  assert.match(login, /<link\b[^>]*href=["']\/login\.css["']/i);
  assert.match(login, /<script\b[^>]*src=["']\/login\.js["']/i);
  for (const route of ['/login.js', '/login.css']) {
    assert.deepEqual(paths[route], {
      type: 'GET', handler_type: 'FileRequestHandler', namespaces: ['site'],
      context: route.endsWith('.css') ? 'css' : 'js', access_level: 'public'
    });
  }
  assert.equal(fs.existsSync(path.join(root, 'css/site/login.css')), true);
  assert.equal(fs.existsSync(path.join(root, 'js/site/login.js')), true);
});

test('429 page is readable without inline or external presentation resources', () => {
  const page = read(path.join(root, 'html/errors/429.html'));
  assert.match(page, /429/);
  assert.match(page, /Too Many Requests/);
  assert.doesNotMatch(page, /<style\b|<script\b|\bstyle\s*=/i);
  assert.doesNotMatch(page, /<(?:link|img|iframe|frame|object|embed)\b/i);
});

test('strict CSP keywords are not introduced', () => {
  const source = textFiles.map(file => read(file)).join('\n');
  assert.doesNotMatch(source, /['"]unsafe-(?:inline|eval)['"]/i);
});
