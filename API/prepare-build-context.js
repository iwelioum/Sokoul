'use strict';

// Materialize active checkout bytes: BuildKit cannot read some Windows cloud placeholders.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { pathToFileURL } = require('node:url');

async function buildExtractorsFromCheckout(sourceRoot, output) {
  const script = path.resolve(sourceRoot, '../extraction/scripts/build-native-extractors.mjs');
  const { buildNativeExtractors } = await import(pathToFileURL(script).href);
  await buildNativeExtractors(output);
}

// buildExtractors is injectable so tests can stage a context without building the extraction bundle.
async function prepareBuildContext(destination, sourceRoot = __dirname, { buildExtractors = buildExtractorsFromCheckout } = {}) {
  const root = fs.mkdtempSync(path.join(destination, 'sokoul-api-build-'));
  const excluded = new Set(['node_modules', '.git', 'cache', '__pycache__', '.pytest_cache', 'tests', '__tests__', 'services_cache']);
  function copy(source, target) {
    fs.mkdirSync(target, { recursive: true });
    for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
      if (excluded.has(entry.name) || (entry.name.startsWith('.env') && entry.name !== '.env.example') || entry.name.endsWith('.log')) continue;
      const sourcePath = path.join(source, entry.name);
      const targetPath = path.join(target, entry.name);
      // Windows cloud reparse points can be reported as symlinks. Resolve them,
      // but never allow a real link to pull files from outside this API tree.
      const resolved = fs.realpathSync(sourcePath);
      const relative = path.relative(fs.realpathSync(sourceRoot), resolved);
      if (relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) throw new Error('Build context escapes the API source tree.');
      const stat = fs.statSync(sourcePath);
      if (stat.isDirectory()) copy(sourcePath, targetPath);
      else if (stat.isFile()) fs.writeFileSync(targetPath, fs.readFileSync(sourcePath));
    }
  }
  for (const service of ['Mainapi', 'proxiesembed', 'watchpartyAPI']) copy(path.join(sourceRoot, service), path.join(root, service));
  await buildExtractors(sourceRoot, path.join(root, 'Mainapi/generated/nativeExtractors'));
  return root;
}

if (require.main === module) {
  prepareBuildContext(os.tmpdir()).then((root) => console.log(root)).catch(() => {
    console.error('Cannot prepare backend Docker sources. Check source files and install the pinned extraction build dependencies with npm ci in extraction/.'); process.exitCode = 1;
  });
}
module.exports = { prepareBuildContext };
