const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/resources');
const files = [];
function collect(directory) {
  for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) collect(full);
    else if (/\.(html|js)$/.test(entry.name)) files.push(full);
  }
}
collect(root);

test('production resources contain no inline handlers or static inline scripts', () => {
  const violations = [];
  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8');
    if (/<script\s*>/i.test(source)) violations.push(`${file}: inline script`);
    if (/\bon(?:click|change|input|submit|load|error|keydown|keyup)\s*=/.test(source)) {
      violations.push(`${file}: inline event handler`);
    }
    if (/\.(?:onclick|onchange|oninput|onsubmit|onload|onerror|onkeydown|onkeyup)\s*=/.test(source)) {
      violations.push(`${file}: DOM0 handler`);
    }
  }
  assert.deepEqual(violations, []);
});

test('strict CSP keywords are not introduced', () => {
  const source = files.map(file => fs.readFileSync(file, 'utf8')).join('\n');
  assert.doesNotMatch(source, /['"]unsafe-(?:inline|eval)['"]/i);
});
