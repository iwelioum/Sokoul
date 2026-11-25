'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { prepareBuildContext } = require('../prepare-build-context');

test('materialized build contexts preserve source bytes and exclude credentials and caches', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sokoul-context-test-'));
  try {
    for (const service of ['Mainapi', 'proxiesembed', 'watchpartyAPI']) {
      const source = path.join(root, 'source', service);
      fs.mkdirSync(path.join(source, 'node_modules'), { recursive: true });
      fs.mkdirSync(path.join(source, 'nested'));
      fs.writeFileSync(path.join(source, 'Dockerfile'), 'FROM fixture\n# café 日本語\n');
      fs.writeFileSync(path.join(source, '.env'), 'SECRET=do-not-copy');
      fs.writeFileSync(path.join(source, 'nested', '.env.production'), 'SECRET=do-not-copy');
      fs.writeFileSync(path.join(source, '.env.example'), 'SECRET=');
      fs.writeFileSync(path.join(source, 'node_modules', 'private.txt'), 'do-not-copy');
    }
    const built = [];
    const staged = await prepareBuildContext(root, path.join(root, 'source'), { buildExtractors: async (_source, output) => built.push(output) });
    assert.deepEqual(built, [path.join(staged, 'Mainapi/generated/nativeExtractors')]);
    for (const service of ['Mainapi', 'proxiesembed', 'watchpartyAPI']) {
      assert.equal(fs.readFileSync(path.join(staged, service, 'Dockerfile'), 'utf8'), 'FROM fixture\n# café 日本語\n');
      assert.ok(fs.existsSync(path.join(staged, service, '.env.example')));
      assert.ok(!fs.existsSync(path.join(staged, service, '.env')));
      assert.ok(!fs.existsSync(path.join(staged, service, 'nested', '.env.production')));
      assert.ok(!fs.existsSync(path.join(staged, service, 'node_modules')));
    }
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
