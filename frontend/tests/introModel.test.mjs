import assert from 'node:assert/strict';
import {
  createIntroSession, createIntroTimeline, createFrameGate, TARGET_FRAME_MS,
  FIELD_EXPAND_SECONDS, phaseAt, workspaceOpacity, revealProgress, stepFallingParticle, releasePerchedOccupancy,
  sculpturePose, sculptureRadius, sculptureTwist, morphSample, fieldFront, burstOffset,
  stepSpring, applyCursorImpulse, SURFACE_CYCLE
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
assert.equal(timeline.phase, 'sculpture', 'idle sculpture never advances automatically');
assert.equal(timeline.advance(), true);
assert.equal(timeline.advance(), false, 'double click must not queue the second transition');
timeline.tick(1.59); assert.equal(timeline.phase, 'morph');
timeline.tick(0.02); assert.equal(timeline.phase, 'word');
timeline.tick(50); assert.equal(timeline.phase, 'word', 'word waits for a deliberate second click');
timeline.advance(); assert.equal(timeline.phase, 'falling');
timeline.tick(0.5); assert.equal(timeline.phase, 'field');
timeline.tick(3); assert.equal(timeline.phase, 'field', 'rain progress, not the clock, starts reveal');
assert.equal(timeline.unveil(), true);
assert.equal(timeline.phase, 'reveal');
timeline.tick(0.75); assert.equal(timeline.phase, 'clearing');
timeline.tick(1.81); assert.equal(timeline.phase, 'done');
assert.equal(timeline.advance(), false);
for (const elapsed of [0, 0.3, 1.6, 2.3, 3.8, 5.2]) {
  const run = createIntroTimeline(); run.advance(); run.tick(1.6); run.advance(); run.tick(elapsed);
  run.skip(); run.tick(100);
  assert.equal(run.phase, 'done', `skip is terminal at ${elapsed}`);
}
assert.equal(phaseAt(10), 'done');
assert.equal(revealProgress(0), 0);
assert.ok(revealProgress(0.375) > 0.4 && revealProgress(0.375) < 0.6);
assert.equal(revealProgress(0.75), 1);

const bounds = { width: 400, height: 800, glyphHeight: 10, gravity: 1000 };
const edge = { id: 'composer', left: 100, right: 300, y: 200 };
const particle = (changes = {}) => ({ x: 180, y: 170, vx: 0, vy: 700, spin: 0.1,
  angle: 0, alpha: 1, seed: 0.2, delay: 0, catchable: true, state: 'falling', ...changes });
const p = particle();
stepFallingParticle(p, 0.05, 1, [edge], bounds);
assert.equal(p.state, 'perched', 'swept collision catches a character crossing the whole edge in one frame');
assert.equal(p.y, 195);
assert.equal(p.catchable, false);
const resized = { ...edge, left: 50, right: 350, y: 260 };
stepFallingParticle(p, 0.01, 1.15, [resized], bounds);
assert.equal(p.x, 170, 'resting characters follow normalized edge position on resize');
assert.ok(p.y <= 255 && p.y > 249);
stepFallingParticle(p, 0.02, 1.7, [resized], bounds);
assert.equal(p.state, 'falling');
stepFallingParticle(p, 0.1, 1.8, [resized, { ...edge, id: 'lower', y: 290 }], bounds);
assert.equal(p.state, 'falling', 'released characters are not recaptured');
const removed = particle({ ...p, state: 'perched', releaseAt: 10 });
stepFallingParticle(removed, 0.02, 3, [], bounds);
assert.equal(removed.state, 'falling', 'removing a resting surface releases the character');
const corner = particle({ x: 98 });
stepFallingParticle(corner, 0.05, 1, [edge], bounds);
assert.equal(corner.state, 'falling', 'rounded-corner exclusion must not catch an unsupported character');
const delayed = particle({ delay: 0.6 });
stepFallingParticle(delayed, 0.1, 0.4, [edge], bounds);
assert.equal(delayed.y, 170);
const nearest = particle();
stepFallingParticle(nearest, 0.1, 1, [{ ...edge, id: 'lower', y: 240 }, edge], bounds);
assert.equal(nearest.edgeId, 'composer', 'DOM order must not choose a lower surface');
const ground = particle({ y: 795, catchable: false });
const groundedY = ground.y;
stepFallingParticle(ground, 0.02, 2, [], bounds);
assert.equal(ground.state, 'exiting');
assert.ok(ground.y >= groundedY, 'exit keeps moving downward');
stepFallingParticle(ground, 0.25, 2.4, [], bounds);
assert.ok(ground.y > groundedY + 4, 'exit drifts off the bottom');
assert.ok(ground.alpha < 1 && ground.alpha > 0, 'exit fades while leaving');
for (let i = 0; i < 40 && ground.state !== 'dead'; i++) stepFallingParticle(ground, 0.1, 2.5 + i * 0.1, [], bounds);
assert.equal(ground.state, 'dead');

const stackedEdge = { id: 'composer', left: 100, right: 300, y: 200, occupancy: new Map() };
const stacked = particle();
stepFallingParticle(stacked, 0.05, 1, [stackedEdge], bounds);
const stacked2 = particle({ seed: 0.3 });
stepFallingParticle(stacked2, 0.05, 1, [stackedEdge], bounds);
assert.equal(stacked2.state, 'perched');
assert.equal(stacked2.edgeLevel, 1);
const stacked3 = particle({ seed: 0.4 });
stepFallingParticle(stacked3, 0.05, 1, [stackedEdge], bounds);
assert.equal(stacked3.state, 'falling', 'third character in the same slot is not caught');
releasePerchedOccupancy(stacked, [stackedEdge], bounds);
const stacked4 = particle({ seed: 0.11 });
stepFallingParticle(stacked4, 0.05, 1, [stackedEdge], bounds);
assert.equal(stacked4.state, 'perched', 'culling a perched character frees occupancy');

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
const offset = burstOffset(0.25, 0.5, 100);
assert.ok(Math.hypot(offset.x, offset.y / 0.82) < 100.01, 'burst stays inside the local radius');
const from = { x: 10, y: 10 }, along = { x: 24, y: 8 }, region = { x: 50, y: 30 }, target = { x: 80, y: 40 };
const atRest = morphSample(0, from, along, region, target);
assert.ok(Math.abs(atRest.x - 10) < 0.01 && Math.abs(atRest.y - 10) < 0.01, 'morph starts on the sculpture');
const loosened = morphSample(0.22, from, along, region, target);
assert.ok(Math.abs(loosened.x - 24) < 0.2, 'first beat loosens along the surface');
const grouped = morphSample(0.5, from, along, region, target);
assert.ok(grouped.x > along.x && grouped.x < target.x, 'mid morph is still traveling to the glyph');
assert.ok(Math.abs(grouped.x - 50) > 4, 'no rest at a contracted letter region');
const gathered = morphSample(1, from, along, region, target);
assert.ok(Math.abs(gathered.x - 80) < 0.01 && Math.abs(gathered.y - 40) < 0.01, 'second beat lands on dduo');
const beforeJoin = morphSample(0.21, from, along, region, target);
const afterJoin = morphSample(0.23, from, along, region, target);
assert.ok(Math.abs(afterJoin.x - beforeJoin.x) < 3 && Math.abs(afterJoin.y - beforeJoin.y) < 3,
  'burst-to-coalesce join does not jump');
assert.equal(fieldFront(0, 800), 0);
assert.equal(fieldFront(FIELD_EXPAND_SECONDS, 800), 0);
assert.ok(fieldFront(1.4, 800) > 200 && fieldFront(1.4, 800) < 700, 'cascade front travels through the field');
assert.ok(fieldFront(2.3, 800) >= 800 * 0.99);

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

console.log('intro_model_test=PASS session, transitions, skip, collision, resize, release, fade, occupancy, frame-gate, burst, cascade');

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

  const word = [];
  for (let i = 0; i < 40; i++) word.push({ x: 20 + (i % 10) * 3, y: 40 + (i % 5) });
  for (let i = 0; i < 40; i++) word.push({ x: 80 + (i % 10) * 3, y: 42 + (i % 5) });
  for (let i = 0; i < 40; i++) word.push({ x: 140 + (i % 10) * 3, y: 41 + (i % 5) });
  for (let i = 0; i < 40; i++) word.push({ x: 200 + (i % 10) * 3, y: 43 + (i % 5) });
  const sources = Array.from({ length: 80 }, (_, i) => ({ x: i * 3, y: 50 }));
  const mapped = r.mapMorphTargets(sources, word, (i) => (i * 0.17) % 1);
  const xs = [...new Set(mapped.map((m) => Math.round(m.region.x)))];
  assertR.ok(xs.length > 8, `distributed regions uniqueX=${xs.length}`);

  const bounds = { width: 400, height: 800, glyphHeight: 10, glyphWidth: 8, gravity: 1000 };
  const wait = {
    role: 'structure', state: 'waiting', x: 120, y: 70, structX: 120, structY: 82,
    convergeFromX: 120, convergeFromY: 70, convergeStart: null, alpha: 0, seed: 0.2,
    vx: 0, vy: 0, catchable: false
  };
  assertR.equal(r.stepStructureParticle(wait, 1.0, bounds), true);
  assertR.equal(wait.state, 'waiting');
  assertR.equal(wait.alpha, 0);
  assertR.equal(wait.y, 70);
  assertR.equal(wait.convergeStart, null);
  r.stepStructureParticle(wait, 2.5, bounds);
  assertR.ok(wait.alpha < 0.5, 'no 0.5 alpha flash at entry');
  assertR.ok(wait.convergeStart == null || wait.convergeStart >= 2.2);
  r.stepStructureParticle(wait, 2.8, bounds);
  assertR.ok(wait.alpha < 0.85 && wait.alpha > 0, 'structure fades with reveal');
  r.stepStructureParticle(wait, 3.6, bounds);
  assertR.ok(Math.abs(wait.y - 82) < 1, 'settled by 3.6');
  r.stepStructureParticle(wait, 4.2, bounds);
  assertR.ok(wait.role === 'rain' || wait.state === 'dead' || wait.state === 'falling');
  const leftover = {
    role: 'structure', state: 'converging', x: 200, y: 400, structX: 200, structY: 400,
    convergeFromX: 200, convergeFromY: 390, convergeStart: 2.3, alpha: 1, seed: 0.05,
    vx: 0, vy: 0, catchable: false
  };
  r.stepStructureParticle(leftover, 4.2, bounds);
  assertR.ok(leftover.state === 'falling' || leftover.state === 'dead');
  const deadP = { x: 10, y: 10, vx: 0, vy: 20, spin: 0, angle: 0, alpha: 0, seed: 0.1, delay: 0, catchable: false, state: 'dead' };
  r.stepFallingParticle(deadP, 0.05, 5, [], bounds);
  assertR.equal(deadP.state, 'dead', 'no resurrection');

  const far = {
    role: 'structure', state: 'falling', x: 200, y: 700, structX: 40, structY: 40,
    alpha: 1, seed: 0.4, vx: 0, vy: 80, catchable: false, delay: 0
  };
  assertR.equal(r.stepStructureParticle(far, 2.3, bounds), false);
  assertR.equal(far.role, 'rain');

  const edge = { id: 'composer', left: 100, right: 300, y: 200, occupancy: new Map() };
  const perch = {
    x: 180, y: 170, vx: 0, vy: 700, spin: 0, angle: 0, alpha: 1, seed: 0.2, delay: 0,
    catchable: true, state: 'falling'
  };
  r.stepFallingParticle(perch, 0.05, 1, [edge], bounds);
  assertR.equal(perch.state, 'perched');
  const slot = r.occupancySlot(perch, edge, bounds);
  assertR.equal(edge.occupancy.get(slot), 1);
  perch.releaseAt = 2;
  r.stepFallingParticle(perch, 0.02, 3, [edge], bounds);
  assertR.equal(perch.state, 'falling');
  assertR.equal(edge.occupancy.get(slot) || 0, 0, 'perched occupancy released');

  const samples = r.selectMaskSamples(
    Array.from({ length: 3000 }, (_, i) => ({ x: (i % 100) * 8, y: Math.floor(i / 100) * 10 })),
    8, 10, 1600
  );
  assertR.ok(samples.length <= 1600 && samples.length > 200, `mask samples ${samples.length}`);

  const letters = r.clusterLetterTargets(word);
  assertR.equal(letters.length, 4, `dduo-like clusters split into letters, got ${letters.length}`);
  for (const m of mapped) {
    const dist = Math.hypot(m.region.x - m.target.x, m.region.y - m.target.y);
    assertR.ok(dist < 2, `morph region is the glyph target, dist=${dist}`);
  }

  const packed = r.layoutLogoLetters([100, 92, 88, 96], 6);
  assertR.equal(packed.gap, 6);
  assertR.ok(packed.gap / 100 <= 0.08, 'logo tracking stays tight relative to letter ink');
  assertR.equal(packed.starts[1], 106);
  assertR.equal(packed.total, 100 + 92 + 88 + 96 + 18);

  const veil = r.fieldGridMetrics(1440, 900, 13.2, 1820);
  assertR.ok(veil.count <= 1820, `field budget ${veil.count}`);
  assertR.ok(Math.abs(veil.cols * veil.stepX - 1440) < 1e-6, 'field covers width');
  assertR.ok(Math.abs(veil.rows * veil.stepY - 900) < 1e-6, 'field covers height');
  assertR.ok(veil.cols * veil.rows >= 900, 'field is a full-screen lattice, not a strip');

  const grid = r.logoGridMetrics(120, 400, 1100);
  assertR.ok(grid.stepY <= 120 / 12, `logo rows fill the cap-height stepY=${grid.stepY}`);
  const snapped = r.snapToLattice(13.2, 27.9, 8, 10);
  assertR.equal(snapped.x, r.snapToLattice(13.2 + 8, 27.9, 8, 10).x - 8);

  const filled = (x, y) => (x >= 20 && x <= 180 && y >= 30 && y <= 90 ? 1 : 0);
  const cells = r.sampleLatticeCoverage(filled, 220, 120, 8, 10, 0.36);
  assertR.ok(cells.length > 40, `filled logo lattice ${cells.length}`);
  for (const p of cells) {
    const s = r.snapToLattice(p.x, p.y, 8, 10);
    assertR.ok(Math.abs(s.x - p.x) < 1e-6 && Math.abs(s.y - p.y) < 1e-6, 'logo cells stay on the lattice');
  }
  const cellXs = cells.map((p) => p.x);
  const cellYs = cells.map((p) => p.y);
  const inner = cells.filter((p) => p.x > Math.min(...cellXs) + 8 && p.x < Math.max(...cellXs) - 8
    && p.y > Math.min(...cellYs) + 10 && p.y < Math.max(...cellYs) - 10);
  assertR.ok(inner.length > cells.length * 0.2, `strokes are filled cells, not an outline inner=${inner.length}/${cells.length}`);

  const quota = r.fillSampleQuota([{ x: 1, y: 1 }, { x: 2, y: 2 }, { x: 3, y: 3 }], 8);
  assertR.equal(quota.length, 3, 'quota never invents off-grid midpoints');

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

  const floorBounds = { width: 400, height: 800, glyphHeight: 10, glyphWidth: 8, gravity: 1000 };
  const left = {
    x: 40, y: 790, vx: 0, vy: 80, spin: 0, angle: 0, alpha: 1, seed: 0.95, delay: 0,
    catchable: false, state: 'falling'
  };
  const right = {
    x: 360, y: 790, vx: 0, vy: 80, spin: 0, angle: 0, alpha: 1, seed: 0.05, delay: 0,
    catchable: false, state: 'falling'
  };
  r.stepFallingParticle(left, 0.05, 2, [], floorBounds);
  r.stepFallingParticle(right, 0.05, 2, [], floorBounds);
  assertR.equal(left.state, 'exiting');
  assertR.equal(right.state, 'exiting');
  const leftY = left.y, rightY = right.y;
  r.stepFallingParticle(left, 0.2, 2.3, [], floorBounds);
  r.stepFallingParticle(right, 0.2, 2.3, [], floorBounds);
  assertR.ok(left.y > leftY && right.y > rightY, 'both sides keep drifting down');
  assertR.ok(left.alpha < 1 && right.alpha < 1, 'exit fades instead of stacking');

  const mid = {
    x: 200, y: 400, vx: 0, vy: 20, spin: 0, angle: 0, alpha: 1, seed: 0.4, delay: 0,
    catchable: true, state: 'falling', role: 'rain'
  };
  r.suppressMidBand(mid, 1.0, floorBounds);
  assertR.equal(mid.alpha, 1, 'mid-band stays visible before fade');
  r.suppressMidBand(mid, 2.8, floorBounds);
  assertR.ok(mid.alpha < 0.2, `mid-band clears before composer reveal alpha=${mid.alpha}`);

  const fieldBounds = { width: 400, height: 800, glyphWidth: 10, glyphHeight: 12 };
  const lockCells = new Set(['10:20']);
  const occupied = new Set();
  const rain = {
    x: 105, y: 245, vx: 0, vy: 100, seed: 0.2, delay: 0, alpha: 0.6,
    state: 'falling', role: 'field', glyph: '0', colX: 105
  };
  r.stepFieldParticle(rain, 0.01, 1.8, fieldBounds, {
    enabled: true, cells: lockCells, occupied, revealFade: 0
  });
  assertR.equal(rain.role, 'lock');
  assertR.ok(occupied.has('10:20'));
  assertR.ok(Math.abs(rain.lockX - 105) < 6);
  assertR.ok(rain.alpha <= 0.52 && rain.alpha >= 0.4, 'lock is half-transparent');
  const miss = {
    x: 50, y: 100, vx: 0, vy: 100, seed: 0.3, delay: 0, alpha: 0.6,
    state: 'falling', role: 'field', glyph: '8', colX: 50
  };
  r.stepFieldParticle(miss, 0.01, 1.8, fieldBounds, {
    enabled: true, cells: lockCells, occupied, revealFade: 0
  });
  assertR.equal(miss.role, 'field', 'non-overlapping rain does not lock');
  const dup = {
    x: 105, y: 245, vx: 0, vy: 100, seed: 0.4, delay: 0, alpha: 0.6,
    state: 'falling', role: 'field', glyph: 'M', colX: 105
  };
  r.stepFieldParticle(dup, 0.01, 1.8, fieldBounds, {
    enabled: true, cells: lockCells, occupied, revealFade: 0
  });
  assertR.equal(dup.role, 'field', 'a lock cell holds only one glyph');
  const lockBox = { enabled: true, cells: lockCells, occupied, lockTop: 200, lockBottom: 280, revealElapsed: 0.08 };
  const topLock = {
    role: 'lock', state: 'lock', lockX: 105, lockY: 205, x: 105, y: 205, baseAlpha: 0.5, alpha: 0.5
  };
  const bottomLock = {
    role: 'lock', state: 'lock', lockX: 105, lockY: 270, x: 105, y: 270, baseAlpha: 0.5, alpha: 0.5
  };
  r.stepFieldParticle(topLock, 0.02, 3, fieldBounds, lockBox);
  r.stepFieldParticle(bottomLock, 0.02, 3, fieldBounds, lockBox);
  assertR.ok(topLock.alpha < bottomLock.alpha, `lock wipes top-down top=${topLock.alpha} bottom=${bottomLock.alpha}`);
  const gone = { ...topLock, y: 205, lockY: 205, state: 'lock', role: 'lock', alpha: 0.5 };
  r.stepFieldParticle(gone, 0.02, 3, fieldBounds, { ...lockBox, revealElapsed: 0.22 });
  assertR.ok(gone.alpha < 0.08 || gone.state === 'dead', 'lock wipe finishes in about 0.2s');
  assertR.ok(r.lockWipeAlpha(205, 0, 200, 280, 0.5) === 0.5);
  assertR.ok(r.lockWipeAlpha(205, 0.12, 200, 280, 0.5) < r.lockWipeAlpha(270, 0.12, 200, 280, 0.5));

  const nearBottom = {
    x: 20, y: 790, vx: 0, vy: 80, seed: 0.2, delay: 0, alpha: 0.6,
    state: 'falling', role: 'field', colX: 20
  };
  r.stepFieldParticle(nearBottom, 0.05, 2, fieldBounds, {
    enabled: false, cells: new Set(), occupied: new Set()
  });
  assertR.equal(nearBottom.state, 'falling', 'field rain does not fade out before the bottom');
  assertR.ok(nearBottom.y > 790);
  const recycled = { role: 'field', state: 'dead', seed: 0.2, alpha: 0, targetAlpha: 0.62 };
  r.recycleFieldParticle(recycled, fieldBounds, -40);
  assertR.ok(recycled.y <= -40, 'recycle attaches above the live column');
  assertR.equal(recycled.state, 'falling');
  assertR.equal(recycled.alpha, 0.62, 'recycled rain is not faded in on screen');
  const onScreenRain = {
    x: 200, y: 120, vx: 0, vy: 80, seed: 0.2, delay: 0, alpha: 0.08,
    targetAlpha: 0.62, state: 'falling', role: 'field', colX: 200
  };
  r.stepFieldParticle(onScreenRain, 0.02, 1, fieldBounds, {
    enabled: false, cells: new Set(), occupied: new Set()
  });
  assertR.equal(onScreenRain.alpha, 0.62, 'on-screen rain must not fade in from transparent');

  const lockFill = r.lockCellsInRoundRect(100, 200, 200, 80, 12, 10, 10);
  assertR.ok(lockFill.length > 40, `input interior lock cells ${lockFill.length}`);
  assertR.ok(lockFill.includes('15:24'), 'a center cell of the input is lockable');
  const borderOnly = lockFill.filter((key) => key.startsWith('10:') || key.startsWith('29:'));
  assertR.ok(borderOnly.length < lockFill.length * 0.35, 'interior cells outnumber the rim');

  assertR.equal(r.shouldEnableLock(100, 400, 800), false);
  assertR.equal(r.shouldEnableLock(420, 400, 800), true);
  assertR.equal(r.shouldStartReveal({
    frontY: 100, height: 800, stepY: 10, lockCount: 0, lockCellCount: 0, firstLockAt: null, time: 1
  }), false, 'do not unveil while rain is still in the upper half');
  assertR.equal(r.shouldStartReveal({
    frontY: 790, height: 800, stepY: 10, lockCount: 0, lockCellCount: 0, firstLockAt: null, time: 2
  }), true, 'unveil after the curtain reaches the bottom');
  assertR.equal(r.shouldStartReveal({
    frontY: 790, height: 800, stepY: 10, lockCount: 0, lockCellCount: 20, firstLockAt: null, time: 2
  }), false, 'wait for lock glyphs when the input mask exists');
  assertR.equal(r.shouldStartReveal({
    frontY: 100, height: 800, stepY: 10, lockCount: 0, lockCellCount: 0, firstLockAt: null, time: 7
  }), true, 'timeout still unveils');

  assertR.equal(r.ASCII_RAMP, 'NOA86?<>/=+!:.');
  assertR.ok(!/[a-z·I-]/.test(r.ASCII_RAMP), 'ramp stays in the even-width set');
  assertR.ok(!r.FIELD_RAMP.includes('.'), 'rain omits the faintest ramp mark');
  let prevIdx = r.glyphIndexFor(0, null);
  for (let light = 0.05; light <= 1; light += 0.05) {
    const idx = r.glyphIndexFor(light, prevIdx);
    assertR.ok(idx <= prevIdx, `brighter light stays denser light=${light} ${prevIdx}->${idx}`);
    assertR.ok(prevIdx - idx <= 1, `luminance walks one step light=${light}`);
    prevIdx = idx;
  }
  const bandLight = 1 - 6 / r.ASCII_RAMP_LAST;
  const held = r.glyphIndexFor(bandLight, null);
  assertR.equal(held, 6);
  assertR.equal(r.glyphIndexFor(bandLight + 0.03, held), held, 'small light noise does not hop glyphs');
  const cols = 24, rows = 18;
  const bl = r.springSweepT(0, rows - 1, cols, rows);
  const midSweep = r.springSweepT(cols / 2, rows / 2, cols, rows);
  const tr = r.springSweepT(cols - 1, 0, cols, rows);
  assertR.ok(bl < midSweep && midSweep < tr, `sweep is bottom-left to top-right bl=${bl} mid=${midSweep} tr=${tr}`);
  const washOnAt = r.SPRING_LETTER_START + r.SPRING_SWEEP_PERIOD * 0.18;
  assertR.ok(r.springRampMix(bl, washOnAt) > r.springRampMix(tr, washOnAt),
    'wash-on densifies bottom-left before top-right');
  const washOffAt = r.SPRING_LETTER_START + r.SPRING_SWEEP_PERIOD * 0.68;
  assertR.ok(r.springRampMix(bl, washOffAt) < r.springRampMix(tr, washOffAt),
    'wash-off clears bottom-left before top-right');
  const wrapA = r.SPRING_LETTER_START + r.SPRING_SWEEP_PERIOD - 1 / 60;
  const wrapB = r.SPRING_LETTER_START + r.SPRING_SWEEP_PERIOD + 1 / 60;
  for (const sweep of [0, 0.5, 1]) {
    assertR.ok(r.springRampMix(sweep, wrapA) < 0.04, `mix is off before wrap sweep=${sweep}`);
    assertR.ok(r.springRampMix(sweep, wrapB) < 0.04, `mix is off after wrap sweep=${sweep}`);
    assertR.equal(r.springGlyphBias(r.springRampMix(sweep, wrapA)), 0);
  }
  let maxJump = 0;
  for (const sweep of [0, 0.35, 0.7, 1]) {
    for (let clock = 0; clock < r.SPRING_SWEEP_PERIOD * 3; clock += 1 / 60) {
      maxJump = Math.max(maxJump, Math.abs(
        r.springRampMix(sweep, clock + 1 / 60) - r.springRampMix(sweep, clock)
      ));
    }
  }
  assertR.ok(maxJump < 0.22, `ramp mix has no frame pop jump=${maxJump}`);

  assertR.ok(r.SPRING_RAMP.startsWith('NOA86'), 'spring ramp keeps the dense head');
  assertR.ok(r.SPRING_RAMP.includes('94523'), 'spring ramp adds intermediate densities');
  assertR.ok(!/[a-z·I-]/.test(r.SPRING_RAMP), 'spring ramp stays in the even-width set');
  assertR.equal(r.ASCII_RAMP, 'NOA86?<>/=+!:.', 'general ramp is unchanged');

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
  assertR.equal(cell.glyphIndex, 6, 'spring does not write ASCII glyphIndex');

  const t0 = 0.4;
  const shadePose = { turn: 0 };
  const centreShade = r.surfaceShade(0, 0.5, 0, shadePose);
  const rimShade = r.surfaceShade(0.4, 0.5, 0, shadePose);
  assertR.ok(centreShade < rimShade, 'shared surfaceShade stays dark centre / bright shoulder');
  assertR.equal(r.sculptureLight(0, shadePose, { coverage: 0.5, slope: 0 }), centreShade);

  const isolated = {
    col: 12, row: 9, u: 0, coverage: 0.5, light: 0.9, slope: 0, turn: 0,
    glyphIndex: 10, glyph: r.glyphAt(10), springIndex: null
  };
  r.stepSpringGlyph(isolated, t0, cols, rows);
  assertR.equal(isolated.glyphIndex, 10, 'core spring index stays off the ASCII ramp field');

  function sparseOf(u, turn, slope = 0, coverage = 0.5) {
    return r.springSparseAmount({ u, coverage, slope, turn, light: 0.9 });
  }
  const leftLit = sparseOf(-0.35, Math.PI / 2);
  const rightLit = sparseOf(0.35, Math.PI / 2);
  assertR.ok(rightLit < leftLit - 0.2, `turn π/2 densifies +u right=${rightLit} left=${leftLit}`);
  const leftBack = sparseOf(-0.35, -Math.PI / 2);
  const rightBack = sparseOf(0.35, -Math.PI / 2);
  assertR.ok(leftBack < rightBack - 0.2, `turn -π/2 densifies -u left=${leftBack} right=${rightBack}`);
  assertR.ok(Math.abs(sparseOf(0.12, 0.4) - sparseOf(0.14, 0.4)) < 0.12, 'sparse amount is spatially continuous');

  function densestU(turn) {
    let bestU = 0;
    let best = Infinity;
    for (let u = -0.45; u <= 0.45; u += 0.05) {
      const s = sparseOf(u, turn);
      if (s < best) { best = s; bestU = u; }
    }
    return bestU;
  }
  const densePos = densestU(Math.PI / 2);
  const denseNeg = densestU(-Math.PI / 2);
  assertR.ok(densePos > 0.05, `lit +u band moves off centre u=${densePos}`);
  assertR.ok(denseNeg < -0.05, `lit -u band moves off centre u=${denseNeg}`);
  assertR.ok(densePos - denseNeg > 0.2, `dense locus follows turn ${denseNeg}->${densePos}`);

  const lightIgnoredA = r.springSparseAmount({ u: 0.2, coverage: 0.5, slope: 0, turn: 0.8, light: 0.14 });
  const lightIgnoredB = r.springSparseAmount({ u: 0.2, coverage: 0.5, slope: 0, turn: 0.8, light: 0.9 });
  assertR.ok(Math.abs(lightIgnoredA - lightIgnoredB) < 1e-9, 'spring density is isolated from shared p.light');

  const glyphTurnA = {
    col: 12, row: 9, u: 0.32, coverage: 0.5, slope: 0, turn: Math.PI / 2,
    light: 0.5, glyphIndex: 10, glyph: r.glyphAt(10), springIndex: null
  };
  const glyphTurnB = { ...glyphTurnA, turn: -Math.PI / 2, springIndex: null };
  r.stepSpringGlyph(glyphTurnA, t0, cols, rows);
  r.stepSpringGlyph(glyphTurnB, t0, cols, rows);
  assertR.equal(glyphTurnA.glyphIndex, 10);
  assertR.ok(glyphTurnA.springIndex < glyphTurnB.springIndex,
    `facing side stays denser ${glyphTurnA.springIndex}<${glyphTurnB.springIndex}`);

  const edgeW = 1440;
  const edgeCell = 12;
  const cov = 0.5;
  const cellU = edgeCell / (edgeW * 0.5);
  assertR.equal(r.springEdgeAlpha(0, cov, edgeCell, edgeW), 1);
  assertR.equal(r.springEdgeAlpha(cov + 0.03, cov, edgeCell, edgeW), 0);
  assertR.equal(r.springEdgeAlpha(cov + 0.03 - 2 * cellU, cov, edgeCell, edgeW), 1);
  const midEdge = r.springEdgeAlpha(cov + 0.03 - 0.5 * cellU, cov, edgeCell, edgeW);
  assertR.ok(midEdge > 0.05 && midEdge < 0.95, `one-cell edge fade mid=${midEdge}`);
  const formedPose = r.sculpturePose(1.2);
  const formedCentre = r.evaluateSurfaceCell(720, 450, 1440, 900, formedPose);
  assertR.ok(formedPose.formed && formedCentre.inside, 'formed centre stays inside the silhouette');

  const heldSpring = {
    col: 10, row: 10, u: 0.12, coverage: 0.5, light: 0.32,
    glyphIndex: 5, glyph: r.glyphAt(5), springIndex: null
  };
  r.stepSpringGlyph(heldSpring, t0, cols, rows);
  const heldGlyph = heldSpring.glyph;
  const heldIdx = heldSpring.springIndex;
  heldSpring.light += 0.02;
  r.stepSpringGlyph(heldSpring, t0, cols, rows);
  assertR.equal(heldSpring.glyph, heldGlyph, 'tiny light change does not flicker');
  assertR.equal(heldSpring.springIndex, heldIdx);
  heldSpring.u += 0.004;
  r.stepSpringGlyph(heldSpring, t0 + 1 / 60, cols, rows);
  assertR.equal(heldSpring.springIndex, heldIdx, 'tiny spatial/clock change does not flicker');

  const cloneA = {
    col: 8, row: 8, u: 0.16, coverage: 0.5, light: 0.28,
    glyphIndex: 3, glyph: r.glyphAt(3), springIndex: null
  };
  const cloneB = { ...cloneA };
  r.stepSpringGlyph(cloneA, 1.2, cols, rows);
  r.stepSpringGlyph(cloneB, 1.2, cols, rows);
  assertR.equal(cloneA.glyph, cloneB.glyph);
  assertR.equal(cloneA.springIndex, cloneB.springIndex);

  const cores = [
    { col: 12, row: 9, u: 0, coverage: 0.5, light: 0.16, glyphIndex: 10, glyph: r.glyphAt(10), springIndex: null },
    { col: 11, row: 8, u: 0.1, coverage: 0.5, light: 0.18, glyphIndex: 2, glyph: r.glyphAt(2), springIndex: null },
    { col: 10, row: 10, u: 0.2, coverage: 0.5, light: 0.22, glyphIndex: 8, glyph: r.glyphAt(8), springIndex: null }
  ];
  for (let i = 0; i < 12; i++) {
    for (const p of cores) r.stepSpringGlyph(p, 2, cols, rows);
  }
  const settled = cores.map((p) => p.springIndex);
  assertR.ok(new Set(settled).size >= 2, `core cells are not one glyph ${settled.join(',')}`);
  let changed = 0;
  for (const p of cores) {
    const before = p.springIndex;
    r.stepSpringGlyph(p, 2 + 1 / 60, cols, rows);
    if (p.springIndex !== before) changed++;
  }
  assertR.ok(changed < cores.length, `one frame does not switch every core cell changed=${changed}`);
  const later = 2 + r.SPRING_CORE_STEP;
  for (let i = 0; i < 8; i++) {
    for (const p of cores) r.stepSpringGlyph(p, later, cols, rows);
  }
  const afterStep = cores.map((p) => p.springIndex);
  assertR.ok(new Set(afterStep).size >= 2, `core step does not unify the body ${afterStep.join(',')}`);

  const rim = {
    col: 22, row: 2, u: 0.48, coverage: 0.5, light: 0.75,
    glyphIndex: null, glyph: '?', springIndex: null
  };
  const rimSeen = new Set();
  for (let clock = 0; clock < r.SPRING_SWEEP_PERIOD * 2; clock += 1 / 60) {
    r.stepSpringGlyph(rim, clock, cols, rows);
    rimSeen.add(rim.glyph);
  }
  assertR.ok(rimSeen.size > 1, `rim still mixes size=${rimSeen.size}`);
  const axisCell = {
    col: 12, row: 9, u: 0, coverage: 0.5, slope: 0, turn: 0,
    light: 0.16, glyphIndex: 10, glyph: r.glyphAt(10), springIndex: null
  };
  const rimAtRest = {
    col: 22, row: 2, u: 0.48, coverage: 0.5, slope: 0, turn: 0,
    light: 0.75, glyphIndex: null, glyph: '?', springIndex: null
  };
  r.stepSpringGlyph(axisCell, t0, cols, rows);
  r.stepSpringGlyph(rimAtRest, t0, cols, rows);
  assertR.ok(rimAtRest.springIndex > axisCell.springIndex,
    `turn-0 glancing rim stays sparser than camera-facing axis ${axisCell.springIndex}->${rimAtRest.springIndex}`);

  console.log('intro_model_repair_test=PASS coverage, grid, morph, structure-lifecycle, occupancy, exit, inset, field-lock, ascii-ramp');
}
