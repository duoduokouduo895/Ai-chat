import assert from 'node:assert/strict';
import { createAsciiIntroEngine } from '../src/components/intro/asciiIntroEngine.mjs';

const windowListeners = new Map();
const documentListeners = new Map();
const mediaListeners = new Set();
const rafCallbacks = new Map();
const observers = [];
let rafId = 0;
let hidden = false;
let now = 0;
let reduced = false;

function listen(map, type, fn) {
  if (!map.has(type)) map.set(type, new Set());
  map.get(type).add(fn);
}
function unlisten(map, type, fn) {
  map.get(type)?.delete(fn);
}
function fire(map, type) {
  for (const fn of [...(map.get(type) || [])]) fn();
}

function drawingContext() {
  return {
    setTransform() {},
    fillRect() {},
    clearRect() {},
    fillText() {},
    save() {},
    restore() {},
    translate() {},
    rotate() {},
    beginPath() {},
    roundRect() {},
    stroke() {},
    measureText: () => ({ width: 120, actualBoundingBoxAscent: 20, actualBoundingBoxDescent: 6 }),
    font: '', textAlign: '', textBaseline: '', fillStyle: '', strokeStyle: '', globalAlpha: 1, lineWidth: 1
  };
}

class TrackingObserver {
  constructor() {
    this.disconnected = false;
    this.observed = 0;
    observers.push(this);
  }
  observe() { this.observed += 1; }
  unobserve() {}
  disconnect() { this.disconnected = true; }
}

function installDom() {
  now = 0;
  hidden = false;
  reduced = false;
  rafId = 0;
  rafCallbacks.clear();
  windowListeners.clear();
  documentListeners.clear();
  mediaListeners.clear();
  observers.length = 0;
  globalThis.performance = { now: () => now };
  globalThis.requestAnimationFrame = (cb) => {
    const id = ++rafId;
    rafCallbacks.set(id, cb);
    return id;
  };
  globalThis.cancelAnimationFrame = (id) => { rafCallbacks.delete(id); };
  globalThis.window = globalThis;
  globalThis.window.devicePixelRatio = 1;
  globalThis.window.visualViewport = undefined;
  globalThis.window.addEventListener = (type, fn) => listen(windowListeners, type, fn);
  globalThis.window.removeEventListener = (type, fn) => unlisten(windowListeners, type, fn);
  globalThis.window.matchMedia = () => ({
    get matches() { return reduced; },
    addEventListener: (_, fn) => mediaListeners.add(fn),
    removeEventListener: (_, fn) => mediaListeners.delete(fn)
  });
  globalThis.document = {
    get hidden() { return hidden; },
    addEventListener: (type, fn) => listen(documentListeners, type, fn),
    removeEventListener: (type, fn) => unlisten(documentListeners, type, fn),
    createElement: () => ({
      width: 1,
      height: 1,
      getContext: () => drawingContext(),
      getBoundingClientRect: () => ({ width: 800, height: 600, top: 0, left: 0, right: 800, bottom: 600 })
    })
  };
  globalThis.getComputedStyle = () => ({
    getPropertyValue: (name) => ({
      '--text-primary': '#fff',
      '--canvas': '#111',
      '--font-mono': 'monospace'
    }[name] || ''),
    visibility: 'visible',
    display: 'block'
  });
  globalThis.ResizeObserver = TrackingObserver;
}

function canvasStub() {
  const ctx = drawingContext();
  return {
    width: 800,
    height: 600,
    dataset: {},
    context: ctx,
    getContext: () => ctx,
    getBoundingClientRect: () => ({ width: 800, height: 600, top: 0, left: 0, right: 800, bottom: 600 })
  };
}

function listenerCount() {
  return [...windowListeners.values(), ...documentListeners.values()]
    .reduce((sum, set) => sum + set.size, 0) + mediaListeners.size;
}

function flushRaf(steps = 1, dt = 1000 / 60) {
  for (let i = 0; i < steps; i++) {
    now += dt;
    const queued = [...rafCallbacks.entries()];
    for (const [id, callback] of queued) {
      rafCallbacks.delete(id);
      callback(now);
    }
  }
}

function assertObserversDisconnected() {
  assert.ok(observers.length > 0, 'observers were created');
  assert.ok(observers.every((item) => item.disconnected), 'created observers were disconnected');
}

installDom();

let errorCount = 0;
let completeCount = 0;
const engine = createAsciiIntroEngine({
  canvas: canvasStub(),
  onPhase() {},
  onRevealStart() {},
  onComplete: () => { completeCount++; },
  onError: () => { errorCount++; }
});
engine.start();
assert.ok(rafCallbacks.size > 0, 'start schedules animation');
assert.ok(listenerCount() > 0, 'start attaches lifecycle listeners');
flushRaf(3);
assert.ok(rafCallbacks.size > 0, 'driven frames keep the loop scheduled');
hidden = true;
fire(documentListeners, 'visibilitychange');
assert.equal(rafCallbacks.size, 0, 'hidden document cancels the frame loop');
hidden = false;
fire(documentListeners, 'visibilitychange');
assert.ok(rafCallbacks.size > 0, 'visible document reschedules the frame loop');
engine.dispose();
assert.equal(listenerCount(), 0, 'dispose removes observers and listeners');
assert.equal(rafCallbacks.size, 0, 'dispose cancels animation frames');
assertObserversDisconnected();
hidden = true;
fire(documentListeners, 'visibilitychange');
assert.equal(rafCallbacks.size, 0, 'disposed engine ignores visibility');
engine.dispose();
assert.equal(errorCount, 0);

completeCount = 0;
observers.length = 0;
const skipping = createAsciiIntroEngine({
  canvas: canvasStub(),
  onPhase() {},
  onRevealStart() {},
  onComplete: () => { completeCount++; },
  onError: () => { errorCount++; }
});
skipping.start();
flushRaf(2);
reduced = true;
for (const fn of mediaListeners) fn({ matches: true });
assert.equal(completeCount, 1, 'reduced-motion change exits through skip');
assert.equal(listenerCount(), 0);
assert.equal(rafCallbacks.size, 0, 'skip drains the animation queue');
assertObserversDisconnected();
skipping.skip();
assert.equal(completeCount, 1, 'skip is idempotent');

errorCount = 0;
observers.length = 0;
const failingCanvas = canvasStub();
failingCanvas.getBoundingClientRect = () => { throw new Error('layout failed'); };
const failing = createAsciiIntroEngine({
  canvas: failingCanvas,
  onPhase() {},
  onRevealStart() {},
  onComplete() {},
  onError: () => { errorCount++; }
});
failing.start();
assert.equal(errorCount, 1, 'start failure reports onError');
assert.equal(listenerCount(), 0, 'start failure disposes listeners');
assert.equal(rafCallbacks.size, 0);
assertObserversDisconnected();

observers.length = 0;
const ObserveFail = class extends TrackingObserver {
  observe() { throw new Error('observe failed'); }
};
globalThis.ResizeObserver = ObserveFail;
assert.throws(() => createAsciiIntroEngine({
  canvas: canvasStub(),
  onPhase() {},
  onRevealStart() {},
  onComplete() {},
  onError() {}
}), /observe failed/);
assertObserversDisconnected();
globalThis.ResizeObserver = TrackingObserver;

let phase = '';
let revealCount = 0;
errorCount = 0;
completeCount = 0;
observers.length = 0;
hidden = false;
reduced = false;
const paced = createAsciiIntroEngine({
  canvas: canvasStub(),
  onPhase: (value) => { phase = value; },
  onRevealStart: () => { revealCount++; },
  onComplete: () => { completeCount++; },
  onError: () => { errorCount++; }
});
paced.start();
flushRaf(2);
assert.equal(phase, 'idle');
paced.advance();
assert.equal(phase, 'morph');
paced.advance();
assert.equal(phase, 'morph', 'second click is ignored by the engine');
flushRaf(20);
assert.equal(phase, 'morph', '200ms is still gathering');
assert.equal(revealCount, 0, 'reveal-start waits until 1450ms');
hidden = true;
fire(documentListeners, 'visibilitychange');
assert.equal(rafCallbacks.size, 0);
now += 5000;
hidden = false;
fire(documentListeners, 'visibilitychange');
flushRaf(1);
assert.equal(phase, 'morph', 'hidden wall-clock gap must not skip morph');
assert.equal(completeCount, 0);
paced.skip();
assert.equal(completeCount, 1);
assert.equal(rafCallbacks.size, 0, 'skip after driven frames drains the queue');
assert.equal(errorCount, 0);
assertObserversDisconnected();

phase = '';
completeCount = 0;
revealCount = 0;
observers.length = 0;
hidden = false;
const finishing = createAsciiIntroEngine({
  canvas: canvasStub(),
  onPhase: (value) => { phase = value; },
  onRevealStart: () => { revealCount++; },
  onComplete: () => { completeCount++; },
  onError: () => { errorCount++; }
});
finishing.start();
flushRaf(1);
finishing.advance();
for (let i = 0; i < 50 && phase === 'morph'; i++) flushRaf(1);
assert.equal(phase, 'morph', 'wordmark hold lasts through 840ms');
assert.equal(revealCount, 0);
for (let i = 0; i < 40 && revealCount === 0; i++) flushRaf(1);
assert.equal(revealCount, 1, 'reveal-start fires once after crossing 1450ms');
for (let i = 0; i < 80 && phase !== 'done'; i++) flushRaf(1);
assert.equal(phase, 'done');
assert.equal(revealCount, 1);
assert.equal(completeCount, 1, 'timeline completion notifies onComplete');
assert.equal(rafCallbacks.size, 0, 'completion drains the animation queue');
finishing.skip();
assert.equal(completeCount, 1, 'complete fires once');
assertObserversDisconnected();

errorCount = 0;
completeCount = 0;
observers.length = 0;
hidden = false;
const throwing = canvasStub();
const drawing = createAsciiIntroEngine({
  canvas: throwing,
  onPhase() {},
  onRevealStart() {},
  onComplete: () => { completeCount++; },
  onError: () => { errorCount++; }
});
drawing.start();
throwing.context.clearRect = () => { throw new Error('draw failed'); };
flushRaf(2);
assert.equal(errorCount, 1, 'draw throw reports onError');
assert.equal(completeCount, 0);
assert.equal(listenerCount(), 0, 'draw throw disposes listeners');
assert.equal(rafCallbacks.size, 0, 'draw throw drains the animation queue');
assertObserversDisconnected();

installDom();
const clickCanvas = canvasStub();
const drawn = [];
const clickCtx = clickCanvas.context;
clickCtx.measureText = (text) => {
  const sample = String(text || 'N');
  const unit = 8;
  return {
    width: Math.max(unit, sample.length * unit),
    actualBoundingBoxAscent: 8,
    actualBoundingBoxDescent: 2
  };
};
clickCtx.fillText = (text, x, y) => {
  drawn.push({ text, x, y, alpha: clickCtx.globalAlpha });
};
let clickErrors = 0;
const clickEngine = createAsciiIntroEngine({
  canvas: clickCanvas,
  onPhase() {},
  onRevealStart() {},
  onComplete() {},
  onError: () => { clickErrors++; }
});
clickEngine.start();
flushRaf(80);
drawn.length = 0;
flushRaf(1);
const clicks = drawn.filter((item) => item.text === 'CLICK');
const glyphs = drawn.filter((item) => item.text !== 'CLICK');
assert.ok(clicks.length >= 1, 'CLICK remains after formation');
assert.ok(clicks.every((item) => Math.abs(item.x - 400) < 1 && Math.abs(item.y - 300) < 1),
  'CLICK stays centred');
const nearCentre = glyphs.filter((item) => Math.hypot(item.x - 400, item.y - 300) < 32);
assert.ok(nearCentre.length > 0, `no 32px excavated hole near=${nearCentre.length} glyphs=${glyphs.length}`);
assert.equal(clickErrors, 0);
clickEngine.dispose();

installDom();
hidden = false;
errorCount = 0;
let skipPhase = '';
let skipComplete = 0;
const skippingPhase = createAsciiIntroEngine({
  canvas: canvasStub(),
  getLayout: () => { throw new Error('layout failed'); },
  onPhase: (value) => { skipPhase = value; },
  onRevealStart() {},
  onComplete: () => { skipComplete++; },
  onError: () => { errorCount++; }
});
skippingPhase.start();
flushRaf(2);
skippingPhase.advance();
assert.equal(skipPhase, 'morph');
flushRaf(40);
assert.ok(skipPhase === 'morph' || skipPhase === 'raster');
skippingPhase.skip();
assert.equal(skipComplete, 1, 'skip during play completes once');
assert.equal(errorCount, 0, 'layout snapshot failure does not abort the engine');

console.log('intro_engine_test=PASS start, visibility, dispose, reduced-motion skip, start-error, raf-drive, hidden-morph, complete-once, draw-throw, observer-disconnect, click-over-glyphs, skip-any-phase');
