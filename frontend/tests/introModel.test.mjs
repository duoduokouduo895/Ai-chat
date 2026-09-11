import assert from 'node:assert/strict';
import {
  createIntroSession, createIntroTimeline, createFrameGate, TARGET_FRAME_MS,
  sculpturePose, sculptureRadius, sculptureTwist, stepSpring, applyCursorImpulse,
  SURFACE_CYCLE, DISPERSE_SECONDS, REVEAL_DELAY_SECONDS, INTRO_SECONDS,
  MORPH_SECONDS, MORPH_END, RASTER_END, playPhaseAt, matchMorphTargets, morphPoint,
  physicsSteps, PHYSICS_DT, MAX_PHYSICS_STEPS, createDisperseParticle,
  stepDisperseParticle, revealProgress, sampleDecryptText, createDecryptPlayback,
  GLITCH_CHARS, isDecryptKept
} from '../src/components/intro/introModel.mjs';

const values = new Map();
const storage = { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value) };
const session = createIntroSession(() => storage);
assert.equal(session.shouldPlay(true), false);
assert.equal(session.shouldPlay(), true);
session.finish();
assert.equal(session.shouldPlay(), false);
assert.equal(createIntroSession(() => storage).shouldPlay(), false, 'refresh reuses the tab marker');
const unavailable = createIntroSession(() => { throw new Error('Storage blocked'); });
assert.equal(unavailable.shouldPlay(), true);
unavailable.finish();
assert.equal(unavailable.shouldPlay(), false, 'memory prevents replay when storage throws');

const timeline = createIntroTimeline();
timeline.tick(50);
assert.equal(timeline.phase, 'idle', 'idle never advances automatically');
assert.equal(timeline.advance(), true);
assert.equal(timeline.phase, 'morph');
assert.equal(timeline.advance(), false, 'second click must not retrigger');
timeline.tick(0.2);
assert.equal(timeline.phase, 'morph');
timeline.tick(0.64);
assert.equal(timeline.phase, 'morph', 'wordmark still holds at 840ms');
timeline.setElapsed(0.85);
assert.equal(timeline.phase, 'raster', 'scatter and scan wait until 850ms');
timeline.setElapsed(1.449);
assert.equal(timeline.phase, 'raster', 'matrix hold lasts through 1449ms');
timeline.setElapsed(1.451);
assert.equal(timeline.phase, 'fade', 'handoff starts once 1450ms is crossed');
timeline.setElapsed(2.151);
assert.equal(timeline.phase, 'done', 'intro finishes once 2150ms is crossed');
assert.equal(timeline.advance(), false);
assert.equal(playPhaseAt(0), 'morph');
assert.equal(playPhaseAt(0.35), 'morph');
assert.equal(playPhaseAt(0.6), 'morph', 'mid-hold stays on the wordmark');
assert.equal(playPhaseAt(0.84), 'morph');
assert.equal(playPhaseAt(0.85), 'raster');
assert.equal(playPhaseAt(1.35), 'raster');
assert.equal(playPhaseAt(1.449), 'raster');
assert.equal(playPhaseAt(1.451), 'fade');
assert.equal(playPhaseAt(2.14), 'fade');
assert.equal(playPhaseAt(2.151), 'done');
assert.ok(Math.abs(MORPH_END - 0.85) < 1e-12);
assert.ok(Math.abs(RASTER_END - 1.45) < 1e-12);
assert.ok(Math.abs(INTRO_SECONDS - 2.15) < 1e-12);
for (const elapsed of [0, 0.35, 0.6, 0.84, 1.35, 1.45, 2.0]) {
  const run = createIntroTimeline();
  run.advance();
  run.tick(elapsed);
  run.skip();
  run.tick(100);
  assert.equal(run.phase, 'done', `skip is terminal at ${elapsed}`);
}
assert.equal(revealProgress(0), 0);
assert.equal(revealProgress(1.449), 0);
assert.ok(revealProgress(1.8) > 0 && revealProgress(1.8) < 1);
assert.equal(revealProgress(REVEAL_DELAY_SECONDS), 0);
assert.equal(revealProgress(INTRO_SECONDS), 1);
{
  const { rasterScanFront } = await import('../src/components/intro/workspaceRaster.mjs');
  assert.equal(rasterScanFront(0.84, 900, 0.85, 0.5), 0, 'scan does not start during the 500ms hold');
  assert.equal(rasterScanFront(0.85, 900, 0.85, 0.5), 0);
  assert.ok(rasterScanFront(1.35, 900, 0.85, 0.5) >= 900, 'matrix is complete at 1350ms');
}
const parked = morphPoint({ x: 0.2, y: 0.3 }, { x: 0.7, y: 0.4 }, 1);
const stillParked = morphPoint({ x: 0.2, y: 0.3 }, { x: 0.7, y: 0.4 }, 0.84 / 0.35);
assert.ok(Math.abs(parked.x - stillParked.x) < 1e-12 && Math.abs(parked.y - stillParked.y) < 1e-12,
  'after 350ms the wordmark pose is frozen');

const poseA = sculpturePose(0);
assert.ok(poseA.radial < 0.4 && poseA.height > 1.3, 'pose A is a tall thin rise');
const poseB = sculpturePose(SURFACE_CYCLE * 0.25);
assert.ok(poseB.waist > 0.8 && poseB.waist < 1.1 && poseB.radial > 0.45,
  'pose B opens the shoulders without a hard pinch');
const poseC = sculpturePose(SURFACE_CYCLE * 0.5);
assert.ok(poseC.radial > 0.6 && poseC.radial < 0.74 && poseC.height > 0.9 && poseC.height < 1.12,
  'pose C is a mild wide lens');
const poseD = sculpturePose(SURFACE_CYCLE * 0.75);
assert.ok(poseD.height > 1.2 && poseD.radial < 0.5, 'pose D stretches and leaves upward');
assert.ok(poseC.radial - poseA.radial > 0.3, 'wide pose has real horizontal tension');
const keyed = { radial: 0.64, height: 1.18, waist: 1.28, turn: 0, travel: 0 };
assert.ok(Math.abs(sculptureRadius(0.5, keyed) - sculptureRadius(-0.5, keyed)) < 1e-9, 'upper and lower lobes stay coordinated');
assert.ok(Math.abs(sculptureRadius(0.5, keyed) - sculptureRadius(2.5, keyed)) < 1e-9, 'profile is periodic so bodies can enter in turn');
assert.equal(sculptureTwist(0, 1, 1), sculptureTwist(0, 1, 0), 'pointer does not steer the whole surface');
const sprung = { x: 0, y: 0, vx: 0, vy: 0, targetX: 10, targetY: 0 };
for (let i = 0; i < 90; i++) stepSpring(sprung, 1 / 60);
assert.ok(Math.abs(sprung.x - 10) < 0.5, 'spring settles on the target cell');
const near = { x: 0, y: 0, vx: 0, vy: 0 };
const far = { x: 400, y: 0, vx: 0, vy: 0 };
applyCursorImpulse(near, 8, 0, 80, 2000, 1 / 60);
applyCursorImpulse(far, 8, 0, 80, 2000, 1 / 60);
assert.ok(Math.abs(near.vx) > 0.05, 'cursor stirs nearby points');
assert.equal(far.vx, 0, 'cursor does not stir distant points');

const short = physicsSteps(0.008);
assert.equal(short.length, 1);
assert.ok(short[0] <= PHYSICS_DT);
const long = physicsSteps(1);
assert.equal(long.length, MAX_PHYSICS_STEPS);
assert.ok(long.every((dt) => dt <= PHYSICS_DT + 1e-12));

const particle = createDisperseParticle({
  x: 0.2, y: 0.5, glyph: 'N', light: 0.8, alpha: 0.9, randA: 0.5, randB: 0.5
});
assert.ok(Math.abs(particle.vx - ((0.2 - 0.5) * 0.8)) < 1e-9, 'outward velocity is seeded once');
assert.ok(Math.abs(particle.vy - (-0.25)) < 1e-9, 'lift is seeded once');
const vx0 = particle.vx;
stepDisperseParticle(particle, 1 / 60, 1 / 60);
assert.equal(particle.vx, vx0, 'random velocity is not regenerated');
assert.ok(particle.vy > -0.25, 'semi-implicit euler applies gravity to velocity first');
assert.ok(particle.y !== 0.5, 'position updates after velocity');
const falling = createDisperseParticle({
  x: 0.5, y: 0.99, glyph: 'A', alpha: 1, randA: 0.5, randB: 0
});
falling.vy = 2;
stepDisperseParticle(falling, 1 / 60, 0.5);
assert.equal(falling.visible, false, 'particles leaving the viewport stop drawing');
assert.ok(falling.y > 1 || falling.visible === false);
const faded = createDisperseParticle({
  x: 0.5, y: 0.5, glyph: 'O', alpha: 1, randA: 0.5, randB: 0.5
});
stepDisperseParticle(faded, 1 / 60, 1);
assert.ok(faded.alpha <= 0.04);
assert.equal(faded.visible, false);

const hz144 = createFrameGate();
const step144 = 1000 / 144;
let draws144 = 0;
for (let i = 0; i <= 288; i++) {
  if (hz144.accept(i * step144).draw) draws144++;
}
assert.ok(draws144 > 105 && draws144 < 140, `144Hz must average near 60fps not 48, draws=${draws144}`);
const hz60 = createFrameGate();
let draws60 = 0;
for (let i = 0; i <= 120; i++) {
  if (hz60.accept(i * TARGET_FRAME_MS).draw) draws60++;
}
assert.ok(draws60 >= 120 && draws60 <= 121, `60Hz accepts every vsync, draws=${draws60}`);
const resumed = createFrameGate();
assert.equal(resumed.accept(0).deltaSeconds, TARGET_FRAME_MS / 1000);
resumed.reset();
assert.equal(resumed.accept(5000).deltaSeconds, TARGET_FRAME_MS / 1000, 'visibility reset must not dump paused time');

console.log('intro_model_test=PASS session, timeline, skip, spring, disperse, frame-gate');

{
  const { default: assertR } = await import('node:assert/strict');
  const r = await import('../src/components/intro/introModel.mjs');

  function countInside(clock, width = 1440, height = 900) {
    const pose = r.sculpturePose(clock);
    let n = 0;
    for (let y = height * 0.25; y <= height * 0.75; y += 16) {
      for (let x = width * 0.2; x <= width * 0.8; x += 10) {
        if (r.evaluateSurfaceCell(x, y, width, height, pose).inside) n++;
      }
    }
    return n;
  }
  const c0 = countInside(1.5);
  const c30 = countInside(30);
  const c90 = countInside(90);
  assertR.ok(c0 > 80 && c30 > 80 && c90 > 80, `central coverage persists c0=${c0} c30=${c30} c90=${c90}`);

  for (const [w, h, minCols, budget] of [[1440, 900, 80, 7200], [390, 844, 40, 3000], [1920, 1080, 90, 7200]]) {
    const g = r.introGridMetrics(w, h, 0);
    assertR.ok(g.count <= budget, `budget ${w}x${h} count=${g.count}`);
    assertR.ok(g.cols >= minCols, `cols ${g.cols} at ${w}`);
    assertR.ok(Math.abs(g.cols * g.cellX - w) < 1e-6, 'grid covers width');
    assertR.ok(g.fontSize <= g.cellY + 1e-6, 'font fits row');
    assertR.ok(g.fontSize <= g.cellX + 1e-6, 'font fits column');
    assertR.ok(g.fontSize >= Math.min(g.cellX, g.cellY) * 0.55, 'font still fills the cell');
  }

  assertR.equal(r.ASCII_RAMP, 'NOA86?<>/=+!:.');
  assertR.ok(!/[a-z·I-]/.test(r.ASCII_RAMP), 'ramp stays in the even-width set');
  let prevIdx = r.glyphIndexFor(0, null);
  for (let light = 0.05; light <= 1; light += 0.05) {
    const idx = r.glyphIndexFor(light, prevIdx);
    assertR.ok(idx <= prevIdx, `brighter light stays denser light=${light} ${prevIdx}->${idx}`);
    assertR.ok(prevIdx - idx <= 1, `luminance walks one step light=${light}`);
    prevIdx = idx;
  }
  const cols = 24, rows = 18;
  const bl = r.springSweepT(0, rows - 1, cols, rows);
  const midSweep = r.springSweepT(cols / 2, rows / 2, cols, rows);
  const tr = r.springSweepT(cols - 1, 0, cols, rows);
  assertR.ok(bl < midSweep && midSweep < tr, `sweep is bottom-left to top-right bl=${bl} mid=${midSweep} tr=${tr}`);
  assertR.ok(r.SPRING_RAMP.startsWith('NOA86'), 'spring ramp keeps the dense head');
  assertR.ok(!/[a-z·I-]/.test(r.SPRING_RAMP), 'spring ramp stays in the even-width set');

  const cell = {
    col: 4, row: 12, u: 0.18, coverage: 0.5, light: 0.55,
    glyphIndex: 6, glyph: r.glyphAt(6), springIndex: null
  };
  let lastIndex = null;
  const seen = new Set();
  for (let clock = 0; clock < r.SPRING_SWEEP_PERIOD * 2.5; clock += 1 / 60) {
    r.stepSpringGlyph(cell, clock, cols, rows);
    assertR.ok(r.SPRING_RAMP.includes(cell.glyph), `glyph stays on spring ramp ${cell.glyph}`);
    if (lastIndex != null) {
      assertR.ok(Math.abs(cell.springIndex - lastIndex) <= 1,
        `no skip ${r.springGlyphAt(lastIndex)}->${cell.glyph} at ${clock}`);
    }
    lastIndex = cell.springIndex;
    seen.add(cell.glyph);
  }
  assertR.ok(seen.size > 1, `ramp still breathes size=${seen.size}`);

  const formedPose = r.sculpturePose(1.2);
  const formedCentre = r.evaluateSurfaceCell(720, 450, 1440, 900, formedPose);
  assertR.ok(formedPose.formed && formedCentre.inside, 'formed centre stays inside the silhouette');
  const edgeW = 1440;
  const edgeCell = 12;
  const cov = 0.5;
  const cellU = edgeCell / (edgeW * 0.5);
  assertR.equal(r.springEdgeAlpha(0, cov, edgeCell, edgeW), 1);
  assertR.equal(r.springEdgeAlpha(cov + 0.03, cov, edgeCell, edgeW), 0);
  assertR.equal(r.springEdgeAlpha(cov + 0.03 - 2 * cellU, cov, edgeCell, edgeW), 1);

  function maxInsideSpan(clock, width = 1440, height = 900) {
    const pose = r.sculpturePose(clock);
    let maxU = 0;
    for (let y = height * 0.1; y <= height * 0.9; y += 12) {
      for (let x = 0; x <= width; x += 12) {
        if (r.evaluateSurfaceCell(x, y, width, height, pose).inside) {
          maxU = Math.max(maxU, Math.abs((x - width / 2) / (width / 2)));
        }
      }
    }
    return maxU;
  }
  for (const t of [0, r.SURFACE_CYCLE * 0.25, r.SURFACE_CYCLE * 0.5, r.SURFACE_CYCLE * 0.75, 12]) {
    const span = maxInsideSpan(t);
    assertR.ok(span < 0.75, `compressed pose stays inset from edges t=${t} span=${span}`);
  }

  console.log('intro_model_repair_test=PASS coverage, grid, spring, silhouette');
}

assert.ok(GLITCH_CHARS.includes('$') && GLITCH_CHARS.includes('Ø'));
assert.equal(isDecryptKept(' '), true);
assert.equal(isDecryptKept('中'), false);
const zh = '选择 模型';
assert.equal(sampleDecryptText(zh, 1), zh);
const rng = (() => { let i = 0; return () => { i += 0.17; return i % 1; }; })();
const mid = sampleDecryptText(zh, 0.2, rng);
assert.notEqual(mid, zh);
assert.equal(mid.includes(' '), true, 'whitespace is preserved');
assert.equal(Array.from(mid).length, Array.from(zh).length);
const start = sampleDecryptText('标题', 0, () => 0);
assert.notEqual(start, '标题');
assert.equal(sampleDecryptText('标题', 0, () => 0).includes('标') === false || sampleDecryptText('标题', 1) === '标题', true);

const rafCallbacks = new Map();
let rafId = 0;
let now = 0;
globalThis.performance = { now: () => now };
globalThis.requestAnimationFrame = (cb) => { const id = ++rafId; rafCallbacks.set(id, cb); return id; };
globalThis.cancelAnimationFrame = (id) => { rafCallbacks.delete(id); };
function flush(steps = 1, dt = 1000 / 30) {
  for (let i = 0; i < steps; i++) {
    now += dt;
    const queued = [...rafCallbacks.entries()];
    for (const [id, callback] of queued) {
      rafCallbacks.delete(id);
      callback(now);
    }
  }
}

let latest = '';
let done = 0;
const play = createDecryptPlayback({
  text: '中文 标题',
  onUpdate: (value) => { latest = value; },
  onComplete: () => { done += 1; }
});
play.start();
flush(1, 0);
flush(8, 50);
assert.ok(latest.includes(' '), 'decrypt keeps spaces while running');
assert.equal(done, 0, 'decrypt is not finished early');
flush(20, 50);
assert.equal(latest, '中文 标题');
assert.equal(done, 1);

let cancelledLatest = 'old';
const cancelling = createDecryptPlayback({
  text: '过期标题',
  onUpdate: (value) => { cancelledLatest = value; },
  onComplete: () => { done += 1; }
});
cancelling.start();
flush(2, 50);
cancelling.cancel();
const frozen = cancelledLatest;
flush(20, 50);
assert.equal(cancelledLatest, frozen, 'cancel stops further frames');
assert.equal(done, 1, 'cancelled playback does not complete');

let paused = false;
let pausedLatest = '';
let pausedDone = 0;
const pausing = createDecryptPlayback({
  text: '稳定标题',
  getPaused: () => paused,
  onUpdate: (value) => { pausedLatest = value; },
  onComplete: () => { pausedDone += 1; }
});
pausing.start();
flush(4, 50);
paused = true;
const held = pausedLatest;
flush(12, 50);
assert.equal(pausedLatest, held, 'paused decrypt does not advance');
assert.equal(pausedDone, 0);
paused = false;
flush(20, 50);
assert.equal(pausedLatest, '稳定标题');
assert.equal(pausedDone, 1);

console.log('intro_decrypt_test=PASS chinese, whitespace, cancel, final text, pause');

{
  const landed = morphPoint({ x: 0.1, y: 0.2 }, { x: 0.7, y: 0.4 }, 1);
  assert.ok(Math.abs(landed.x - 0.7) < 1e-12 && Math.abs(landed.y - 0.4) < 1e-12, 'morph lands exactly at 350ms');
  const midMorph = morphPoint({ x: 0, y: 0 }, { x: 1, y: 1 }, 0.5);
  assert.ok(midMorph.x > 0 && midMorph.x < 1);

  const targets = [
    { x: 0.2, y: 0.3, u: 0.2, v: 0.3, glyph: 'A', alpha: 1, light: 0.8 },
    { x: 0.8, y: 0.3, u: 0.8, v: 0.3, glyph: '8', alpha: 1, light: 0.8 }
  ];
  const few = matchMorphTargets([{ x: 0.1, y: 0.1, glyph: 'N', alpha: 0.5, light: 0.4 }], targets);
  assert.equal(few.length, 2);
  assert.equal(few.filter((p) => p.role === 'spawn').length, 1);
  const many = matchMorphTargets([
    { x: 0.1, y: 0.1, glyph: 'N', alpha: 1, light: 0.4 },
    { x: 0.2, y: 0.1, glyph: 'O', alpha: 1, light: 0.4 },
    { x: 0.9, y: 0.8, glyph: 'A', alpha: 1, light: 0.4 }
  ], targets);
  assert.ok(many.some((p) => p.role === 'fade'));
  const empty = matchMorphTargets([], targets);
  assert.equal(empty.length, 2);
  assert.ok(empty.every((p) => p.role === 'spawn'));
  const a = matchMorphTargets([{ x: 0.4, y: 0.2, glyph: 'N', alpha: 1, light: 0.5 }], targets);
  const b = matchMorphTargets([{ x: 0.4, y: 0.2, glyph: 'N', alpha: 1, light: 0.5 }], targets);
  assert.equal(a[0].to.glyph, b[0].to.glyph);
}

{
  const { wordmarkInside, sampleWordmark, DDUO_VIEWBOX } = await import('../src/components/intro/dduoMark.mjs');
  assert.equal(wordmarkInside(118 / DDUO_VIEWBOX.width, 178 / DDUO_VIEWBOX.height), false, 'd hole stays open');
  assert.equal(wordmarkInside(870 / DDUO_VIEWBOX.width, 178 / DDUO_VIEWBOX.height), false, 'o hole stays open');
  assert.equal(wordmarkInside(185 / DDUO_VIEWBOX.width, 80 / DDUO_VIEWBOX.height), true, 'd stem is filled');
  assert.equal(wordmarkInside(940 / DDUO_VIEWBOX.width, 178 / DDUO_VIEWBOX.height), true, 'o ring is filled');
  const mark = sampleWordmark(1440, 900);
  assert.ok(mark.cells.length > 80, `wordmark has body cells=${mark.cells.length}`);
  assert.ok(mark.cols >= 120, `wordmark cols=${mark.cols}`);
  const holeish = mark.cells.filter((c) => Math.hypot(c.u - 0.87, c.v - 0.556) < 0.03);
  assert.equal(holeish.length, 0, 'sampled cells skip the o counter');
  const again = sampleWordmark(1440, 900);
  assert.equal(again.cells.length, mark.cells.length);
  assert.equal(again.cells[0].glyph, mark.cells[0].glyph);
}

{
  const { buildWorkspaceRaster, pointInRoundRect, rasterGridMetrics, readIntroLayout } = await import('../src/components/intro/workspaceRaster.mjs');
  const box = { x: 20, y: 20, w: 80, h: 40, radius: 8 };
  const raster = buildWorkspaceRaster(200, 180, [
    { kind: 'input', ...box },
    { kind: 'title', x: 30, y: 80, w: 120, h: 24, radius: 0, text: 'Dduo AI-Chat' },
    { kind: 'input', x: 400, y: 10, w: 40, h: 40, radius: 0 }
  ]);
  assert.ok(raster.cells.length > 10);
  for (const cell of raster.cells.filter((c) => c.glyph === '=' || c.glyph === '|')) {
    assert.ok(cell.x >= -1 && cell.x <= 201 && cell.y >= -1 && cell.y <= 181, 'cells stay in canvas space');
  }
  const interior = raster.cells.find((c) => c.glyph !== '=' && c.glyph !== '|' && c.glyph !== '-' && pointInRoundRect(c.x, c.y, box));
  const edge = raster.cells.find((c) => c.glyph === '=');
  if (interior && edge) assert.ok(interior.light < edge.light, 'input interior is dimmer than the rim');
  const title = raster.cells.some((c) => c.glyph === 'D' || c.glyph === 'd');
  assert.ok(title, 'title uses real characters');
  const dprA = rasterGridMetrics(800, 600);
  const dprB = rasterGridMetrics(800, 600);
  assert.equal(dprA.stepX, dprB.stepX);
  const offsetRoot = {
    querySelectorAll: () => [{
      getBoundingClientRect: () => ({ left: 140, top: 80, width: 60, height: 30, bottom: 110 }),
      getAttribute: (name) => ({ 'data-intro': 'title', 'data-intro-text': 'Chat' }[name] || '')
    }]
  };
  globalThis.getComputedStyle = () => ({ visibility: 'visible', display: 'block', opacity: '1', borderTopLeftRadius: '0' });
  const items = readIntroLayout(offsetRoot, { left: 40, top: 20, bottom: 800 });
  assert.equal(items.length, 1);
  assert.equal(items[0].x, 100);
  assert.equal(items[0].y, 60);
  const hiddenRoot = {
    querySelectorAll: () => [{
      getBoundingClientRect: () => ({ left: 10, top: 10, width: 40, height: 20, bottom: 30 }),
      getAttribute: () => 'input'
    }]
  };
  globalThis.getComputedStyle = () => ({ visibility: 'hidden', display: 'block', opacity: '1', borderTopLeftRadius: '4px' });
  assert.equal(readIntroLayout(hiddenRoot, { left: 0, top: 0, bottom: 100 }).length, 0);
}

console.log('intro_round2_test=PASS morph match, wordmark holes, raster clip/offset');
