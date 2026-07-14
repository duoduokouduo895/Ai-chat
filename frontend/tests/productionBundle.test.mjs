import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const jsDir = path.resolve(testDir, '../dist/js');

assert.ok(fs.existsSync(jsDir), 'dist/js does not exist; run the production build first');

const chunkFiles = fs.readdirSync(jsDir).filter((file) => file.endsWith('.js'));
const chunkSet = new Set(chunkFiles);
const graph = new Map();
const staticImportPatterns = [
  /\b(?:import|export)[^;]*?\bfrom\s*["']\.\/([^"']+\.js)["']/g,
  /\bimport\s*["']\.\/([^"']+\.js)["']/g
];

for (const file of chunkFiles) {
  const source = fs.readFileSync(path.join(jsDir, file), 'utf8');
  const imports = [];
  for (const pattern of staticImportPatterns) {
    for (const match of source.matchAll(pattern)) {
      if (chunkSet.has(match[1])) {
        imports.push(match[1]);
      }
    }
  }
  graph.set(file, imports);
}

const visiting = new Set();
const visited = new Set();
const stack = [];

const visit = (file) => {
  if (visiting.has(file)) {
    const cycleStart = stack.indexOf(file);
    const cycle = [...stack.slice(cycleStart), file];
    assert.fail(`Production bundle contains a static import cycle: ${cycle.join(' -> ')}`);
  }
  if (visited.has(file)) {
    return;
  }

  visiting.add(file);
  stack.push(file);
  for (const dependency of graph.get(file) ?? []) {
    visit(dependency);
  }
  stack.pop();
  visiting.delete(file);
  visited.add(file);
};

for (const file of chunkFiles) {
  visit(file);
}

for (const [file, imports] of graph.entries()) {
  if (file.startsWith('network-')) {
    assert.ok(
      imports.every((dependency) => !dependency.startsWith('apiClient-')),
      `Axios network chunk must not import its consumer apiClient chunk: ${file}`
    );
  }
}

console.log(`production_bundle_test=PASS chunks=${chunkFiles.length}`);
