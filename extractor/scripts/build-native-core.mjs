// Adapt the canonical browser algorithms to native QuickJS without shipping WASM or downloading code.
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
let quickjs = await readFile(path.join(root, 'extraction/runtime/fsvid-vidzy-quickjs-entry.js'), 'utf8');
quickjs = quickjs.replace(/^import RELEASE_SYNC[^;]+;\s*import\s*\{[\s\S]*?\}\s*from 'quickjs-emscripten-core';/, '');
const begin = quickjs.indexOf('async function getQuickJsModule() {');
const end = quickjs.indexOf('export function createBootstrap', begin);
if (begin < 0 || end < 0) throw new Error('Canonical QuickJS adapter boundary changed');
quickjs = quickjs.slice(0, begin) + 'async function getQuickJsModule() { return globalThis.__sokoulNativeQJS.getQuickJS(); }\nfunction shouldInterruptAfterDeadline(deadline) { return deadline; }\n' + quickjs.slice(end);
quickjs = quickjs.replace(/^export /gm, '');
const core = await readFile(path.join(root, 'extraction/runtime/extractors-core.js'), 'utf8');
const adapter = await readFile(path.join(here, '../src/main/resources/sokoul-extract/adapter.js'), 'utf8');
const output = path.join(process.argv[2], 'sokoul-extract');
await mkdir(output, { recursive: true });
await writeFile(path.join(output, 'canonical.js'), '(function(){\n' + quickjs + '\n})();\n' + core + '\n' + adapter);
// Export detector expressions as data so parsing a source list never starts a VM.
const boundary = '        EMBED_PATTERNS,';
if (!core.includes(boundary)) throw new Error('Canonical detection boundary changed');
const context = vm.createContext({ URL, setTimeout, clearTimeout });
vm.runInContext(core.replace(boundary, boundary + '\n        nativePatternDefinitions: { VOE_DOMAIN_PATTERN, VEEV_DOMAIN_PATTERN, DOODSTREAM_DOMAIN_PATTERN, LULUSTREAM_DOMAIN_PATTERN },'), context);
const detectors = context.SokoulExtractionCore.createExtractors({});
const patterns = {};
for (const [host, detector] of Object.entries(detectors.EMBED_PATTERNS)) {
  const source = String(detector);
  const named = /=>\s*(\w+)\.test\(url\)/.exec(source);
  const includes = /\.includes\('([^']+)'\)/.exec(source);
  if (named) patterns[host] = detectors.nativePatternDefinitions[named[1]].source;
  else if (includes) patterns[host] = includes[1].replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  else if (source.includes('=> /') && source.includes('/i.test(url)')) patterns[host] = source.slice(source.indexOf('/') + 1, source.lastIndexOf('/i.test(url)'));
  else if (host !== 'seekstreaming') throw new Error('Unknown canonical detector: ' + host);
}
await writeFile(path.join(output, 'hosts.json'), JSON.stringify(patterns));
