import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
const extractionDirectory=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export async function buildNativeExtractors(outputDirectory) {
  await mkdir(outputDirectory,{recursive:true});
  const service=path.resolve(extractionDirectory,'../API/Mainapi/services/nativeExtractors');
  await Promise.all(['index.cjs','worker.cjs'].map(name=>copyFile(path.join(service,name),path.join(outputDirectory,name))));
  await writeFile(path.join(outputDirectory,'extractors-core.cjs'),await readFile(path.join(extractionDirectory,'runtime/extractors-core.js'),'utf8'));
  await build({entryPoints:[path.join(extractionDirectory,'runtime/fsvid-vidzy-quickjs-entry.js')],outfile:path.join(outputDirectory,'quickjs.cjs'),bundle:true,format:'cjs',platform:'node',target:'node22',logLevel:'silent',define:{'import.meta.url':'__sokoulImportMetaUrl'},banner:{js:"const __sokoulImportMetaUrl = require('node:url').pathToFileURL(__filename).href;"}});
  await copyFile(path.join(extractionDirectory,'node_modules/@jitl/quickjs-wasmfile-release-sync/dist/emscripten-module.wasm'),path.join(outputDirectory,'quickjs.wasm'));
  await mkdir(path.join(outputDirectory,'licenses'),{recursive:true});
  for(const [pkg,name] of [['quickjs-emscripten-core','quickjs-emscripten-core'],['@jitl/quickjs-wasmfile-release-sync','quickjs-wasm']]) {
    await copyFile(path.join(extractionDirectory,'node_modules',pkg,'LICENSE'),path.join(outputDirectory,'licenses',name+'.txt'));
  }
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
  const index=process.argv.indexOf('--output');if(index<0||!process.argv[index+1])throw new Error('Supply --output <directory>');
  await buildNativeExtractors(path.resolve(process.argv[index+1]));
}
