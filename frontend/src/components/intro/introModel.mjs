export const INTRO_SESSION_KEY = 'dduo:intro:seen:v1';
export const MORPH_SECONDS = 1.6;
export const FIELD_EXPAND_SECONDS = 0.5;
export const END_SECONDS = 10;
export const FIELD_START = 0;
export const FIELD_LOCK_START = 1.5;
export const LOCK_HOLD = 0.45;
export const LOCK_FADE = 0.2;
export const REVEAL_START = 8;
export const REVEAL_SPAN = 0.75;
export const FIELD_REVEAL_TIMEOUT = 6.5;
export const MID_FADE_START = 1.6;
export const MID_FADE_SPAN = 1.2;
export const SURFACE_CYCLE = 8.4;
export const TARGET_FRAME_MS = 1000 / 60;
export const AXIS_PERIOD = 2;
export const STRUCTURE_SCAN_START = 2.2;
export const STRUCTURE_SCAN_SPAN = 1.4;
export const STRUCTURE_RELEASE = 4.2;
export const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));
export const smooth = (value) => { const t = clamp(value); return t * t * (3 - 2 * t); };
export const ASCII_RAMP = 'NOA86?<>/=+!:.';
export const ASCII_RAMP_LAST = ASCII_RAMP.length - 1;
export const FIELD_RAMP = 'NOA86?<>/=+!:';
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

// Four circulating envelopes. Travel only slides the periodic profile through a
// stable screen grid; it must not translate the whole body off-screen.
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

// Dark centre, bright shoulders: projected-normal fresnel of a surface of revolution.
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

export function fieldGridMetrics(width, height, fontSize = 13, maxCount = 1400) {
  const w = Math.max(32, width);
  const h = Math.max(32, height);
  let cols = Math.max(12, Math.floor(w / Math.max(7, fontSize * 0.52)));
  let rows = Math.max(10, Math.floor(h / Math.max(9, fontSize * 0.82)));
  while (cols * rows > maxCount && (cols > 14 || rows > 10)) {
    if (cols / Math.max(1, rows) > w / h && cols > 14) cols -= 1;
    else if (rows > 10) rows -= 1;
    else cols -= 1;
  }
  const stepX = w / cols;
  const stepY = h / rows;
  return {
    cols,
    rows,
    stepX,
    stepY,
    count: cols * rows,
    drawSize: Math.min(stepX / 0.56, stepY * 0.98)
  };
}

export function selectMaskSamples(points, cellX, cellY, maxCount = 1600) {
  if (!points?.length) return [];
  const gx = Math.max(4, cellX || 8);
  const gy = Math.max(6, cellY || 10);
  const buckets = new Map();
  for (const point of points) {
    const key = `${Math.round(point.x / gx)}:${Math.round(point.y / gy)}`;
    if (!buckets.has(key)) buckets.set(key, point);
  }
  const unique = [...buckets.values()];
  if (unique.length <= maxCount) return unique;
  const picked = [];
  const step = unique.length / maxCount;
  for (let i = 0; i < maxCount; i++) {
    picked.push(unique[Math.min(unique.length - 1, Math.floor(i * step))]);
  }
  return picked;
}

export function isLocalStructure(p, height) {
  if (p.structX == null || p.structY == null) return false;
  if (p.y - p.structY > Math.max(40, height * 0.2)) return false;
  return Math.hypot(p.x - p.structX, p.y - p.structY) <= Math.max(64, height * 0.26);
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

export function burstOffset(seedA, seedB, radius) {
  const angle = seedA * Math.PI * 2;
  const dist = (0.2 + seedB * 0.8) * radius;
  return { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist * 0.82 };
}

function lerpPoint(a, b, t) {
  return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
}

export const LOGO_GLYPHS = 'O08M';

export function logoGlyphFor(light) {
  const t = clamp(light ?? 0.7);
  if (t > 0.8) return 'M';
  if (t > 0.6) return '8';
  if (t > 0.44) return '0';
  return 'O';
}

export function layoutLogoLetters(inkWidths, gap) {
  const widths = inkWidths.map((w) => Math.max(0, w));
  const total = widths.reduce((sum, w) => sum + w, 0) + gap * Math.max(0, widths.length - 1);
  const starts = [];
  let x = 0;
  for (const w of widths) {
    starts.push(x);
    x += w + gap;
  }
  return { total, starts, gap };
}

export function logoGridMetrics(wordHeight, wordWidth, maxCount = 1100) {
  const h = Math.max(24, wordHeight);
  const w = Math.max(24, wordWidth);
  let rows = 18;
  for (let i = 0; i < 10; i++) {
    const stepY = h / rows;
    const stepX = stepY * 0.62;
    const cols = Math.max(1, Math.ceil(w / stepX));
    if (rows * cols <= maxCount * 2.2) {
      return {
        stepX,
        stepY,
        drawSize: Math.min(stepX / 0.56, stepY * 1.02),
        rows,
        cols
      };
    }
    rows -= 1;
  }
  const stepY = h / 8;
  const stepX = stepY * 0.62;
  return { stepX, stepY, drawSize: Math.min(stepX / 0.56, stepY * 1.02), rows: 8, cols: Math.ceil(w / stepX) };
}

export function snapToLattice(x, y, stepX, stepY) {
  const ox = stepX / 2;
  const oy = stepY / 2;
  return {
    x: ox + Math.round((x - ox) / stepX) * stepX,
    y: oy + Math.round((y - oy) / stepY) * stepY
  };
}

export function sampleLatticeCoverage(alphaAt, width, height, stepX, stepY, minCoverage = 0.36) {
  const points = [];
  const insetX = stepX * 0.28;
  const insetY = stepY * 0.28;
  for (let y = stepY / 2; y < height; y += stepY) {
    for (let x = stepX / 2; x < width; x += stepX) {
      let sum = 0;
      let n = 0;
      for (const dy of [-insetY, 0, insetY]) {
        for (const dx of [-insetX, 0, insetX]) {
          const a = alphaAt(x + dx, y + dy);
          if (a == null) continue;
          sum += a;
          n++;
        }
      }
      const light = n ? sum / n : 0;
      if (light >= minCoverage) {
        const snapped = snapToLattice(x, y, stepX, stepY);
        points.push({ x: snapped.x, y: snapped.y, light });
      }
    }
  }
  return points;
}

// Burst, then one coalesce onto the glyph. A mid-path rest at a contracted
// letter region reads as a stuttered second formation.
export function morphSample(progress, from, along, region, target) {
  const p = clamp(progress);
  const end = target || region;
  if (p <= 0.22) return lerpPoint(from, along, smooth(p / 0.22));
  return lerpPoint(along, end, smooth((p - 0.22) / 0.78));
}

export function clusterLetterTargets(word) {
  if (!word.length) return [];
  const sorted = [...word].sort((a, b) => a.x - b.x || a.y - b.y);
  const minX = sorted[0].x;
  const maxX = sorted[sorted.length - 1].x;
  const span = Math.max(1, maxX - minX);
  const bins = Math.max(24, Math.min(160, Math.round(span / 4)));
  const hist = new Array(bins).fill(0);
  const byBin = Array.from({ length: bins }, () => []);
  for (const point of sorted) {
    const b = Math.min(bins - 1, Math.floor(((point.x - minX) / span) * bins));
    hist[b]++;
    byBin[b].push(point);
  }
  const valley = Math.max(...hist, 1) * 0.12;
  const groups = [];
  let current = [];
  let inLetter = false;
  for (let b = 0; b < bins; b++) {
    if (hist[b] > valley) {
      current.push(...byBin[b]);
      inLetter = true;
    } else if (inLetter) {
      if (current.length) groups.push(current);
      current = [];
      inLetter = false;
    }
  }
  if (current.length) groups.push(current);
  return groups.length ? groups : [sorted];
}

export function fillSampleQuota(points, quota) {
  if (!points.length || quota <= 0) return [];
  if (points.length <= quota) return points.slice();
  const out = [];
  for (let i = 0; i < quota; i++) {
    out.push(points[Math.min(points.length - 1, Math.floor(i * points.length / quota))]);
  }
  return out;
}

export function balanceLetterTargets(word) {
  const groups = clusterLetterTargets(word);
  if (!groups.length) return [];
  if (groups.length === 1) return groups;
  const peak = Math.max(...groups.map((group) => group.length));
  const quota = Math.max(12, Math.round(peak * 0.94));
  return groups.map((group) => fillSampleQuota(group, quota));
}

export function mapMorphTargets(sources, word, noiseFn = () => 0.5) {
  void noiseFn;
  if (!sources.length || !word.length) return [];
  const dst = word.map((point) => ({
    target: point,
    region: { x: point.x, y: point.y, light: point.light }
  })).sort((a, b) => a.target.x - b.target.x || a.target.y - b.target.y);
  const srcOrder = sources.map((_, i) => i).sort((a, b) => sources[a].x - sources[b].x || sources[a].y - sources[b].y);
  const assigned = new Array(sources.length);
  for (let k = 0; k < sources.length; k++) {
    const sample = dst[Math.min(dst.length - 1, Math.floor(k * dst.length / sources.length))];
    assigned[srcOrder[k]] = sample;
  }
  return assigned;
}

export function fieldFront(time, height) {
  return smooth(clamp((time - FIELD_EXPAND_SECONDS) / 1.7)) * height * 1.05;
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
export const phaseAt = (seconds) => seconds < 0.2 ? 'falling'
  : seconds < 8 ? 'field'
    : seconds < 8.75 ? 'reveal'
      : seconds < END_SECONDS ? 'clearing' : 'done';
export const workspaceOpacity = (seconds) => smooth((seconds - REVEAL_START) / REVEAL_SPAN);
export const revealProgress = (elapsed, span = REVEAL_SPAN) => elapsed <= 0 ? 0 : smooth(elapsed / span);

export function fieldFrontY(particles) {
  let max = -Infinity;
  if (!particles) return max;
  for (const p of particles) {
    if (p.state === 'dead') continue;
    if (p.y > max) max = p.y;
  }
  return max;
}

export function lockWipeAlpha(y, revealElapsed, lockTop, lockBottom, base = 0.5) {
  if (!(revealElapsed > 0)) return base;
  const top = Number.isFinite(lockTop) ? lockTop : y;
  const bottom = Number.isFinite(lockBottom) && lockBottom > top ? lockBottom : top + 8;
  const span = Math.max(8, bottom - top);
  const rowT = clamp((y - top) / span);
  const soft = 0.22;
  const front = clamp(revealElapsed / LOCK_FADE) * (1 + soft);
  return base * clamp(1 + (rowT - front) / soft);
}

export function shouldEnableLock(frontY, inputBottom, height) {
  const gate = Number.isFinite(inputBottom) ? inputBottom : height * 0.45;
  return frontY >= gate;
}

export function shouldStartReveal({
  frontY, height, stepY, lockCount, lockCellCount, firstLockAt, time, timeout = FIELD_REVEAL_TIMEOUT
}) {
  if (time >= timeout) return true;
  const filled = frontY >= height - 2 * Math.max(4, stepY || 12);
  const locksOk = (lockCount || 0) > 0 || !(lockCellCount > 0);
  const held = firstLockAt == null ? filled : (time - firstLockAt) >= LOCK_HOLD;
  return filled && locksOk && held;
}

export function createIntroTimeline() {
  let phase = 'sculpture';
  let elapsed = 0;
  return {
    get phase() { return phase; },
    get elapsed() { return elapsed; },
    advance() {
      if (phase !== 'sculpture' && phase !== 'word') return false;
      phase = phase === 'sculpture' ? 'morph' : 'falling';
      elapsed = 0;
      return true;
    },
    tick(delta) {
      if (phase === 'sculpture' || phase === 'word' || phase === 'done') return;
      elapsed += Math.max(0, delta);
      if (phase === 'morph') {
        if (elapsed >= MORPH_SECONDS) { phase = 'word'; elapsed = 0; }
      } else if (phase === 'reveal' || phase === 'clearing') {
        if (elapsed >= REVEAL_SPAN + 1.8) phase = 'done';
        else if (elapsed >= REVEAL_SPAN) phase = 'clearing';
      } else {
        phase = elapsed < 0.2 ? 'falling' : 'field';
      }
    },
    unveil() {
      if (phase === 'done' || phase === 'reveal' || phase === 'clearing') return false;
      phase = 'reveal';
      elapsed = 0;
      return true;
    },
    skip() { phase = 'done'; }
  };
}

export function assignStructureTargets(particles, outline, height) {
  if (!particles?.length || !outline?.length) return;
  const maxLift = Math.max(48, height * 0.28);
  const candidates = [];
  for (let i = 0; i < particles.length; i++) {
    const p = particles[i];
    if (p.state === 'dead' || p.state === 'perched' || p.role === 'structure') continue;
    candidates.push(p);
  }
  candidates.sort((a, b) => a.y - b.y || a.x - b.x);
  const points = [...outline].sort((a, b) => a.y - b.y || a.x - b.x);
  const cap = Math.min(points.length, Math.floor(particles.length * 0.42), 900);
  const used = new Set();
  let ci = 0;
  for (let i = 0; i < cap; i++) {
    const point = points[Math.floor(i * points.length / cap)];
    let best = -1;
    let bestDist = Infinity;
    const start = Math.max(0, ci - 8);
    for (let k = start; k < candidates.length; k++) {
      if (used.has(k)) continue;
      const p = candidates[k];
      if (p.y - point.y > maxLift) continue;
      const dist = Math.hypot(p.x - point.x, p.y - point.y);
      if (dist < bestDist) { bestDist = dist; best = k; if (k > ci) ci = k; }
      if (p.y > point.y + maxLift) break;
    }
    if (best < 0 || bestDist > height * 0.42) continue;
    used.add(best);
    const p = candidates[best];
    p.role = 'structure';
    p.structX = point.x;
    p.structY = point.y;
    p.convergeFromX = null;
    p.convergeFromY = null;
    p.convergeStart = null;
  }
}

export function stepStructureParticle(p, time, bounds) {
  if (p.role !== 'structure' || p.state === 'dead') return false;
  if (p.state !== 'waiting' && p.state !== 'converging' && !isLocalStructure(p, bounds.height)) {
    p.role = 'rain';
    p.catchable = false;
    return false;
  }
  if (time >= STRUCTURE_RELEASE) {
    const residual = (p.seed || 0) < 0.18;
    p.role = 'rain';
    p.catchable = false;
    p.convergeFromX = p.convergeFromY = p.convergeStart = null;
    if (!residual) {
      p.state = 'dead';
      p.alpha = 0;
      return true;
    }
    p.state = 'falling';
    p.vy = Math.max(p.vy || 0, 55 + (p.seed || 0) * 40);
    p.vx = ((p.seed || 0) - 0.5) * 28;
    p.alpha = Math.max(p.alpha, 0.35);
    return false;
  }
  const scan = smooth((time - STRUCTURE_SCAN_START) / STRUCTURE_SCAN_SPAN) * bounds.height;
  const beforeScan = time < STRUCTURE_SCAN_START || (p.structY ?? p.y) > scan;
  if (beforeScan && time < 3.6) {
    p.state = 'waiting';
    if (p.convergeFromX == null) {
      p.convergeFromX = p.structX ?? p.x;
      p.convergeFromY = (p.structY ?? p.y) - Math.max(6, (bounds.glyphHeight || 10) * 0.45);
    }
    p.x = p.convergeFromX;
    p.y = p.convergeFromY;
    p.vx = 0;
    p.vy = 0;
    p.alpha = 0;
    p.convergeStart = null;
    return true;
  }
  if (p.convergeStart == null || Number.isNaN(p.convergeStart)) {
    if (p.convergeFromX == null) {
      p.convergeFromX = p.x;
      p.convergeFromY = p.y;
    }
    p.convergeStart = time;
    p.state = 'converging';
  }
  const local = smooth((time - p.convergeStart) / Math.max(0.12, 3.6 - p.convergeStart));
  p.x = p.convergeFromX + (p.structX - p.convergeFromX) * local;
  p.y = p.convergeFromY + (p.structY - p.convergeFromY) * local;
  p.vx = 0;
  p.vy = 0;
  p.state = 'converging';
  if (time >= 3.6) {
    p.x = p.structX;
    p.y = p.structY;
    p.alpha = 1 - workspaceOpacity(time);
  } else {
    p.alpha = local;
  }
  return true;
}

export function thinParticlesSpatially(particles, surfaces, bounds) {
  const seen = new Set();
  const gx = Math.max(4, bounds.glyphWidth || 8);
  const gy = Math.max(6, bounds.glyphHeight || 10);
  return particles.filter((p) => {
    if (p.state === 'dead') return false;
    if (p.role === 'structure' || p.state === 'perched') return true;
    const key = `${Math.round(p.x / gx)}:${Math.round(p.y / gy)}`;
    if (seen.has(key)) {
      releasePerchedOccupancy(p, surfaces, bounds);
      return false;
    }
    seen.add(key);
    return true;
  });
}

export function beginExit(p, time, bounds) {
  if (p.state === 'exiting' || p.state === 'dead') return;
  p.state = 'exiting';
  p.exitAt = time;
  p.exitSpeed = 28 + (p.seed || 0) * 27;
  p.vx *= 0.12;
  p.vy = p.exitSpeed;
  p.angle = 0;
  p.catchable = false;
  const hold = Math.max(2, (bounds.glyphHeight || 10) * 0.2);
  p.y = Math.min(p.y, bounds.height - hold);
}

export function suppressMidBand(p, time, bounds) {
  if (p.state === 'dead' || p.state === 'perched' || p.state === 'exiting') return;
  if (p.role === 'structure' || p.role === 'field' || p.role === 'lock' || p.edgeId != null) return;
  if (time < MID_FADE_START) return;
  const fade = smooth((time - MID_FADE_START) / MID_FADE_SPAN);
  if (fade <= 0) return;
  const y0 = bounds.height * 0.22;
  const y1 = bounds.height * 0.72;
  if (p.y < y0 || p.y > y1) return;
  p.alpha = Math.min(p.alpha, 1 - fade);
  p.catchable = false;
  if (fade > 0.45) p.vy = Math.max(p.vy || 0, 80);
  if (p.alpha <= 0.02) {
    p.state = 'dead';
    p.alpha = 0;
  }
}

// Swept feet-to-edge collision prevents tunnelling even at reduced frame rates.
export function stepFallingParticle(p, dt, time, surfaces, bounds) {
  if (time < p.delay || p.state === 'dead') return;
  const half = bounds.glyphHeight / 2;
  if (p.state === 'perched') {
    const edge = surfaces.find((item) => item.id === p.edgeId);
    if (edge && time < p.releaseAt) {
      p.x = edge.left + p.edgeFraction * (edge.right - edge.left);
      p.y = edge.y - half - (p.edgeLevel || 0) * bounds.glyphHeight * 0.85 - Math.abs(Math.sin((time - p.landedAt) * 14))
        * 5 * Math.exp(-(time - p.landedAt) * 8);
      return;
    }
    releasePerchedOccupancy(p, surfaces, bounds);
    p.state = 'falling';
    p.vx = (p.seed < 0.5 ? -1 : 1) * (10 + p.seed * 16);
    p.vy = 40;
  }
  if (p.state === 'exiting') {
    p.vy = p.exitSpeed || 40;
    p.vx *= Math.max(0, 1 - dt * 8);
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    const gone = Math.max(0, (p.y - (bounds.height - bounds.glyphHeight)) / Math.max(12, bounds.glyphHeight * 2.4));
    const timed = (time - (p.exitAt || time)) / (0.95 + (p.seed || 0) * 0.3);
    p.alpha = 1 - smooth(Math.max(gone, timed));
    if (p.y > bounds.height + bounds.glyphHeight || p.alpha <= 0) {
      p.state = 'dead';
      p.alpha = 0;
    }
    return;
  }
  const previousFeet = p.y + half;
  p.vy += bounds.gravity * dt;
  if (p.y > bounds.height * 0.72) p.vy = Math.max(p.vy, 0);
  if (p.y > bounds.height - bounds.glyphHeight * 3.5) p.vy = Math.min(p.vy, 140);
  p.x += p.vx * dt;
  p.y += p.vy * dt;
  p.angle += p.spin * dt;
  p.x = clamp(p.x, half, bounds.width - half);
  if (p.catchable) {
    let landing = null;
    for (const edge of surfaces) {
      if (edge.id !== p.edgeId && p.x >= edge.left && p.x <= edge.right
        && previousFeet <= edge.y && p.y + half >= edge.y
        && (!landing || edge.y < landing.y)) {
        const slot = occupancySlot(p, edge, bounds);
        if ((edge.occupancy?.get(slot) || 0) < 2) landing = edge;
      }
    }
    if (landing) {
      p.state = 'perched'; p.edgeId = landing.id;
      const slot = occupancySlot(p, landing, bounds);
      landing.occupancy ??= new Map();
      p.edgeLevel = landing.occupancy.get(slot) || 0;
      landing.occupancy.set(slot, p.edgeLevel + 1);
      p.edgeFraction = (p.x - landing.left) / Math.max(1, landing.right - landing.left);
      p.y = landing.y - half - p.edgeLevel * bounds.glyphHeight * 0.85; p.landedAt = time;
      p.releaseAt = time + 0.25 + p.seed * 0.3;
      p.angle = 0; p.vy = 0;
      p.catchable = false;
      return;
    }
  }
  if (p.y >= bounds.height - bounds.glyphHeight * 2.2) beginExit(p, time, bounds);
}

export function fieldCellKey(x, y, cellX, cellY) {
  const gx = Math.max(1, cellX || 8);
  const gy = Math.max(1, cellY || 10);
  return `${Math.floor(x / gx)}:${Math.floor(y / gy)}`;
}

export function lockCellsInRoundRect(left, top, width, height, radius, stepX, stepY) {
  const cells = [];
  const gx = Math.max(1, stepX || 8);
  const gy = Math.max(1, stepY || 10);
  const right = left + Math.max(0, width);
  const bottom = top + Math.max(0, height);
  const r = Math.max(0, Math.min(radius || 0, width / 2, height / 2));
  const col0 = Math.floor(left / gx);
  const col1 = Math.floor((right - 1e-6) / gx);
  const row0 = Math.floor(top / gy);
  const row1 = Math.floor((bottom - 1e-6) / gy);
  for (let row = row0; row <= row1; row++) {
    for (let col = col0; col <= col1; col++) {
      const cx = (col + 0.5) * gx;
      const cy = (row + 0.5) * gy;
      if (cx < left || cx > right || cy < top || cy > bottom) continue;
      const dx = cx < left + r ? left + r - cx : cx > right - r ? cx - (right - r) : 0;
      const dy = cy < top + r ? top + r - cy : cy > bottom - r ? cy - (bottom - r) : 0;
      if (dx && dy && dx * dx + dy * dy > r * r) continue;
      cells.push(`${col}:${row}`);
    }
  }
  return cells;
}

export function recycleFieldParticle(p, bounds, aboveY = 0) {
  if (p.role === 'lock') return false;
  const gy = bounds.glyphHeight || 10;
  p.role = 'field';
  p.state = 'falling';
  p.vy = (bounds.height || 800) * (0.5 + (p.seed || 0) * 0.06);
  p.y = aboveY - gy;
  p.vx = 0;
  p.alpha = p.targetAlpha ?? 0.62;
  p.delay = 0;
  p.lockX = p.lockY = p.lockAt = p.lockKey = null;
  return true;
}

export function stepFieldParticle(p, dt, time, bounds, lock = null) {
  if (p.state === 'dead') return;
  if (time < (p.delay || 0)) {
    p.alpha = 0;
    return;
  }
  const cellX = bounds.glyphWidth || 8;
  const cellY = bounds.glyphHeight || 10;
  if (p.role === 'lock') {
    p.x = p.lockX ?? p.x;
    p.y = p.lockY ?? p.y;
    p.vx = 0;
    p.vy = 0;
    const base = p.baseAlpha ?? 0.5;
    p.alpha = lockWipeAlpha(p.y, lock?.revealElapsed ?? 0, lock?.lockTop, lock?.lockBottom, base);
    if (p.alpha <= 0.02) {
      p.state = 'dead';
      p.alpha = 0;
    }
    return;
  }
  if (p.state === 'exiting') {
    p.vy = p.exitSpeed || 40;
    p.y += p.vy * dt;
    const gone = Math.max(0, (p.y - (bounds.height - cellY)) / Math.max(12, cellY * 2.4));
    const timed = (time - (p.exitAt || time)) / (0.8 + (p.seed || 0) * 0.3);
    p.alpha = Math.min(p.alpha || 1, 1 - smooth(Math.max(gone, timed)));
    if (p.y > bounds.height + cellY || p.alpha <= 0) {
      p.state = 'dead';
      p.alpha = 0;
    }
    return;
  }
  p.x = p.colX != null ? p.colX : p.x;
  p.y += (p.vy || 110) * dt;
  const target = p.targetAlpha ?? 0.62;
  if (p.y < 0) p.alpha = Math.min(target, (p.alpha || 0) + dt * 4);
  else p.alpha = target;
  if (lock?.enabled && lock.cells?.size) {
    const key = fieldCellKey(p.x, p.y, cellX, cellY);
    if (lock.cells.has(key) && !lock.occupied.has(key)) {
      lock.occupied.add(key);
      const col = Math.floor(p.x / cellX);
      const row = Math.floor(p.y / cellY);
      p.role = 'lock';
      p.lockKey = key;
      p.lockX = (col + 0.5) * cellX;
      p.lockY = (row + 0.5) * cellY;
      p.lockAt = time;
      p.x = p.lockX;
      p.y = p.lockY;
      p.baseAlpha = 0.5;
      p.alpha = 0.5;
      return;
    }
  }
  if (p.y > bounds.height + cellY) {
    p.state = 'dead';
    p.alpha = 0;
  }
}

export function occupancySlot(p, edge, bounds) {
  return Math.floor((p.x - edge.left) / (bounds.glyphWidth || bounds.glyphHeight * 0.6));
}

export function releasePerchedOccupancy(p, surfaces, bounds) {
  if (p.state !== 'perched' || p.edgeId == null) return;
  const edge = surfaces.find((item) => item.id === p.edgeId);
  if (!edge?.occupancy) return;
  const slot = occupancySlot(p, edge, bounds);
  const count = edge.occupancy.get(slot) || 0;
  if (count <= 1) edge.occupancy.delete(slot);
  else edge.occupancy.set(slot, count - 1);
}
