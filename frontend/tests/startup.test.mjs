import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mainSource = fs.readFileSync(path.join(frontendRoot, 'src', 'main.js'), 'utf8');
const executable = mainSource.replace(/^import\s[\s\S]*?;\n/gm, '');

assert.equal(mainSource.includes('startupShell'), false);
assert.match(mainSource, /router\.isReady\(\)\.then/);
assert.match(executable, /app\.mount\(/);

const drain = async (times = 8) => {
  for (let i = 0; i < times; i += 1) {
    await Promise.resolve();
  }
};

const createHarness = ({ nextTick } = {}) => {
  const events = [];
  const copy = { textContent: '正在加载' };
  const timers = [];
  const shell = {
    removed: false,
    attrs: { 'aria-busy': 'true' },
    classes: new Set(),
    listeners: {},
    classList: {
      add(name) {
        shell.classes.add(name);
      }
    },
    addEventListener(type, handler) {
      this.listeners[type] = handler;
    },
    copy,
    setAttribute(name, value) {
      this.attrs[name] = String(value);
    },
    querySelector(sel) {
      return sel === '.startup-copy' ? copy : null;
    },
    remove() {
      this.removed = true;
    }
  };

  let resolveReady;
  let rejectReady;
  const ready = new Promise((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });

  const router = {
    isReady() {
      events.push('isReady');
      return ready;
    }
  };

  const context = {
    createApp(Root) {
      events.push(`create:${Root.name}`);
      return {
        component(name) {
          events.push(`register:${name}`);
        },
        use(plugin) {
          events.push(plugin === router ? 'use-router' : 'use');
        },
        mount(target) {
          events.push(`mount:${target}`);
        }
      };
    },
    App: { name: 'Root' },
    router,
    nextTick: nextTick ?? (async () => {
      events.push('nextTick');
    }),
    ElSelect: {},
    ElOption: {},
    FontAwesomeIcon: {},
    document: {
      getElementById(id) {
        return id === 'startup-shell' && !shell.removed ? shell : null;
      }
    },
    console: {
      error(...args) {
        events.push('console.error');
        events.push(args[0]);
      }
    }
  };

  context.setTimeout = (fn, ms) => {
    timers.push({ fn, ms });
  };

  return { events, shell, copy, timers, resolveReady, rejectReady, context };
};

const runMain = (context) => {
  vm.runInNewContext(executable, context, { filename: 'main.js' });
};

{
  let settleTick;
  const { events, shell, resolveReady, context } = createHarness({
    nextTick: () => {
      events.push('nextTick');
      return new Promise((resolve) => {
        settleTick = resolve;
      });
    }
  });

  runMain(context);
  assert.deepEqual(events, [
    'create:Root',
    'register:ElSelect',
    'register:ElOption',
    'register:FontAwesomeIcon',
    'use-router',
    'mount:#app',
    'isReady'
  ]);
  assert.equal(shell.removed, false);

  resolveReady();
  await drain();
  assert.ok(events.includes('nextTick'));
  assert.equal(shell.removed, false);

  settleTick();
  await drain();
  // 先淡出，transitionend 后才移除
  assert.equal(shell.removed, false);
  assert.ok(shell.classes.has('is-leaving'));
  assert.equal(shell.attrs['aria-busy'], 'false');
  shell.listeners.transitionend();
  assert.equal(shell.removed, true);
}

{
  // transitionend 不触发时由定时器兜底移除
  const { shell, timers, resolveReady, context } = createHarness();
  runMain(context);
  resolveReady();
  await drain();
  assert.ok(shell.classes.has('is-leaving'));
  assert.equal(shell.removed, false);
  assert.equal(timers.length, 1);
  assert.ok(timers[0].ms <= 500);
  timers[0].fn();
  assert.equal(shell.removed, true);
}

{
  const { events, shell, copy, rejectReady, context } = createHarness();
  runMain(context);
  assert.equal(shell.removed, false);
  rejectReady(new Error('Failed to fetch dynamically imported module'));
  await drain();
  assert.equal(shell.removed, false);
  assert.equal(shell.attrs['aria-busy'], 'false');
  assert.equal(shell.classes.has('is-leaving'), false);
  assert.equal(copy.textContent, '页面加载失败，请刷新重试');
  assert.equal(events.includes('nextTick'), false);
  assert.equal(events.includes('console.error'), true);
}

console.log('startup_test=PASS');
