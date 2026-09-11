export const INTRO_SESSION_KEY = 'dduo:intro:seen:v1';
export const MORPH_SECONDS = 0.35;
export const MORPH_HOLD_SECONDS = 0.5;
export const RASTER_SCAN_SECONDS = 0.5;
export const RASTER_HOLD_SECONDS = 0.1;
export const REVEAL_FADE_SECONDS = 0.7;
export const DISPERSE_SECONDS = 0.6;
export const MORPH_END = MORPH_SECONDS + MORPH_HOLD_SECONDS;
export const RASTER_END = MORPH_END + RASTER_SCAN_SECONDS + RASTER_HOLD_SECONDS;
export const REVEAL_DELAY_SECONDS = RASTER_END;
export const INTRO_SECONDS = RASTER_END + REVEAL_FADE_SECONDS;
export const DISPERSE_GRAVITY = 1.6;
export const DISPERSE_OUTWARD = 0.8;
export const DISPERSE_JITTER = 0.4;
export const DISPERSE_LIFT = 0.5;
export const PHYSICS_DT = 1 / 60;
export const MAX_PHYSICS_STEPS = 4;
export const DECRYPT_DURATION = 0.6;
export const DECRYPT_HZ = 30;
export const GLITCH_CHARS = '$MBNQØW@&R8GD6S9ÖOH#ÉE5UK0ÄÅA2XP34ZC%VIF17YTJL*';
export const REVEAL_EASING = [1, 0.02, 0.37, 0.97];
export const SURFACE_CYCLE = 8.4;
export const TARGET_FRAME_MS = 1000 / 60;
export const AXIS_PERIOD = 2;
export const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));
export const smooth = (value) => { const t = clamp(value); return t * t * (3 - 2 * t); };
export const ASCII_RAMP = 'NOA86?<>/=+!:.';
export const ASCII_RAMP_LAST = ASCII_RAMP.length - 1;
export const SPRING_RAMP = 'NOA8694523?!<>=+/:.';
export const SPRING_RAMP_LAST = SPRING_RAMP.length - 1;
export const SPRING_SWEEP_PERIOD = 6.4;
export const SPRING_LETTER_START = 0.85;
export const SPRING_RAMP_BIAS = 3;
export const SPRING_CORE_ORIGIN = ASCII_RAMP.indexOf('/');
export const SPRING_CORE_STEP = 0.58;
const SPRING_WASH = 0.36;
const SPRING_HOLD_ON = 0.50;
const SPRING_HOLD_OFF = 0.86;
const SPRING_BAND = 0.08;
const GLYPH_HOLD = 0.62;
const CORE_INNER = 0.46;
const CORE_OUTER = 0.82;
const SPRING_RADIAL_WEIGHT = 0.22;
const SPRING_LIGHT_Y = 0.22;
const SPRING_INSIDE_PAD = 0.03;

export function springSweepT(col, row, cols, rows) {
  const u = col / Math.max(1, cols - 1);
  const rise = (rows - 1 - row) / Math.max(1, rows - 1);
  return clamp(0.5 * (u + rise));
}

export function springRampMix(sweepT, clock, period = SPRING_SWEEP_PERIOD) {
  if (clock < SPRING_LETTER_START) return 0;
  const wave = (((clock - SPRING_LETTER_START) / period) % 1 + 1) % 1;
  const span = 1 + SPRING_BAND;
  if (wave < SPRING_WASH) {
    const front = (wave / SPRING_WASH) * span;
    return smooth(clamp((front - sweepT) / SPRING_BAND));
  }
  if (wave < SPRING_HOLD_ON) return 1;
  if (wave < SPRING_HOLD_OFF) {
    const front = ((wave - SPRING_HOLD_ON) / (SPRING_HOLD_OFF - SPRING_HOLD_ON)) * span;
    return 1 - smooth(clamp((front - sweepT) / SPRING_BAND));
  }
  return 0;
}

export function asciiRawIndex(light, bias = 0) {
  return (1 - clamp(light)) * ASCII_RAMP_LAST + bias;
}

export function glyphIndexFor(light, prev = null, bias = 0) {
  const raw = clamp(asciiRawIndex(light, bias), 0, ASCII_RAMP_LAST);
  const nearest = Math.round(raw);
  if (prev == null || !Number.isFinite(prev)) return nearest;
  const held = Math.round(clamp(prev, 0, ASCII_RAMP_LAST));
  if (Math.abs(raw - held) < GLYPH_HOLD) return held;
  return held + Math.sign(nearest - held);
}

export function glyphAt(index) {
  return ASCII_RAMP[Math.min(ASCII_RAMP_LAST, Math.max(0, index | 0))];
}

export function springGlyphAt(index) {
  return SPRING_RAMP[Math.min(SPRING_RAMP_LAST, Math.max(0, index | 0))];
}

export function springFacingAmount(u, coverage, slope = 0, turn = 0) {
  const radius = Math.max(1e-4, coverage);
  const xn = clamp(u / radius, -0.999, 0.999);
  const z = Math.sqrt(Math.max(0, 1 - xn * xn));
  const ny = -(Number.isFinite(slope) ? slope : 0) * 0.55;
  const inv = 1 / (Math.hypot(xn, ny, z) || 1);
  const nx = xn * inv;
  const nz = z * inv;
  const t = Number.isFinite(turn) ? turn : 0;
  const lx = Math.sin(t);
  const ly = SPRING_LIGHT_Y;
  const lz = Math.cos(t);
  const linv = 1 / (Math.hypot(lx, ly, lz) || 1);
  const ndotl = nx * lx * linv + ny * inv * ly * linv + nz * lz * linv;
  return clamp(0.5 + 0.5 * ndotl);
}

export function springSparseAmount(p) {
  const coverage = Number(p.coverage) > 1e-4 ? Number(p.coverage) : 1;
  const u = Number.isFinite(p.u) ? p.u : 0;
  const radial = clamp(Math.abs(u) / coverage);
  const slope = Number.isFinite(p.slope) ? p.slope : 0;
  const turn = Number.isFinite(p.turn) ? p.turn : 0;
  const dirSparse = 1 - springFacingAmount(u, coverage, slope, turn);
  return clamp(SPRING_RADIAL_WEIGHT * radial + (1 - SPRING_RADIAL_WEIGHT) * dirSparse);
}

export function springEdgeAlpha(u, coverage, cellX, width) {
  const limit = (Number.isFinite(coverage) ? coverage : 0) + SPRING_INSIDE_PAD;
  const absU = Math.abs(Number.isFinite(u) ? u : 0);
  if (!(absU < limit)) return 0;
  const half = Math.max(1e-4, Number(width) * 0.5);
  const cellU = Math.max(1e-4, Number(cellX) / half);
  return smooth(clamp((limit - absU) / cellU));
}

export function springRawIndex(sparse, bias = 0) {
  return clamp(sparse) * SPRING_RAMP_LAST + bias;
}

export function springGlyphIndexFor(sparse, prev = null, bias = 0) {
  const raw = clamp(springRawIndex(sparse, bias), 0, SPRING_RAMP_LAST);
  const nearest = Math.round(raw);
  if (prev == null || !Number.isFinite(prev)) return nearest;
  const held = Math.round(clamp(prev, 0, SPRING_RAMP_LAST));
  if (Math.abs(raw - held) < GLYPH_HOLD) return held;
  return held + Math.sign(nearest - held);
}

export function springGlyphBias(mix, seedOffset = 0) {
  return -Math.round(clamp(mix) * SPRING_RAMP_BIAS) + seedOffset;
}

export function springCoreBlend(u, coverage) {
  if (!(coverage > 1e-4)) return 1;
  const radial = Math.abs(u) / coverage;
  return smooth(clamp((radial - CORE_INNER) / Math.max(1e-4, CORE_OUTER - CORE_INNER)));
}

export function pingPongIndex(steps, last) {
  const span = Math.max(1, last);
  const path = span * 2;
  const pos = ((steps % path) + path) % path;
  return pos <= span ? pos : path - pos;
}

export function springCoreIndex(clock, start = SPRING_LETTER_START, step = SPRING_CORE_STEP) {
  const t = Math.max(0, clock - start);
  const steps = Math.floor(t / Math.max(1e-4, step));
  return pingPongIndex(steps + SPRING_CORE_ORIGIN, ASCII_RAMP_LAST);
}

export function springCoreGlyph(clock) {
  return glyphAt(springCoreIndex(clock));
}

export function stepSpringGlyph(p, clock, cols, rows) {
  const mix = springRampMix(springSweepT(p.col, p.row, cols, rows), clock);
  const sparse = springSparseAmount(p);
  const bias = -clamp(mix) * 0.9;
  const prev = Number.isFinite(p.springIndex) ? p.springIndex : null;
  const idx = springGlyphIndexFor(sparse, prev, bias);
  p.springIndex = idx;
  p.glyph = springGlyphAt(idx);
  p.rampMix = mix;
  return idx;
}

export const SURFACE_MAX_COVERAGE = 0.68;
export const SURFACE_KEYS = [
  { radial: 0.30, height: 1.46, waist: 0.42, turn: 0.00 },
  { radial: 0.52, height: 1.22, waist: 0.92, turn: 0.55 },
  { radial: 0.68, height: 0.98, waist: 0.34, turn: 1.10 },
  { radial: 0.36, height: 1.42, waist: 0.95, turn: 1.60 }
];

function mixKey(a, b, t, key) {
  return a[key] + (b[key] - a[key]) * t;
}

export function wrapAxis(value, period = AXIS_PERIOD) {
  return value - period * Math.floor(value / period + 0.5);
}

export function sculpturePose(clock, pointerX = 0) {
  const t = Math.max(0, clock);
  const phase = ((t / SURFACE_CYCLE) % 1 + 1) % 1;
  const x = phase * 4;
  const i = Math.floor(x) % 4;
  const u = smooth(x - Math.floor(x));
  const a = SURFACE_KEYS[i];
  const b = SURFACE_KEYS[(i + 1) % 4];
  void pointerX;
  return {
    radial: mixKey(a, b, u, 'radial'),
    height: mixKey(a, b, u, 'height'),
    waist: mixKey(a, b, u, 'waist'),
    turn: mixKey(a, b, u, 'turn') + t * 0.11,
    travel: t * 0.26 + (1 - smooth(t / 1.35)) * 0.85,
    formed: t > 1.05,
    cycle: phase,
    index: i
  };
}

function radiusFromAxis(axis, pose) {
  const lobe = Math.max(1e-4, Math.abs(Math.sin(axis * Math.PI)));
  const pinched = Math.pow(lobe, 0.38 + pose.waist * 0.72);
  const columnMix = clamp(1.05 - pose.radial * 1.55);
  return pinched * (1 - columnMix) + 0.52 * columnMix;
}

export function sculptureRadius(v, clockOrPose) {
  const pose = typeof clockOrPose === 'object' && clockOrPose ? clockOrPose : sculpturePose(clockOrPose || 0);
  return radiusFromAxis(wrapAxis(v + (pose.travel || 0)), pose);
}

export function radiusSlope(v, pose, eps = 0.04) {
  const axis = v + (pose.travel || 0);
  return (radiusFromAxis(axis + eps, pose) - radiusFromAxis(axis - eps, pose)) / (2 * eps);
}

export function surfaceShade(u, coverage, slope, pose) {
  const radius = Math.max(1e-4, coverage);
  const xn = clamp(u / radius, -0.999, 0.999);
  const z = Math.sqrt(Math.max(0, 1 - xn * xn));
  const ny = -slope * 0.55;
  const inv = 1 / (Math.hypot(xn, ny, z) || 1);
  const nz = z * inv;
  const fresnel = 1 - Math.abs(nz);
  const theta = Math.atan2(z, xn);
  const bands = 0.5 + 0.5 * Math.sin(theta * 2 + (pose.turn || 0));
  return clamp(0.14 + 0.64 * fresnel + 0.2 * bands * fresnel);
}

export function sculptureLight(u, pose, extras = {}) {
  const coverage = extras.coverage ?? extras.radius ?? pose.radial ?? 1;
  const slope = extras.slope ?? 0;
  return surfaceShade(u, coverage, slope, pose);
}

export function sculptureTwist(v, clock, pointerX = 0) {
  void pointerX;
  return sculpturePose(clock, 0).turn + v * 0.18;
}

export function screenAxis(y, height, pose) {
  return ((y - height / 2) / Math.max(1, height)) * (2.35 / Math.max(0.45, pose.height));
}

export function evaluateSurfaceCell(x, y, width, height, pose) {
  const cx = width / 2;
  const v = screenAxis(y, height, pose);
  const u = (x - cx) / Math.max(1, width * 0.5);
  const radius = sculptureRadius(v, pose);
  const coverage = Math.min(radius * pose.radial, SURFACE_MAX_COVERAGE);
  const edge = (coverage + 0.03 - Math.abs(u)) / 0.08;
  const slope = radiusSlope(v, pose);
  const light = surfaceShade(u, Math.max(coverage, 1e-4), slope, pose);
  return { u, v, radius, coverage, edge: clamp(edge), inside: edge > 0, light, slope };
}

export function introGridMetrics(width, height, quality = 0, glyphWidth = 0) {
  const mobile = width <= 768;
  const budget = mobile ? (quality >= 2 ? 1800 : quality ? 2400 : 3000)
    : (quality >= 2 ? 3800 : quality ? 5400 : 7200);
  let fontSize = mobile ? 11 : 13.2;
  let cellX = 0, cellY = 0, cols = 8, rows = 8;
  for (let i = 0; i < 28; i++) {
    cellY = fontSize * 1.22;
    cellX = glyphWidth > 0 ? Math.max(glyphWidth, fontSize * 0.55) : fontSize * 0.62;
    cols = Math.max(8, Math.floor(width / cellX));
    rows = Math.max(8, Math.floor(height / cellY));
    if (cols * rows <= budget) break;
    fontSize += 0.35;
  }
  cellX = width / cols;
  cellY = height / rows;
  return {
    cols, rows, cellX, cellY,
    count: cols * rows,
    fontSize: Math.min(cellX * 0.92, cellY * 0.78)
  };
}

export function stepSpring(p, dt, stiffness = 28, damping = 9.5) {
  const t = Math.min(Math.max(dt, 0), 0.04);
  p.vx += ((p.targetX - p.x) * stiffness - p.vx * damping) * t;
  p.vy += ((p.targetY - p.y) * stiffness - p.vy * damping) * t;
  p.x += p.vx * t;
  p.y += p.vy * t;
}

export function applyCursorImpulse(p, mouseX, mouseY, radius, strength, dt) {
  const dx = p.x - mouseX, dy = p.y - mouseY;
  const distSq = dx * dx + dy * dy;
  if (distSq > radius * radius || distSq < 1) return;
  const dist = Math.sqrt(distSq);
  const falloff = 1 - dist / radius;
  const mag = strength * falloff * falloff * dt;
  p.vx += (dx / dist) * mag;
  p.vy += (dy / dist) * mag;
}

export function physicsSteps(elapsedSeconds) {
  const elapsed = Math.max(0, Number(elapsedSeconds) || 0);
  const steps = [];
  let remaining = elapsed;
  for (let i = 0; i < MAX_PHYSICS_STEPS && remaining > 0; i++) {
    const dt = Math.min(PHYSICS_DT, remaining);
    steps.push(dt);
    remaining -= dt;
  }
  return steps;
}

export function morphArrive(progress) {
  return smooth(clamp(progress));
}

export function morphPoint(from, to, progress) {
  const t = morphArrive(progress);
  return {
    x: from.x + (to.x - from.x) * t,
    y: from.y + (to.y - from.y) * t
  };
}

export function matchMorphTargets(sources, targets) {
  const src = [...(sources || [])].sort((a, b) => a.x - b.x || a.y - b.y);
  const dst = [...(targets || [])].sort((a, b) => a.x - b.x || a.y - b.y);
  if (!dst.length) {
    return src.map((s) => ({
      from: s,
      to: { x: s.x, y: s.y, glyph: s.glyph, alpha: 0, light: s.light },
      role: 'fade'
    }));
  }
  if (!src.length) {
    const origin = { x: 0.5, y: 0.5, glyph: 'N', alpha: 0, light: 0.6 };
    return dst.map((t) => ({ from: origin, to: t, role: 'spawn' }));
  }
  const pairs = [];
  const assigned = new Array(src.length).fill(false);
  for (let k = 0; k < dst.length; k++) {
    const si = Math.round(k * (src.length - 1) / Math.max(1, dst.length - 1));
    const role = assigned[si] ? 'spawn' : 'land';
    assigned[si] = true;
    pairs.push({ from: src[si], to: dst[k], role });
  }
  for (let i = 0; i < src.length; i++) {
    if (assigned[i]) continue;
    const ti = Math.round(i * (dst.length - 1) / Math.max(1, src.length - 1));
    pairs.push({ from: src[i], to: dst[ti], role: 'fade' });
  }
  return pairs;
}

export function playPhaseAt(elapsed) {
  if (elapsed < MORPH_END) return 'morph';
  if (elapsed < RASTER_END) return 'raster';
  if (elapsed < INTRO_SECONDS) return 'fade';
  return 'done';
}

export function createDisperseParticle({ x, y, glyph, light, alpha, randA, randB }) {
  const nx = Number(x) || 0;
  const ny = Number(y) || 0;
  const a = Number.isFinite(randA) ? randA : 0.5;
  const b = Number.isFinite(randB) ? randB : 0.5;
  const startAlpha = Number.isFinite(alpha) ? alpha : 1;
  return {
    x: nx,
    y: ny,
    glyph: glyph || 'N',
    light: Number.isFinite(light) ? light : 0.6,
    startAlpha,
    alpha: startAlpha,
    vx: (nx - 0.5) * DISPERSE_OUTWARD + (a - 0.5) * DISPERSE_JITTER,
    vy: -b * DISPERSE_LIFT,
    visible: startAlpha >= 0.04
  };
}

export function stepDisperseParticle(p, dt, elapsed) {
  if (!p || p.visible === false) return p;
  const step = Math.min(PHYSICS_DT, Math.max(0, dt));
  p.vy += DISPERSE_GRAVITY * step;
  p.x += p.vx * step;
  p.y += p.vy * step;
  const life = clamp(elapsed / DISPERSE_SECONDS);
  p.alpha = p.startAlpha * (1 - life);
  if (p.alpha < 0.04 || p.x < 0 || p.x > 1 || p.y < 0 || p.y > 1) p.visible = false;
  return p;
}

function sampleBezierX(t, x1, x2) {
  const ax = 3 * x1 - 3 * x2 + 1;
  const bx = 3 * x2 - 6 * x1;
  const cx = 3 * x1;
  return ((ax * t + bx) * t + cx) * t;
}

function sampleBezierY(t, y1, y2) {
  const ay = 3 * y1 - 3 * y2 + 1;
  const by = 3 * y2 - 6 * y1;
  const cy = 3 * y1;
  return ((ay * t + by) * t + cy) * t;
}

function sampleBezierDX(t, x1, x2) {
  const ax = 3 * x1 - 3 * x2 + 1;
  const bx = 3 * x2 - 6 * x1;
  const cx = 3 * x1;
  return (3 * ax * t + 2 * bx) * t + cx;
}

export function cubicBezierEase(x1, y1, x2, y2) {
  return (x) => {
    if (x <= 0) return 0;
    if (x >= 1) return 1;
    let t = x;
    for (let i = 0; i < 8; i++) {
      const err = sampleBezierX(t, x1, x2) - x;
      const dx = sampleBezierDX(t, x1, x2);
      if (Math.abs(err) < 1e-6 || Math.abs(dx) < 1e-6) break;
      t = clamp(t - err / dx);
    }
    return sampleBezierY(t, y1, y2);
  };
}

export const revealEase = cubicBezierEase(...REVEAL_EASING);

export function revealProgress(elapsed) {
  const t = (Number(elapsed) - REVEAL_DELAY_SECONDS) / REVEAL_FADE_SECONDS;
  return revealEase(clamp(t));
}

export function isDecryptKept(ch) {
  return ch == null || ch === '' || /\s/u.test(ch);
}

export function sampleDecryptText(text, progress, random = Math.random) {
  const chars = Array.from(text ?? '');
  if (!(progress < 1)) return chars.join('');
  const payload = chars.filter((ch) => !isDecryptKept(ch));
  const total = payload.length;
  let seen = 0;
  return chars.map((ch) => {
    if (isDecryptKept(ch)) return ch;
    const threshold = total ? (seen + 1) / total : 1;
    seen += 1;
    if (progress >= threshold) return ch;
    const idx = Math.floor(random() * GLITCH_CHARS.length) % GLITCH_CHARS.length;
    return GLITCH_CHARS.charAt(Math.max(0, idx)) || ch;
  }).join('');
}

export function createDecryptPlayback({
  text,
  duration = DECRYPT_DURATION,
  interval = 1 / DECRYPT_HZ,
  random = Math.random,
  getPaused = () => false,
  onUpdate,
  onComplete
} = {}) {
  const source = String(text ?? '');
  let raf = 0;
  let cancelled = false;
  let startedAt = null;
  let lastGlyphAt = -Infinity;
  let pauseAt = null;

  function frame(now) {
    if (cancelled) return;
    raf = 0;
    if (getPaused?.()) {
      if (pauseAt == null) pauseAt = now;
      raf = requestAnimationFrame(frame);
      return;
    }
    if (pauseAt != null) {
      const held = now - pauseAt;
      if (startedAt != null) startedAt += held;
      if (Number.isFinite(lastGlyphAt)) lastGlyphAt += held;
      pauseAt = null;
    }
    if (startedAt == null) startedAt = now;
    const progress = clamp((now - startedAt) / (Math.max(1e-4, duration) * 1000));
    if (progress >= 1) {
      onUpdate?.(source);
      onComplete?.();
      return;
    }
    if (now - lastGlyphAt >= Math.max(1, interval * 1000)) {
      lastGlyphAt = now;
      onUpdate?.(sampleDecryptText(source, progress, random));
    }
    raf = requestAnimationFrame(frame);
  }

  return {
    start() {
      if (cancelled) return;
      raf = requestAnimationFrame(frame);
    },
    cancel() {
      cancelled = true;
      cancelAnimationFrame(raf);
      raf = 0;
    }
  };
}

export function createFrameGate(targetMs = TARGET_FRAME_MS) {
  let primed = false;
  let lastDrawAt = 0;
  let carry = 0;
  return {
    accept(now) {
      if (!primed) {
        primed = true;
        lastDrawAt = now;
        carry = 0;
        return { draw: true, deltaSeconds: targetMs / 1000 };
      }
      const elapsed = now - lastDrawAt;
      if (elapsed + carry < targetMs) return { draw: false, deltaSeconds: 0 };
      carry = Math.min(targetMs, elapsed + carry - targetMs);
      lastDrawAt = now;
      return { draw: true, deltaSeconds: Math.min(Math.max(elapsed, 0) / 1000, 0.1) };
    },
    reset() {
      primed = false;
      lastDrawAt = 0;
      carry = 0;
    }
  };
}

export function createIntroSession(storage = () => globalThis.sessionStorage) {
  let seenInMemory = false;
  return {
    shouldPlay(reducedMotion = false) {
      if (reducedMotion || seenInMemory) return false;
      try { return storage()?.getItem(INTRO_SESSION_KEY) !== '1'; } catch { return true; }
    },
    finish() {
      seenInMemory = true;
      try { storage()?.setItem(INTRO_SESSION_KEY, '1'); } catch { /* Private storage can be unavailable. */ }
    }
  };
}

export const introSession = createIntroSession();

export function createIntroTimeline() {
  let phase = 'idle';
  let elapsed = 0;
  function syncPhase() {
    if (phase === 'idle') return;
    phase = playPhaseAt(elapsed);
  }
  return {
    get phase() { return phase; },
    get elapsed() { return elapsed; },
    advance() {
      if (phase !== 'idle') return false;
      phase = 'morph';
      elapsed = 0;
      return true;
    },
    tick(delta) {
      if (phase === 'idle' || phase === 'done') return;
      elapsed += Math.max(0, delta);
      if (elapsed >= INTRO_SECONDS) elapsed = INTRO_SECONDS;
      syncPhase();
    },
    setElapsed(value) {
      if (phase === 'idle') return;
      elapsed = Math.max(0, Number(value) || 0);
      if (elapsed >= INTRO_SECONDS) elapsed = INTRO_SECONDS;
      syncPhase();
    },
    skip() { phase = 'done'; elapsed = INTRO_SECONDS; }
  };
}
