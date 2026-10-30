const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/js/site/login.js'), 'utf8');

test('login follows the server-selected destination for dual teacher/admin accounts', () => {
  assert.match(source, /new URL\(response\.url, window\.location\.href\)/);
  assert.match(source, /destination\.pathname\}\$\{destination\.search\}\$\{destination\.hash\}/);
  assert.doesNotMatch(source, /window\.location\.href\s*=\s*next[^;]*\/dashboard/);
});
