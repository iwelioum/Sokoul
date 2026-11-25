import { fixtures } from '../../extraction/tests/fixtures.mjs';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
await mkdir(process.argv[2], { recursive: true });
await writeFile(path.join(process.argv[2], 'native-fixtures.json'), JSON.stringify(fixtures));
