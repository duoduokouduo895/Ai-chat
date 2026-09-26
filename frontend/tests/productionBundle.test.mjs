import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const testDir = path.dirname(fileURLToPath(import.meta.url));
const distDir = path.resolve(testDir, '../dist');
const jsDir = path.join(distDir, 'js');
const assetsDir = path.join(distDir, 'assets');

const parseCfHeaders = (text) => {
  const rules = [];
  let current = null;

  for (const rawLine of text.split(/\r?\n/)) {
    if (!rawLine.trim() || rawLine.trim().startsWith('#')) {
      continue;
    }

    if (/^[ \t]/.test(rawLine)) {
      const idx = rawLine.indexOf(':');
      assert.ok(current, `header line has no path: ${rawLine}`);
      current.headers[rawLine.slice(0, idx).trim().toLowerCase()] = rawLine.slice(idx + 1).trim();
      continue;
    }

    current = { path: rawLine.trim(), headers: {} };
    rules.push(current);
  }

  return rules;
};

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

// 首屏关键路径：入口与工作区路由都不应静态依赖 Markdown 渲染器，UI 库已移除
const reachable = (start) => {
  const seen = new Set();
  const walk = (file) => {
    if (seen.has(file)) return;
    seen.add(file);
    for (const dependency of graph.get(file) ?? []) walk(dependency);
  };
  walk(start);
  return seen;
};
const entryChunk = chunkFiles.find((file) => file.startsWith('index-'));
const workspaceChunk = chunkFiles.find((file) => file.startsWith('MarketingPlatform-'));
assert.ok(entryChunk && workspaceChunk, 'entry and workspace chunks must exist');
for (const start of [entryChunk, workspaceChunk]) {
  const staticDeps = [...reachable(start)];
  assert.equal(
    staticDeps.some((file) => file.startsWith('markdown-')),
    false,
    `${start} must load the markdown renderer lazily, got: ${staticDeps.join(', ')}`
  );
}
const introChunk = chunkFiles.find((file) => file.startsWith('AsciiIntro-'));
assert.ok(introChunk, 'intro engine must be split into its own lazy chunk');
for (const start of [entryChunk, workspaceChunk]) {
  assert.equal(reachable(start).has(introChunk), false, `${start} must not statically load the intro engine`);
}
assert.equal(chunkFiles.some((file) => file.startsWith('ui-libs-')), false, 'element-plus chunk should be gone');
for (const file of chunkFiles) {
  const source = fs.readFileSync(path.join(jsDir, file), 'utf8');
  assert.equal(/fortawesome|svg-inline--fa|axios/i.test(source), false, `Font Awesome / axios code found in ${file}`);
}
for (const file of chunkFiles) {
  const source = fs.readFileSync(path.join(jsDir, file), 'utf8');
  assert.equal(/el-select|ElMessageBox|element-plus/.test(source), false, `Element Plus code found in ${file}`);
}

const hashedJs = /^[A-Za-z0-9_-]+-[A-Za-z0-9_-]+\.js$/;
for (const file of chunkFiles) {
  assert.match(file, hashedJs, `JS chunk must be content-hashed: ${file}`);
}

assert.ok(fs.existsSync(assetsDir), 'dist/assets does not exist; run the production build first');
const assetFiles = fs.readdirSync(assetsDir);
assert.ok(assetFiles.some((file) => /^[A-Za-z0-9_-]+-[A-Za-z0-9_-]+\.css$/.test(file)), 'hashed CSS assets are missing');

const builtIndex = fs.readFileSync(path.join(distDir, 'index.html'), 'utf8');
assert.match(builtIndex, /\/js\/index-[A-Za-z0-9_-]+\.js/);
assert.match(builtIndex, /\/assets\/index-[A-Za-z0-9_-]+\.css/);
assert.ok(builtIndex.includes('id="startup-shell"'));
assert.ok(builtIndex.includes('<div id="app"></div>'));
assert.ok(builtIndex.indexOf('<div id="app"></div>') < builtIndex.indexOf('id="startup-shell"'));

const headersPath = path.join(distDir, '_headers');
assert.ok(fs.existsSync(headersPath), 'Vite must copy public/_headers into dist');
const headerRules = parseCfHeaders(fs.readFileSync(headersPath, 'utf8'));
assert.deepEqual(headerRules.map((rule) => rule.path), ['/js/*', '/assets/*']);
for (const rule of headerRules) {
  assert.equal(rule.headers['cache-control'], 'public, max-age=31536000, immutable');
}
assert.equal(headerRules.some((rule) => rule.path === '/*' || rule.path === '/'), false);

console.log(`production_bundle_test=PASS chunks=${chunkFiles.length}`);
