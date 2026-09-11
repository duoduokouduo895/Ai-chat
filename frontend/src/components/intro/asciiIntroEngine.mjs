import {
  clamp, sculpturePose, evaluateSurfaceCell, introGridMetrics,
  stepSpring, applyCursorImpulse, stepSpringGlyph, springEdgeAlpha,
  createIntroTimeline, physicsSteps, createDisperseParticle, stepDisperseParticle,
  revealProgress, morphPoint, matchMorphTargets, morphArrive,
  MORPH_SECONDS, MORPH_END, RASTER_SCAN_SECONDS, REVEAL_DELAY_SECONDS, SPRING_RAMP
} from './introModel.mjs';
import { sampleWordmark } from './dduoMark.mjs';
import { buildWorkspaceRaster, rasterScanFront, rasterCellAlpha } from './workspaceRaster.mjs';

const noise = (i) => { const n = Math.sin(i * 127.1 + 31.7) * 43758.5453; return n - Math.floor(n); };

function qaConfig() {
  try {
    if (import.meta.env?.DEV) return globalThis.__INTRO_QA__ || {};
  } catch { /* Node tests and production builds omit the QA hook. */ }
  return null;
}

export function createAsciiIntroEngine({ canvas, getLayout, onPhase, onRevealStart, onComplete, onError }) {
  const ctx = canvas.getContext('2d', { alpha: true });
  if (!ctx) throw new Error('Canvas 2D is unavailable');
  const timeline = createIntroTimeline();
  const style = getComputedStyle(canvas);
  const foreground = style.getPropertyValue('--text-primary').trim() || '#f4f4f5';
  const background = style.getPropertyValue('--canvas').trim() || '#0c0c0d';
  const mono = style.getPropertyValue('--font-mono').trim() || 'Consolas, monospace';
  let width = 0, height = 0, cellX = 7, cellY = 12, fontSize = 11, sculptureDrawSize = 11;
  let lattice = [], particles = [];
  let wordmark = null, raster = { cells: [], drawSize: 11, stepY: 12 };
  let rasterStarted = false, morphFromSize = 11;
  let raf = 0, clock = 0, lastNow = null, disposed = false, dirty = true, finished = false;
  let revealNotified = false;
  let latticeCols = 0, latticeRows = 0;
  let previousPhase = '';
  let quality = 0, slowFrames = 0, averageFrame = 16.7, measuredFrames = 0;
  let mouseActive = false, mousePx = 0, mousePy = 0;

  function notify() {
    const scene = timeline.phase;
    if (previousPhase !== scene) {
      previousPhase = scene;
      onPhase?.(scene);
    }
    if (!revealNotified && scene !== 'idle' && timeline.elapsed + 1e-6 >= REVEAL_DELAY_SECONDS) {
      revealNotified = true;
      onRevealStart?.();
    }
  }

  function complete() {
    if (finished) return;
    finished = true;
    dispose();
    onComplete?.();
  }

  function publishQa() {
    const qa = qaConfig();
    if (!qa && !(import.meta.env && import.meta.env.DEV)) return;
    canvas.dataset.introClock = clock.toFixed(3);
    canvas.dataset.introElapsed = timeline.elapsed.toFixed(3);
    canvas.dataset.introPhase = timeline.phase;
  }

  function seedLattice(grid = introGridMetrics(width, height, quality, cellX)) {
    latticeCols = grid.cols;
    latticeRows = grid.rows;
    cellX = grid.cellX;
    cellY = grid.cellY;
    fontSize = grid.fontSize;
    const prevFont = ctx.font;
    const candidate = Math.max(fontSize, cellY * 0.84);
    ctx.font = `${candidate}px ${mono}`;
    const springMetrics = ctx.measureText(SPRING_RAMP);
    const springCount = Math.max(1, SPRING_RAMP.length);
    const springCharW = springMetrics.width / springCount;
    const springAscent = springMetrics.actualBoundingBoxAscent;
    const springDescent = springMetrics.actualBoundingBoxDescent;
    const springCharH = Number.isFinite(springAscent) && Number.isFinite(springDescent)
      ? Math.max(1e-4, springAscent + springDescent)
      : candidate;
    const springFit = Math.min(
      springCharW > 0 ? (cellX * 0.94) / springCharW : 1,
      (cellY * 0.84) / springCharH
    );
    sculptureDrawSize = Math.max(1, candidate * springFit);
    ctx.font = prevFont;
    lattice = [];
    for (let row = 0; row < grid.rows; row++) {
      for (let col = 0; col < grid.cols; col++) {
        const restX = (col + 0.5) * cellX;
        const restY = (row + 0.5) * cellY;
        const seed = noise(row * 97 + col + 3);
        lattice.push({
          col, row, seed,
          restX, restY,
          x: restX, y: restY,
          vx: 0, vy: 0,
          targetX: restX, targetY: restY,
          light: 0.6, glyph: 'N',
          springIndex: null,
          alpha: 0,
          appearAt: (1 - row / Math.max(1, grid.rows - 1)) * 0.9 + seed * 0.08
        });
      }
    }
  }

  function readLayout() {
    try { return getLayout?.(canvas.getBoundingClientRect()) || []; }
    catch { return []; }
  }

  function refreshStructure() {
    wordmark = sampleWordmark(width, height);
    raster = buildWorkspaceRaster(width, height, readLayout());
  }

  function retargetWordmark() {
    wordmark = sampleWordmark(width, height);
    const layout = wordmark.layout;
    for (const p of particles) {
      if (p.toU == null || p.toV == null) continue;
      p.toX = (layout.x + p.toU * layout.w) / width;
      p.toY = (layout.y + p.toV * layout.h) / height;
    }
  }

  function measure() {
    const rect = canvas.getBoundingClientRect();
    const oldWidth = width, oldHeight = height;
    width = Math.max(1, rect.width);
    height = Math.max(1, rect.height);
    ctx.font = `${(width <= 768 ? 11 : 13.2)}px ${mono}`;
    const probe = Math.max(
      ctx.measureText('N').width,
      ctx.measureText('A').width,
      ctx.measureText('8').width,
      ctx.measureText('O').width,
      8
    );
    const grid = introGridMetrics(width, height, quality, probe);
    fontSize = grid.fontSize;
    cellX = grid.cellX;
    cellY = grid.cellY;
    ctx.font = `${fontSize}px ${mono}`;
    let widest = probe;
    for (const ch of 'NOA8') widest = Math.max(widest, ctx.measureText(ch).width);
    if (widest > cellX) fontSize *= (cellX / widest) * 0.98;
    const mobile = width <= 768;
    const ratio = Math.min(window.devicePixelRatio || 1, mobile ? 1.25 : quality >= 2 ? 1.25 : 1.75);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'center';
    ctx.font = `${fontSize}px ${mono}`;
    if (!lattice.length || latticeCols !== grid.cols || latticeRows !== grid.rows) seedLattice(grid);
    else {
      for (const p of lattice) {
        p.restX = (p.col + 0.5) * cellX;
        p.restY = (p.row + 0.5) * cellY;
        p.targetX = p.restX;
        p.targetY = p.restY;
      }
    }
    if (oldWidth && oldHeight && (oldWidth !== width || oldHeight !== height) && timeline.phase === 'idle') {
      const sx = width / oldWidth, sy = height / oldHeight;
      for (const p of lattice) {
        p.x *= sx; p.y *= sy; p.vx *= sx; p.vy *= sy;
      }
    }
    if (timeline.phase === 'idle') refreshStructure();
    else if (oldWidth && oldHeight && (oldWidth !== width || oldHeight !== height)) {
      retargetWordmark();
      raster = buildWorkspaceRaster(width, height, readLayout());
    }
    dirty = false;
  }

  function pointerX() {
    if (!mouseActive || !width) return 0;
    return clamp(mousePx / width * 2 - 1, -1, 1);
  }

  function clickMetrics() {
    const formed = sculpturePose(clock, pointerX()).formed;
    if (!formed) return { formed: false, halfW: 0, halfH: 0 };
    ctx.font = `600 ${Math.max(11, fontSize)}px ${mono}`;
    const metrics = ctx.measureText('CLICK');
    const ascent = Number.isFinite(metrics.actualBoundingBoxAscent)
      ? metrics.actualBoundingBoxAscent : Math.max(11, fontSize) * 0.72;
    const descent = Number.isFinite(metrics.actualBoundingBoxDescent)
      ? metrics.actualBoundingBoxDescent : Math.max(11, fontSize) * 0.2;
    return {
      formed: true,
      halfW: Math.max(1, metrics.width) * 0.5,
      halfH: Math.max(1, ascent + descent) * 0.5
    };
  }

  function drawnAlpha(p, hole) {
    let alpha = p.alpha;
    if (hole.formed && Math.abs(p.x - width / 2) <= hole.halfW && Math.abs(p.y - height / 2) <= hole.halfH) {
      alpha *= 0.62;
    }
    return alpha;
  }

  function stepLattice(dt) {
    if (!lattice.length) seedLattice();
    const pose = sculpturePose(clock, pointerX());
    for (let i = 0; i < lattice.length; i++) {
      const p = lattice[i];
      if (clock + 0.02 < p.appearAt) { p.alpha = 0; continue; }
      const sample = evaluateSurfaceCell(p.restX, p.restY, width, height, pose);
      p.targetX = p.restX;
      p.targetY = p.restY;
      p.light = sample.light;
      p.u = sample.u;
      p.coverage = sample.coverage;
      p.slope = sample.slope;
      p.turn = pose.turn;
      stepSpringGlyph(p, clock, latticeCols, latticeRows);
      stepSpring(p, dt, 20, 8);
      if (mouseActive) applyCursorImpulse(p, mousePx, mousePy, Math.min(width, height) * 0.14, 1600, dt);
      const edgeA = sample.inside ? springEdgeAlpha(sample.u, sample.coverage, cellX, width) : 0;
      p.alpha += (edgeA - p.alpha) * Math.min(1, dt * 7);
    }
    return pose;
  }

  function snapshotVisible() {
    const hole = clickMetrics();
    const sources = [];
    for (const p of lattice) {
      const alpha = drawnAlpha(p, hole);
      if (alpha < 0.08) continue;
      if (p.x < -cellX || p.x > width + cellX || p.y < -cellY || p.y > height + cellY) continue;
      sources.push({
        x: p.x / width,
        y: p.y / height,
        glyph: p.glyph,
        light: p.light,
        alpha
      });
    }
    return sources;
  }

  function beginMorph() {
    refreshStructure();
    morphFromSize = sculptureDrawSize;
    const pairs = matchMorphTargets(snapshotVisible(), wordmark.cells);
    particles = pairs.map((pair) => ({
      x: pair.from.x,
      y: pair.from.y,
      fromX: pair.from.x,
      fromY: pair.from.y,
      toX: pair.to.x,
      toY: pair.to.y,
      toU: pair.to.u,
      toV: pair.to.v,
      glyph: pair.from.glyph,
      fromGlyph: pair.from.glyph,
      toGlyph: pair.to.glyph || pair.from.glyph,
      light: pair.from.light,
      toLight: pair.to.light ?? pair.from.light,
      startAlpha: pair.role === 'spawn' ? 0 : pair.from.alpha,
      targetAlpha: pair.role === 'fade' ? 0 : (pair.to.alpha ?? 1),
      role: pair.role,
      randA: Math.random(),
      randB: Math.random(),
      visible: true,
      size: sculptureDrawSize,
      vx: 0,
      vy: 0
    }));
  }

  function stepMorph(elapsed) {
    const t = clamp(elapsed / MORPH_SECONDS);
    const blend = morphArrive(clamp((t - 0.5) / 0.5));
    const toSize = wordmark?.drawSize || morphFromSize;
    for (const p of particles) {
      const pos = morphPoint({ x: p.fromX, y: p.fromY }, { x: p.toX, y: p.toY }, t);
      p.x = pos.x;
      p.y = pos.y;
      if (p.role === 'fade') p.alpha = p.startAlpha * (1 - t);
      else if (p.role === 'spawn') p.alpha = p.targetAlpha * morphArrive(t);
      else p.alpha = p.startAlpha + (p.targetAlpha - p.startAlpha) * blend;
      if (blend >= 0.5) {
        p.glyph = p.toGlyph;
        p.light = p.toLight;
      }
      p.size = morphFromSize + (toSize - morphFromSize) * blend;
      p.visible = p.alpha >= 0.04;
    }
  }

  function beginRaster() {
    if (rasterStarted) return;
    rasterStarted = true;
    const toSize = wordmark?.drawSize || sculptureDrawSize;
    for (const p of particles) {
      if (p.role === 'fade' || p.alpha < 0.04) {
        p.visible = false;
        continue;
      }
      const next = createDisperseParticle({
        x: p.x, y: p.y, glyph: p.glyph, light: p.light, alpha: p.alpha,
        randA: p.randA, randB: p.randB
      });
      p.x = next.x; p.y = next.y;
      p.vx = next.vx; p.vy = next.vy;
      p.startAlpha = next.startAlpha;
      p.alpha = next.alpha;
      p.visible = next.visible;
      p.size = toSize;
    }
  }

  function fillBackground() {
    const fade = timeline.phase === 'idle' ? 0 : revealProgress(timeline.elapsed);
    const alpha = 1 - fade;
    if (alpha < 0.01) return;
    ctx.globalAlpha = alpha;
    ctx.fillStyle = background;
    ctx.fillRect(0, 0, width, height);
    ctx.globalAlpha = 1;
  }

  function drawSculpture() {
    const hole = clickMetrics();
    ctx.fillStyle = foreground;
    ctx.textAlign = 'center';
    ctx.font = `${sculptureDrawSize}px ${mono}`;
    for (let i = 0; i < lattice.length; i++) {
      const p = lattice[i];
      const alpha = drawnAlpha(p, hole);
      if (alpha < 0.04) continue;
      ctx.globalAlpha = alpha;
      ctx.fillText(p.glyph, p.x, p.y);
    }
    if (hole.formed) {
      ctx.globalAlpha = 0.92;
      ctx.font = `600 ${Math.max(11, fontSize)}px ${mono}`;
      ctx.fillText('CLICK', width / 2, height / 2);
      ctx.font = `${fontSize}px ${mono}`;
    }
  }

  function drawParticles() {
    ctx.fillStyle = foreground;
    ctx.textAlign = 'center';
    const fade = timeline.phase === 'fade' ? 1 - revealProgress(timeline.elapsed) : 1;
    for (const p of particles) {
      if (!p.visible || p.alpha < 0.04) continue;
      const alpha = p.alpha * fade * (0.55 + p.light * 0.45);
      if (alpha < 0.04) continue;
      ctx.globalAlpha = alpha;
      ctx.font = `${p.size || sculptureDrawSize}px ${mono}`;
      ctx.fillText(p.glyph, p.x * width, p.y * height);
    }
  }

  function drawRaster() {
    if (!raster?.cells?.length) return;
    const elapsed = timeline.elapsed;
    const front = rasterScanFront(elapsed, height, MORPH_END, RASTER_SCAN_SECONDS);
    const fade = timeline.phase === 'fade' ? 1 - revealProgress(elapsed) : 1;
    ctx.fillStyle = foreground;
    ctx.textAlign = 'center';
    ctx.font = `${raster.drawSize}px ${mono}`;
    for (const cell of raster.cells) {
      const alpha = rasterCellAlpha(cell, front, fade, raster.stepY * 4);
      if (alpha < 0.04) continue;
      ctx.globalAlpha = alpha * (0.55 + cell.light * 0.45);
      ctx.fillText(cell.glyph, cell.x, cell.y);
    }
  }

  function stepIdle(dt) {
    clock += dt;
    stepLattice(dt);
  }

  function stepPlay(dt) {
    const before = timeline.elapsed;
    timeline.tick(dt);
    const after = timeline.elapsed;
    if (before < MORPH_END && after >= MORPH_END) stepMorph(1);
    if (after >= MORPH_END) beginRaster();
    if (timeline.phase === 'morph') stepMorph(after);
    else {
      const fallTime = Math.max(0, after - MORPH_END);
      for (const p of particles) stepDisperseParticle(p, dt, fallTime);
    }
  }

  function applyQaHold() {
    const qa = qaConfig();
    if (!qa || timeline.phase === 'idle') return false;
    if (qa.paused) return true;
    if (qa.holdElapsed == null) return false;
    timeline.setElapsed(Number(qa.holdElapsed) || 0);
    if (timeline.elapsed >= MORPH_END) {
      stepMorph(1);
      beginRaster();
    } else stepMorph(timeline.elapsed);
    return true;
  }

  function draw() {
    if (dirty) measure();
    ctx.globalAlpha = 1;
    ctx.clearRect(0, 0, width, height);
    fillBackground();
    if (timeline.phase === 'idle') drawSculpture();
    else {
      if (timeline.phase !== 'morph') drawRaster();
      drawParticles();
    }
    publishQa();
    notify();
    if (timeline.phase === 'done') complete();
  }

  function advanceClock(elapsed) {
    const held = applyQaHold();
    if (!held) {
      const qa = qaConfig();
      const delta = qa?.paused ? 0 : elapsed;
      const steps = physicsSteps(delta);
      if (!steps.length && timeline.phase === 'idle') stepIdle(0);
      for (const dt of steps) {
        if (timeline.phase === 'idle') stepIdle(dt);
        else stepPlay(dt);
      }
    }
    draw();
  }

  function frame(now) {
    if (disposed) return;
    raf = 0;
    try {
      const started = performance.now();
      if (lastNow == null) {
        lastNow = now;
        if (dirty) measure();
        if (timeline.phase === 'idle') stepIdle(0);
        draw();
      } else {
        const elapsed = Math.max(0, (now - lastNow) / 1000);
        lastNow = now;
        averageFrame += (elapsed * 1000 - averageFrame) * 0.05;
        advanceClock(elapsed);
      }
      if (disposed) return;
      measuredFrames++;
      if (import.meta.env?.DEV) {
        canvas.dataset.introFrame = String(measuredFrames);
        if (measuredFrames % 30 === 0) canvas.dataset.introFps = (1000 / averageFrame).toFixed(1);
      }
      const overloaded = performance.now() - started > 16 || (measuredFrames > 20 && averageFrame > 24);
      slowFrames = overloaded ? slowFrames + 1 : Math.max(0, slowFrames - 1);
      if (slowFrames > 8 && quality < 2 && timeline.phase === 'idle') {
        quality++;
        slowFrames = 0;
        dirty = true;
      }
      raf = requestAnimationFrame(frame);
    } catch (error) {
      dispose();
      onError?.(error);
    }
  }

  function invalidate() { dirty = true; }
  let resizeObserver = null;
  let reducedMotion = null;
  const handleReducedMotion = () => { if (reducedMotion?.matches) skip(); };
  function visibility() {
    if (document.hidden) {
      cancelAnimationFrame(raf);
      raf = 0;
      lastNow = null;
    } else if (!disposed && !raf) {
      lastNow = null;
      raf = requestAnimationFrame(frame);
    }
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    cancelAnimationFrame(raf);
    raf = 0;
    lastNow = null;
    try { resizeObserver?.disconnect(); } catch { /* Observer may not have been attached. */ }
    window.removeEventListener('resize', invalidate);
    window.visualViewport?.removeEventListener('resize', invalidate);
    window.visualViewport?.removeEventListener('scroll', invalidate);
    document.removeEventListener('visibilitychange', visibility);
    reducedMotion?.removeEventListener('change', handleReducedMotion);
    lattice = [];
    particles = [];
    wordmark = null;
    raster = { cells: [], drawSize: 11, stepY: 12 };
  }

  try {
    resizeObserver = new ResizeObserver(invalidate);
    reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    resizeObserver.observe(canvas);
    window.addEventListener('resize', invalidate);
    window.visualViewport?.addEventListener('resize', invalidate);
    window.visualViewport?.addEventListener('scroll', invalidate);
    document.addEventListener('visibilitychange', visibility);
    reducedMotion.addEventListener('change', handleReducedMotion);
  } catch (error) {
    dispose();
    throw error;
  }

  function skip() {
    if (disposed || finished) return;
    timeline.skip();
    complete();
  }

  return {
    start() {
      try {
        measure();
        draw();
        if (!document.hidden) raf = requestAnimationFrame(frame);
      } catch (error) {
        dispose();
        onError?.(error);
      }
    },
    advance() {
      if (disposed || finished) return;
      try {
        if (timeline.phase !== 'idle') return;
        if (dirty) measure();
        stepLattice(0);
        beginMorph();
        if (timeline.advance()) notify();
        draw();
      } catch (error) {
        dispose();
        onError?.(error);
      }
    },
    pointer(x, y) {
      if (!Number.isFinite(x) || !Number.isFinite(y)) { mouseActive = false; return; }
      mouseActive = true;
      mousePx = x;
      mousePy = y;
    },
    skip,
    dispose
  };
}
