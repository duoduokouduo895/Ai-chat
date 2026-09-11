import {
  clamp, smooth, createIntroTimeline, createFrameGate, MORPH_SECONDS,
  workspaceOpacity, sculpturePose, evaluateSurfaceCell, introGridMetrics,
  morphSample, burstOffset, stepSpring, applyCursorImpulse, stepFallingParticle,
  releasePerchedOccupancy, mapMorphTargets, suppressMidBand, logoGlyphFor,
  stepStructureParticle, thinParticlesSpatially, selectMaskSamples,
  layoutLogoLetters, logoGridMetrics, sampleLatticeCoverage, snapToLattice,
  stepFieldParticle, recycleFieldParticle, FIELD_START, REVEAL_SPAN,
  fieldFrontY, shouldEnableLock, shouldStartReveal, revealProgress, lockCellsInRoundRect,
  FIELD_RAMP, SPRING_RAMP, glyphAt, glyphIndexFor, stepSpringGlyph, springEdgeAlpha
} from './introModel.mjs';

const noise = (i) => { const n = Math.sin(i * 127.1 + 31.7) * 43758.5453; return n - Math.floor(n); };

function qaConfig() {
  try {
    if (import.meta.env?.DEV) return globalThis.__INTRO_QA__ || {};
  } catch { /* Node tests and production builds omit the QA hook. */ }
  return null;
}

export function createAsciiIntroEngine({ canvas, workspace, onPhase, onReveal, onComplete, onError }) {
  const ctx = canvas.getContext('2d', { alpha: true });
  if (!ctx) throw new Error('Canvas 2D is unavailable');
  const mask = document.createElement('canvas');
  const maskCtx = mask.getContext('2d', { willReadFrequently: true });
  if (!maskCtx) throw new Error('Glyph sampling is unavailable');
  const timeline = createIntroTimeline();
  const style = getComputedStyle(workspace);
  const foreground = style.getPropertyValue('--text-primary').trim() || '#eeedf2';
  const background = style.getPropertyValue('--canvas').trim() || '#17151d';
  const mono = style.getPropertyValue('--font-mono').trim() || 'Consolas, monospace';
  let width = 0, height = 0, cellX = 7, cellY = 12, fontSize = 11, sculptureDrawSize = 11;
  let wordStepX = 8, wordStepY = 10, wordDrawSize = 11;
  let fieldStepX = 12, fieldStepY = 14, fieldDrawSize = 13, fieldCols = 16, fieldRows = 12;
  let latticeCols = 0, latticeRows = 0;
  let particles = [], word = [], sculpture = [], outline = [], surfaces = [], lattice = [];
  let field = [], lockCells = new Set(), lockOccupied = new Set();
  let lockGateY = 0, lockTop = 0, lockBottom = 0, firstLockAt = null, unveilArmed = false;
  const FIELD_GLYPHS = FIELD_RAMP;
  let raf = 0, clock = 0, disposed = false, dirty = true;
  const frameGate = createFrameGate();
  let previousPhase = '', previousReveal = -1, quality = 0, slowFrames = 0;
  let mouseActive = false, mousePx = 0, mousePy = 0;
  let averageFrame = 16.7, measuredFrames = 0;
  const observed = new Set();
  const surfaceIds = new WeakMap();
  let surfaceId = 0;

  function resetMask() {
    mask.width = Math.ceil(width); mask.height = Math.ceil(height);
    maskCtx.fillStyle = '#fff'; maskCtx.strokeStyle = '#fff';
  }

  function sampleMask(stepX = cellX, stepY = cellY, threshold = 80) {
    const pixels = maskCtx.getImageData(0, 0, mask.width, mask.height).data;
    const points = [];
    for (let y = stepY / 2; y < height; y += stepY) {
      for (let x = stepX / 2; x < width; x += stepX) {
        const alpha = pixels[(Math.floor(y) * mask.width + Math.floor(x)) * 4 + 3];
        if (alpha > threshold) points.push({ x, y, light: alpha / 255 });
      }
    }
    return points;
  }

  function paintWordMask(text, mobile) {
    const glyphs = [...text];
    let size = Math.min(height * 0.32, width * (mobile ? 0.34 : 0.26));
    const inkOf = (m) => {
      const left = Number.isFinite(m.actualBoundingBoxLeft) ? m.actualBoundingBoxLeft : 0;
      const right = Number.isFinite(m.actualBoundingBoxRight) ? m.actualBoundingBoxRight : m.width;
      return { left, right, ink: Math.max(m.width * 0.55, left + right) };
    };
    const layoutAt = (s) => {
      maskCtx.font = `800 ${s}px Arial, sans-serif`;
      const gap = Math.max(6, s * 0.042);
      const inks = glyphs.map((g) => inkOf(maskCtx.measureText(g)));
      return { ...layoutLogoLetters(inks.map((item) => item.ink), gap), inks, gap };
    };
    let laid = layoutAt(size);
    const desired = width * (mobile ? 0.62 : 0.48);
    size *= desired / Math.max(1, laid.total);
    size = Math.min(size, height * 0.32);
    laid = layoutAt(size);
    const metrics = maskCtx.measureText(text);
    const wordHeight = (metrics.actualBoundingBoxAscent || size * 0.72)
      + (metrics.actualBoundingBoxDescent || size * 0.18);
    const input = surfaces.find((edge) => edge.right - edge.left > width * 0.25 && edge.y > height * 0.3);
    const baseline = Math.min(height * 0.46 + wordHeight / 2,
      input ? input.y - cellY * 2 : height * 0.62);
    maskCtx.fillStyle = '#fff';
    maskCtx.textAlign = 'left';
    maskCtx.textBaseline = 'alphabetic';
    const origin = width / 2 - laid.total / 2;
    for (let i = 0; i < glyphs.length; i++) {
      maskCtx.fillText(glyphs[i], origin + laid.starts[i] + laid.inks[i].left, baseline);
    }
    return {
      height: wordHeight,
      width: laid.total,
      left: origin,
      top: baseline - (metrics.actualBoundingBoxAscent || size * 0.72),
      gap: laid.gap,
      size
    };
  }

  function findField(node) {
    if (node.matches?.('textarea, input')) return node;
    return node.querySelector?.('textarea, input') || null;
  }

  function drawVisibleText(node, canvasRect) {
    const css = getComputedStyle(node);
    const text = (node.textContent || '').replace(/\s+/g, ' ').trim();
    if (!text) return;
    maskCtx.font = `${css.fontWeight} ${css.fontSize} ${css.fontFamily}`;
    maskCtx.fillStyle = '#fff';
    try {
      if (typeof document.createRange === 'function') {
        const range = document.createRange();
        range.selectNodeContents(node);
        const rects = [...range.getClientRects()].filter((box) => box.width && box.height);
        try { range.detach(); } catch { /* Range.detach is optional. */ }
        const align = css.textAlign;
        maskCtx.textAlign = align === 'center' ? 'center' : align === 'right' ? 'right' : 'left';
        maskCtx.textBaseline = 'middle';
        if (rects.length === 1) {
          const box = rects[0];
          const x = align === 'center' ? box.left + box.width / 2 : align === 'right' ? box.right : box.left;
          maskCtx.fillText(text, x - canvasRect.left, box.top + box.height / 2 - canvasRect.top, box.width);
          return;
        }
        if (rects.length > 1) {
          const words = text.split(' ');
          const lines = [];
          let line = '';
          const maxW = rects[0].width;
          for (const token of words) {
            const trial = line ? `${line} ${token}` : token;
            if (line && maskCtx.measureText(trial).width > maxW) { lines.push(line); line = token; }
            else line = trial;
          }
          if (line) lines.push(line);
          rects.forEach((box, index) => {
            const x = align === 'center' ? box.left + box.width / 2 : align === 'right' ? box.right : box.left;
            maskCtx.fillText(lines[index] || '', x - canvasRect.left, box.top + box.height / 2 - canvasRect.top, box.width);
          });
          return;
        }
      }
    } catch { /* Fall back to the element box. */ }
    const box = node.getBoundingClientRect();
    maskCtx.textAlign = css.textAlign === 'center' ? 'center' : css.textAlign === 'right' ? 'right' : 'left';
    maskCtx.textBaseline = 'middle';
    const x = css.textAlign === 'center' ? box.left + box.width / 2 : css.textAlign === 'right' ? box.right : box.left;
    maskCtx.fillText(text, x - canvasRect.left, box.top + box.height / 2 - canvasRect.top, box.width);
  }

  function drawSvgIcon(svg, canvasRect) {
    const box = svg.getBoundingClientRect();
    if (!box.width || !box.height) return;
    const ox = box.left - canvasRect.left;
    const oy = box.top - canvasRect.top;
    if (typeof Path2D !== 'function') {
      maskCtx.fillRect(ox, oy, box.width, box.height);
      return;
    }
    const raw = (svg.getAttribute('viewBox') || '').trim().split(/[\s,]+/).map(Number);
    const vx = raw.length === 4 ? raw[0] : 0;
    const vy = raw.length === 4 ? raw[1] : 0;
    const vw = raw.length === 4 && raw[2] ? raw[2] : box.width || 1;
    const vh = raw.length === 4 && raw[3] ? raw[3] : box.height || 1;
    maskCtx.save();
    maskCtx.translate(ox, oy);
    maskCtx.scale((box.width || 1) / vw, (box.height || 1) / vh);
    maskCtx.translate(-vx, -vy);
    maskCtx.fillStyle = '#fff';
    const paths = svg.querySelectorAll?.('path') || [];
    for (const el of paths) {
      const d = el.getAttribute('d');
      if (!d) continue;
      try { maskCtx.fill(new Path2D(d)); } catch { /* Ignore malformed icon paths. */ }
    }
    maskCtx.restore();
  }

  function paintIntroNode(node, canvasRect) {
    const box = node.getBoundingClientRect();
    if (!box.width || !box.height || box.bottom <= canvasRect.top || box.top >= canvasRect.bottom) return;
    const css = getComputedStyle(node);
    if (css.visibility === 'hidden' || css.display === 'none') return;
    const kind = node.dataset.intro;
    maskCtx.globalAlpha = kind === 'text' ? 1 : 0.9;
    maskCtx.fillStyle = '#fff';
    maskCtx.strokeStyle = '#fff';
    if (kind === 'text') { drawVisibleText(node, canvasRect); return; }
    const x = box.left - canvasRect.left, y = box.top - canvasRect.top;
    const radius = Math.min(parseFloat(css.borderTopLeftRadius) || 0, box.width / 2, box.height / 2);
    if (kind === 'input') {
      maskCtx.lineWidth = Math.max(1.6, cellX * 0.55);
      maskCtx.beginPath(); maskCtx.roundRect(x, y, box.width, box.height, radius); maskCtx.stroke();
      const field = findField(node);
      if (field) {
        const shown = (field.value || '').length ? field.value : (field.placeholder || field.getAttribute?.('placeholder') || '');
        if (shown) {
          const fieldCss = getComputedStyle(field);
          const fieldBox = field.getBoundingClientRect();
          maskCtx.font = `${fieldCss.fontSize || css.fontSize || '14px'} ${fieldCss.fontFamily || mono}`;
          maskCtx.textAlign = 'left'; maskCtx.textBaseline = 'top';
          const padX = parseFloat(fieldCss.paddingLeft) || 0;
          const padY = parseFloat(fieldCss.paddingTop) || 0;
          maskCtx.fillText(shown, fieldBox.left - canvasRect.left + padX, fieldBox.top - canvasRect.top + padY,
            Math.max(1, fieldBox.width - padX * 2));
        }
      }
      return;
    }
    const svgs = node.querySelectorAll?.('svg') || [];
    let drewSvg = false;
    for (const svg of svgs) {
      const owner = svg.closest?.('[data-intro]');
      if (owner && owner !== node) continue;
      drawSvgIcon(svg, canvasRect);
      drewSvg = true;
    }
    if (!drewSvg) {
      maskCtx.lineWidth = Math.max(1.4, cellX * 0.45);
      maskCtx.beginPath(); maskCtx.roundRect(x, y, box.width, box.height, radius); maskCtx.stroke();
    }
    const texts = node.querySelectorAll?.('h1, h2, h3, strong, span, p, label, button, a') || [];
    for (const el of texts) {
      if (el.hasAttribute?.('data-intro')) continue;
      if (el.closest?.('[data-intro]') !== node) continue;
      if (el.getAttribute?.('aria-hidden') === 'true') continue;
      const elCss = getComputedStyle(el);
      if (elCss.visibility === 'hidden' || elCss.display === 'none') continue;
      if (el.querySelector?.('svg') && !(el.textContent || '').trim()) continue;
      let leaf = false;
      for (const child of el.childNodes) {
        if (child.nodeType === 3 && (child.textContent || '').trim()) { leaf = true; break; }
      }
      if (!leaf) continue;
      drawVisibleText(el, canvasRect);
    }
  }

  function syncSurfaces(node, canvasRect, previousSurfaces) {
    const box = node.getBoundingClientRect();
    if (!box.width || !box.height || box.bottom <= canvasRect.top || box.top >= canvasRect.bottom) return;
    const css = getComputedStyle(node);
    if (css.visibility === 'hidden' || css.display === 'none') return;
    const x = box.left - canvasRect.left, y = box.top - canvasRect.top;
    const radius = Math.min(parseFloat(css.borderTopLeftRadius) || 0, box.width / 2, box.height / 2);
    const catchable = node.dataset.intro === 'input'
      || (node.hasAttribute('data-intro-surface') && node.dataset.intro === 'frame' && y > height * 0.45);
    if (!catchable) return;
    if (!surfaceIds.has(node)) surfaceIds.set(node, ++surfaceId);
    const id = surfaceIds.get(node);
    surfaces.push({
      id, left: x + radius + cellX, right: x + box.width - radius - cellX, y,
      occupancy: previousSurfaces.get(id)?.occupancy || new Map()
    });
  }

  function nearestOutline(x, y) {
    let best = null, bestDist = Infinity;
    for (const point of outline) {
      const dist = Math.hypot(point.x - x, point.y - y);
      if (dist < bestDist) { bestDist = dist; best = point; }
    }
    return best;
  }

  function refreshStructureTargets() {
    const bounds = { glyphWidth: cellX, glyphHeight: fontSize };
    if (!outline.length) {
      for (const p of particles) {
        if (p.role !== 'structure' || p.state === 'dead') continue;
        releasePerchedOccupancy(p, surfaces, bounds);
        p.role = 'rain';
        p.catchable = false;
        if (p.state === 'waiting' || p.state === 'converging' || p.state === 'perched') p.state = 'falling';
        p.convergeFromX = p.convergeFromY = p.convergeStart = null;
      }
      return;
    }
    for (const p of particles) {
      if (p.role !== 'structure' || p.state === 'dead') continue;
      const point = nearestOutline(p.structX ?? p.x, p.structY ?? p.y);
      if (!point || Math.hypot(point.x - (p.structX ?? p.x), point.y - (p.structY ?? p.y)) > Math.max(cellX * 4, 48)) {
        releasePerchedOccupancy(p, surfaces, bounds);
        p.role = 'rain';
        p.catchable = false;
        if (p.state === 'waiting' || p.state === 'converging' || p.state === 'perched') p.state = 'falling';
        continue;
      }
      p.structX = point.x;
      p.structY = point.y;
    }
  }

  function syncFieldGrid() {
    fieldStepX = Math.max(4, cellX * 0.5);
    fieldStepY = Math.max(5, cellY * 0.5);
    fieldDrawSize = Math.max(6, fontSize * 0.5);
    fieldCols = Math.max(8, Math.floor(width / fieldStepX));
    fieldRows = Math.max(8, Math.ceil(height / fieldStepY));
  }

  function sampleLockCells(canvasRect) {
    lockCells = new Set();
    lockGateY = height * 0.45;
    lockTop = height;
    lockBottom = 0;
    const nodes = workspace.querySelectorAll('[data-intro="input"]');
    for (const node of nodes) {
      const box = node.getBoundingClientRect();
      if (!box.width || !box.height) continue;
      const x = box.left - canvasRect.left;
      const y = box.top - canvasRect.top;
      const css = getComputedStyle(node);
      const radius = Math.min(parseFloat(css.borderTopLeftRadius) || 0, box.width / 2, box.height / 2);
      for (const key of lockCellsInRoundRect(x, y, box.width, box.height, radius, fieldStepX, fieldStepY)) {
        lockCells.add(key);
      }
      lockTop = Math.min(lockTop, y);
      lockBottom = Math.max(lockBottom, y + box.height);
      lockGateY = Math.max(lockGateY, y + box.height);
    }
    if (lockBottom < lockTop) {
      lockTop = height * 0.4;
      lockBottom = height * 0.62;
    }
  }

  function seedField() {
    field = [];
    lockOccupied = new Set();
    syncFieldGrid();
    const perCol = fieldRows + 8;
    const maxLive = width <= 768 ? 1600 : 3200;
    let stride = 1;
    while (Math.ceil(fieldCols / stride) * perCol > maxLive && stride < 4) stride += 1;
    const fall = Math.max(240, height * 0.5);
    const off = fieldStepY + fieldDrawSize;
    for (let col = 0; col < fieldCols; col += stride) {
      const x = (col + 0.5) * fieldStepX;
      for (let k = 0; k < perCol; k++) {
        const seed = noise(col * 19 + k + 11);
        field.push({
          col,
          colX: x,
          x,
          y: -off - fieldStepY * k,
          seed,
          glyph: FIELD_GLYPHS[Math.min(FIELD_GLYPHS.length - 1, Math.floor(seed * FIELD_GLYPHS.length))],
          delay: FIELD_START + (col % 10) * 0.006,
          vx: 0,
          vy: fall + seed * height * 0.06,
          alpha: 0.62 + seed * 0.2,
          targetAlpha: 0.62 + seed * 0.2,
          light: 0.45 + seed * 0.35,
          state: 'falling',
          role: 'field'
        });
      }
    }
  }

  function measure() {
    const rect = canvas.getBoundingClientRect();
    const oldWidth = width, oldHeight = height;
    width = Math.max(1, rect.width); height = Math.max(1, rect.height);
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
    syncFieldGrid();
    const mobile = width <= 768;
    const ratio = Math.min(window.devicePixelRatio || 1, mobile ? 1.25 : quality >= 2 ? 1.25 : 1.75);
    canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.textBaseline = 'middle'; ctx.textAlign = 'center';
    ctx.font = `${fontSize}px ${mono}`;
    resetMask();
    const previousSurfaces = new Map(surfaces.map((edge) => [edge.id, edge]));
    surfaces = [];
    const nodes = new Set(workspace.querySelectorAll('[data-intro]'));
    for (const node of observed) {
      if (!nodes.has(node)) { resizeObserver.unobserve(node); observed.delete(node); }
    }
    for (const node of nodes) {
      if (!observed.has(node)) { resizeObserver.observe(node); observed.add(node); }
      syncSurfaces(node, rect, previousSurfaces);
      paintIntroNode(node, rect);
    }
    outline = selectMaskSamples(sampleMask(cellX, cellY, 24), cellX, cellY, width <= 768 ? 900 : 1600);
    sampleLockCells(rect);
    resetMask();
    const painted = paintWordMask('dduo', mobile);
    const logo = logoGridMetrics(painted.height, painted.width, mobile ? 640 : 1100);
    wordStepX = logo.stepX;
    wordStepY = logo.stepY;
    wordDrawSize = logo.drawSize;
    const pixels = maskCtx.getImageData(0, 0, mask.width, mask.height).data;
    const mw = mask.width, mh = mask.height;
    word = sampleLatticeCoverage((x, y) => {
      const ix = Math.floor(x), iy = Math.floor(y);
      if (ix < 0 || iy < 0 || ix >= mw || iy >= mh) return null;
      return pixels[(iy * mw + ix) * 4 + 3] / 255;
    }, width, height, wordStepX, wordStepY, 0.34);
    const resized = oldWidth && oldHeight && (oldWidth !== width || oldHeight !== height);
    if (resized) {
      const sx = width / oldWidth, sy = height / oldHeight;
      for (const p of particles) {
        p.x *= sx; p.y *= sy; p.fromX *= sx; p.fromY *= sy;
        if (p.alongX != null) { p.alongX *= sx; p.alongY *= sy; }
        if (p.regionX != null) { p.regionX *= sx; p.regionY *= sy; }
        if (p.fieldX != null) { p.fieldX *= sx; p.fieldY *= sy; }
        if (p.targetX != null) { p.targetX *= sx; p.targetY *= sy; }
        if (p.structX != null) { p.structX *= sx; p.structY *= sy; }
        if (p.convergeFromX != null) { p.convergeFromX *= sx; p.convergeFromY *= sy; }
      }
      for (const p of lattice) {
        p.x *= sx; p.y *= sy; p.targetX *= sx; p.targetY *= sy; p.vx *= sx; p.vy *= sy;
        if (p.restX != null) { p.restX *= sx; p.restY *= sy; }
      }
    }
    if (!lattice.length || latticeCols !== grid.cols || latticeRows !== grid.rows) seedLattice(grid);
    else {
      for (const p of lattice) {
        p.restX = (p.col + 0.5) * cellX;
        p.restY = (p.row + 0.5) * cellY;
        p.targetX = p.restX;
        p.targetY = p.restY;
      }
    }
    if ((timeline.phase === 'morph' || timeline.phase === 'word')
      && (resized || particles.some((p) => p.targetX == null))) {
      assignWordTargets();
    }
    if (timeline.phase === 'falling' || timeline.phase === 'outline' || timeline.phase === 'reveal') {
      refreshStructureTargets();
    }
    dirty = false;
  }

  function assignWordTargets() {
    if (!word.length) throw new Error('Empty word mask');
    const mapped = mapMorphTargets(particles, word, (index) => noise(index + 3));
    particles.forEach((p, index) => {
      const sample = mapped[index];
      if (!sample) return;
      const snapped = snapToLattice(sample.target.x, sample.target.y, wordStepX, wordStepY);
      p.targetX = snapped.x; p.targetY = snapped.y;
      p.regionX = snapped.x; p.regionY = snapped.y;
    });
  }

  function glyphFor(light, prev = null) {
    return glyphAt(glyphIndexFor(light, prev));
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

  function snapshotSculpture() {
    sculpture = [];
    for (const p of lattice) {
      if (p.alpha < 0.08) continue;
      if (p.x < -cellX || p.x > width + cellX || p.y < -cellY || p.y > height + cellY) continue;
      sculpture.push({ x: p.x, y: p.y, glyph: p.glyph, light: p.light });
    }
  }

  function pointerX() {
    if (!mouseActive || !width) return 0;
    return clamp(mousePx / width * 2 - 1, -1, 1);
  }

  function stepLattice(dt) {
    if (!lattice.length) seedLattice();
    const pose = sculpturePose(clock, pointerX());
    const cx = width / 2;
    const cy = height / 2;
    const formed = pose.formed;
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
    return { pose, cx, cy, formed };
  }

  function drawSculpture(dt) {
    const { cx, cy, formed } = stepLattice(dt);
    ctx.fillStyle = foreground;
    ctx.textAlign = 'center';
    const clickFont = `600 ${Math.max(11, fontSize)}px ${mono}`;
    let clickHalfW = 0;
    let clickHalfH = 0;
    if (formed) {
      ctx.font = clickFont;
      const metrics = ctx.measureText('CLICK');
      clickHalfW = Math.max(1, metrics.width) * 0.5;
      const ascent = Number.isFinite(metrics.actualBoundingBoxAscent)
        ? metrics.actualBoundingBoxAscent : Math.max(11, fontSize) * 0.72;
      const descent = Number.isFinite(metrics.actualBoundingBoxDescent)
        ? metrics.actualBoundingBoxDescent : Math.max(11, fontSize) * 0.2;
      clickHalfH = Math.max(1, ascent + descent) * 0.5;
    }
    ctx.font = `${sculptureDrawSize}px ${mono}`;
    for (let i = 0; i < lattice.length; i++) {
      const p = lattice[i];
      if (p.alpha < 0.04) continue;
      let alpha = p.alpha;
      if (formed && Math.abs(p.x - cx) <= clickHalfW && Math.abs(p.y - cy) <= clickHalfH) {
        alpha *= 0.62;
      }
      if (alpha < 0.04) continue;
      ctx.globalAlpha = alpha;
      ctx.fillText(p.glyph, p.x, p.y);
    }
    if (formed) {
      ctx.globalAlpha = 0.92;
      ctx.font = clickFont;
      ctx.fillText('CLICK', cx, cy);
      ctx.font = `${fontSize}px ${mono}`;
    }
  }

  function beginMorph() {
    snapshotSculpture();
    if (!sculpture.length) {
      clock = Math.max(clock, 1.2);
      stepLattice(1 / 60);
      snapshotSculpture();
    }
    if (!sculpture.length) {
      const formed = sculpturePose(clock, 0);
      for (const p of lattice) {
        if (p.restX < 0 || p.restX > width || p.restY < 0 || p.restY > height) continue;
        const sample = evaluateSurfaceCell(p.restX, p.restY, width, height, formed);
        if (!sample.inside) continue;
        p.x = p.restX; p.y = p.restY; p.alpha = sample.edge; p.light = sample.light; p.glyph = glyphFor(sample.light);
        sculpture.push({ x: p.x, y: p.y, glyph: p.glyph, light: p.light });
      }
    }
    if (!sculpture.length) {
      sculpture.push({ x: width / 2, y: height / 2, glyph: glyphAt(8), light: 0.6 });
    }
    if (!word.length) throw new Error('Empty word mask');
    const count = word.length;
    const pose = sculpturePose(clock, pointerX());
    const alongScale = Math.min(width, height) * (0.07 + pose.radial * 0.08);
    const sources = Array.from({ length: count }, (_, i) => sculpture[Math.floor(i * sculpture.length / count)]);
    const mapped = mapMorphTargets(sources, word, (i) => noise(i + 3));
    particles = sources.map((source, i) => {
      const sample = mapped[i] || { target: word[Math.min(word.length - 1, i)], region: word[Math.min(word.length - 1, i)] };
      const seed = noise(i);
      const along = burstOffset(noise(i + 17), noise(i + 53), alongScale);
      const snapped = snapToLattice(sample.target.x, sample.target.y, wordStepX, wordStepY);
      const ink = sample.target.light ?? 0.7;
      return {
        x: source.x, y: source.y, glyph: logoGlyphFor(ink), light: ink,
        fromX: source.x, fromY: source.y, seed,
        alongX: source.x + along.x, alongY: source.y + along.y,
        regionX: snapped.x, regionY: snapped.y,
        targetX: snapped.x, targetY: snapped.y,
        vx: 0, vy: 0, angle: 0, spin: 0,
        alpha: 1, state: 'falling', catchable: seed < 0.055, edgeId: null, role: 'rain'
      };
    });
  }

  function beginFall() {
    const top = Math.min(...particles.map((p) => p.targetY ?? p.y));
    const bottom = Math.max(...particles.map((p) => p.targetY ?? p.y));
    const span = Math.max(1, bottom - top);
    const pitchX = wordStepX;
    const pitchY = wordStepY;
    for (const p of particles) {
      p.x = p.targetX ?? p.x;
      p.y = p.targetY ?? p.y;
      p.delay = ((p.y - top) / span) * 0.6 + p.seed * 0.04;
      p.state = 'falling';
      p.vy = 64 + p.seed * 24;
      p.vx = (p.seed - 0.5) * 8;
      p.angle = 0;
      p.spin = 0;
      p.role = 'rain';
      p.catchable = false;
      p.convergeFromX = p.convergeFromY = p.convergeStart = null;
    }
    particles = thinParticlesSpatially(particles, surfaces, {
      glyphWidth: pitchX,
      glyphHeight: pitchY,
      width, height
    });
    firstLockAt = null;
    unveilArmed = false;
    seedField();
  }

  function drawParticles() {
    ctx.fillStyle = foreground;
    ctx.textAlign = 'center';
    const pack = timeline.phase === 'morph' || timeline.phase === 'word';
    const drawSize = pack ? wordDrawSize : fontSize;
    ctx.font = `${drawSize}px ${mono}`;
    const gx = pack ? wordStepX : Math.max(cellX, fontSize * 0.7);
    const gy = pack ? wordStepY : Math.max(fontSize * 0.9, cellY * 0.85);
    const seen = new Set();
    for (const p of particles) {
      if (p.state === 'dead' || p.alpha < 0.04) continue;
      if (!pack && p.state !== 'perched') {
        const key = `${Math.round(p.x / gx)}:${Math.round(p.y / gy)}`;
        if (seen.has(key)) continue;
        seen.add(key);
      }
      ctx.globalAlpha = p.alpha * (0.55 + p.light * 0.45);
      ctx.fillText(p.glyph, p.x, p.y);
    }
  }

  function drawField() {
    if (!field.length) return;
    ctx.fillStyle = foreground;
    ctx.textAlign = 'center';
    ctx.font = `${fieldDrawSize}px ${mono}`;
    const gx = fieldStepX;
    const gy = fieldStepY;
    const seen = new Set();
    for (const p of field) {
      if (p.state === 'dead' || p.alpha < 0.04) continue;
      const cell = `${Math.floor(p.x / gx)}:${Math.floor(p.y / gy)}`;
      if (p.role !== 'lock' && lockOccupied.has(cell)) continue;
      const key = `${Math.round(p.x / gx)}:${Math.round(p.y / gy)}`;
      if (seen.has(key)) continue;
      seen.add(key);
      ctx.globalAlpha = p.alpha * (p.role === 'lock' ? 1 : 0.72) * (0.55 + p.light * 0.45);
      ctx.fillText(p.glyph, p.x, p.y);
    }
  }

  function sceneName(time) {
    const phase = timeline.phase;
    if (phase === 'sculpture' || phase === 'morph' || phase === 'word' || phase === 'done') return phase;
    if (phase === 'reveal' || phase === 'clearing') return phase;
    if (lockOccupied.size) return 'lock';
    return time < 0.2 ? 'falling' : 'field';
  }

  function notify() {
    const time = timeline.elapsed;
    const scene = sceneName(time);
    if (previousPhase !== scene) {
      previousPhase = scene; onPhase(scene);
    }
    const reveal = (timeline.phase === 'reveal' || timeline.phase === 'clearing' || timeline.phase === 'done')
      ? revealProgress(timeline.phase === 'done' ? REVEAL_SPAN : timeline.elapsed)
      : 0;
    if (previousReveal !== reveal) { previousReveal = reveal; onReveal(reveal); }
  }

  function publishQa() {
    const qa = qaConfig();
    if (!qa && !(import.meta.env && import.meta.env.DEV)) return;
    canvas.dataset.introClock = clock.toFixed(3);
    canvas.dataset.introElapsed = timeline.elapsed.toFixed(3);
    canvas.dataset.introPhase = timeline.phase;
  }

  function draw(delta) {
    if (dirty) measure();
    const qa = qaConfig();
    let step = delta;
    if (qa?.paused) step = 0;
    if (qa && qa.holdClock != null && timeline.phase === 'sculpture') {
      clock = Number(qa.holdClock) || 0;
    } else {
      clock += step;
    }
    timeline.tick(step);
    ctx.globalAlpha = 1; ctx.clearRect(0, 0, width, height);
    if (!['reveal', 'clearing', 'done'].includes(timeline.phase) && !['reveal', 'clearing', 'done'].includes(sceneName(timeline.elapsed))) {
      ctx.fillStyle = background; ctx.fillRect(0, 0, width, height);
    }
    if (timeline.phase === 'sculpture') drawSculpture(step);
    else if (timeline.phase === 'morph' || timeline.phase === 'word') {
      const progress = timeline.phase === 'word' ? 1 : clamp(timeline.elapsed / MORPH_SECONDS);
      for (const p of particles) {
        const sample = morphSample(progress,
          { x: p.fromX, y: p.fromY },
          { x: p.alongX, y: p.alongY },
          { x: p.regionX, y: p.regionY },
          { x: p.targetX, y: p.targetY });
        p.x = sample.x; p.y = sample.y;
      }
      drawParticles();
    } else if (timeline.phase !== 'done') {
      const time = timeline.elapsed;
      const steps = Math.min(2, Math.max(1, Math.ceil((step || 0) * 60)));
      const bounds = {
        width, height, glyphHeight: fontSize, glyphWidth: cellX,
        gravity: Math.max(760, height * 1.9)
      };
      const fieldBounds = {
        width, height, glyphHeight: fieldStepY, glyphWidth: fieldStepX,
        gravity: 0
      };
      const front = fieldFrontY(field);
      const lock = {
        enabled: shouldEnableLock(front, lockGateY, height),
        cells: lockCells,
        occupied: lockOccupied,
        lockTop,
        lockBottom,
        revealElapsed: (timeline.phase === 'reveal' || timeline.phase === 'clearing')
          ? timeline.elapsed : 0
      };
      for (const p of particles) {
        if (p.state === 'dead') continue;
        if (stepStructureParticle(p, time, bounds)) continue;
        const stepDt = step / steps;
        for (let n = 0; n < steps; n++) {
          stepFallingParticle(p, stepDt, time - step + (n + 1) * stepDt, surfaces, bounds);
        }
        suppressMidBand(p, time, bounds);
      }
      const fieldDt = step / steps;
      for (const p of field) {
        for (let n = 0; n < steps; n++) {
          stepFieldParticle(p, fieldDt, time - step + (n + 1) * fieldDt, fieldBounds, lock);
        }
        if (p.state === 'dead' && p.role === 'field'
          && timeline.phase !== 'reveal' && timeline.phase !== 'clearing') {
          let top = Infinity;
          for (const q of field) {
            if (q === p || q.col !== p.col || q.state === 'dead' || q.role === 'lock') continue;
            if (q.y < top) top = q.y;
          }
          if (!Number.isFinite(top)) top = -fieldStepY;
          recycleFieldParticle(p, fieldBounds, top);
        }
      }
      if (lockOccupied.size && firstLockAt == null) firstLockAt = time;
      if (!unveilArmed && shouldStartReveal({
        frontY: front, height, stepY: fieldStepY,
        lockCount: lockOccupied.size, lockCellCount: lockCells.size,
        firstLockAt, time
      })) {
        unveilArmed = true;
        timeline.unveil();
      }
      drawField();
      drawParticles();
    }
    publishQa();
    notify();
    if (timeline.phase === 'done') { dispose(); onComplete(); }
  }

  function frame(now) {
    if (disposed) return;
    raf = 0;
    const slot = frameGate.accept(now);
    if (!slot.draw) {
      raf = requestAnimationFrame(frame);
      return;
    }
    try {
      const started = performance.now();
      draw(slot.deltaSeconds);
      if (disposed) return;
      measuredFrames++;
      averageFrame += (slot.deltaSeconds * 1000 - averageFrame) * 0.05;
      if (import.meta.env?.DEV) {
        canvas.dataset.introFrame = String(measuredFrames);
        if (measuredFrames % 30 === 0) canvas.dataset.introFps = (1000 / averageFrame).toFixed(1);
      }
      const overloaded = performance.now() - started > 16 || (measuredFrames > 20 && averageFrame > 24);
      slowFrames = overloaded ? slowFrames + 1 : Math.max(0, slowFrames - 1);
      if (slowFrames > 8 && quality < 2) {
        quality++; slowFrames = 0; dirty = true;
        /* Field is already budgeted; do not respawn a second curtain. */
      }
      raf = requestAnimationFrame(frame);
    } catch (error) { dispose(); onError(error); }
  }

  function invalidate() { dirty = true; }
  let resizeObserver = null;
  let mutationObserver = null;
  let reducedMotion = null;
  const handleReducedMotion = () => { if (reducedMotion?.matches) skip(); };
  function visibility() {
    if (document.hidden) { cancelAnimationFrame(raf); raf = 0; }
    else if (!disposed && !raf) { frameGate.reset(); raf = requestAnimationFrame(frame); }
  }

  function dispose() {
    if (disposed) return;
    disposed = true; cancelAnimationFrame(raf); raf = 0;
    frameGate.reset();
    try { resizeObserver?.disconnect(); } catch { /* Observer may not have been attached. */ }
    try { mutationObserver?.disconnect(); } catch { /* Observer may not have been attached. */ }
    observed.clear();
    window.removeEventListener('resize', invalidate);
    window.visualViewport?.removeEventListener('resize', invalidate);
    window.visualViewport?.removeEventListener('scroll', invalidate);
    try { workspace.removeEventListener('scroll', invalidate, true); } catch { /* Workspace may be detached. */ }
    document.removeEventListener('visibilitychange', visibility);
    reducedMotion?.removeEventListener('change', handleReducedMotion);
    particles = []; outline = []; sculpture = []; word = []; lattice = [];
    field = []; lockCells = new Set(); lockOccupied = new Set();
    mask.width = mask.height = 1;
  }

  try {
    resizeObserver = new ResizeObserver(invalidate);
    mutationObserver = new MutationObserver(invalidate);
    resizeObserver.observe(workspace); resizeObserver.observe(canvas);
    mutationObserver.observe(workspace, { childList: true, subtree: true, characterData: true });
    reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    window.addEventListener('resize', invalidate);
    window.visualViewport?.addEventListener('resize', invalidate);
    window.visualViewport?.addEventListener('scroll', invalidate);
    workspace.addEventListener('scroll', invalidate, true);
    document.addEventListener('visibilitychange', visibility);
    reducedMotion.addEventListener('change', handleReducedMotion);
  } catch (error) {
    dispose();
    throw error;
  }
  function skip() {
    if (disposed) return;
    timeline.skip(); dispose(); onReveal(1); onComplete();
  }
  return {
    start() {
      try { measure(); draw(0); if (!document.hidden) raf = requestAnimationFrame(frame); }
      catch (error) { dispose(); onError(error); }
    },
    advance() {
      if (disposed) return;
      try {
        if (timeline.phase === 'sculpture') { if (dirty) measure(); beginMorph(); }
        else if (timeline.phase === 'word') beginFall();
        if (timeline.advance()) notify();
      } catch (error) { dispose(); onError(error); }
    },
    pointer(x, y) {
      if (!Number.isFinite(x) || !Number.isFinite(y)) { mouseActive = false; return; }
      mouseActive = true;
      mousePx = x;
      mousePy = y;
    },
    skip, dispose
  };
}
