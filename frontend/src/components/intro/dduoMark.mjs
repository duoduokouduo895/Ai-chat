import { clamp } from './introModel.mjs';

export const DDUO_VIEWBOX = { width: 1000, height: 320 };
const LETTER_W = 230;

function inStem(x, y, left, right, top, bottom) {
  return x >= left && x <= right && y >= top && y <= bottom;
}

function inEllipse(x, y, cx, cy, rx, ry) {
  const dx = (x - cx) / Math.max(1e-4, rx);
  const dy = (y - cy) / Math.max(1e-4, ry);
  return dx * dx + dy * dy <= 1;
}

export function inLetterD(x, y) {
  const stem = inStem(x, y, 145, 205, 24, 300);
  const bowl = inEllipse(x, y, 108, 178, 98, 100) && y >= 70;
  const hole = inEllipse(x, y, 108, 178, 44, 46);
  return stem || (bowl && !hole);
}

export function inLetterU(x, y) {
  const left = inStem(x, y, 30, 78, 72, 248);
  const right = inStem(x, y, 142, 190, 72, 248);
  const bottom = inEllipse(x, y, 110, 214, 80, 68) && y >= 170;
  const inner = inEllipse(x, y, 110, 214, 42, 36) && y >= 178;
  return left || right || (bottom && !inner);
}

export function inLetterO(x, y) {
  return inEllipse(x, y, 110, 178, 96, 100) && !inEllipse(x, y, 110, 178, 46, 48);
}

const LETTERS = [
  { x: 10, test: inLetterD },
  { x: 260, test: inLetterD },
  { x: 510, test: inLetterU },
  { x: 760, test: inLetterO }
];

export function wordmarkInside(u, v) {
  const x = u * DDUO_VIEWBOX.width;
  const y = v * DDUO_VIEWBOX.height;
  if (x < 0 || y < 0 || x > DDUO_VIEWBOX.width || y > DDUO_VIEWBOX.height) return false;
  for (const letter of LETTERS) {
    if (x >= letter.x && x <= letter.x + LETTER_W && letter.test(x - letter.x, y)) return true;
  }
  return false;
}

export function wordmarkCoverage(u, v) {
  return wordmarkInside(u, v) ? 1 : 0;
}

export function sampleCellCoverage(u, v, du, dv) {
  const hx = Math.max(1e-4, du * 0.33);
  const hy = Math.max(1e-4, dv * 0.33);
  let sum = 0;
  let n = 0;
  for (const oy of [-hy, 0, hy]) {
    for (const ox of [-hx, 0, hx]) {
      sum += wordmarkCoverage(u + ox, v + oy);
      n += 1;
    }
  }
  return n ? sum / n : 0;
}

export function edgeGlyphFor(gx, gy, mag, coverage) {
  if (!(mag > 0.55)) {
    if (coverage > 0.4) return '0';
    if (coverage > 0.25) return '-';
    return ',';
  }
  const ang = Math.atan2(gy, gx);
  const a = Math.abs(ang);
  if (a < Math.PI / 8 || a > 7 * Math.PI / 8) return '|';
  if (Math.abs(a - Math.PI / 2) < Math.PI / 8) return gy > 0 ? '^' : '_';
  if (ang > 0 && ang < Math.PI / 2) return gy > gx ? '\'' : '/';
  if (ang > Math.PI / 2) return '\\';
  if (ang < 0 && ang > -Math.PI / 2) return '/';
  return '\\';
}

export function styleFromCoverage(coverage, gx, gy) {
  const mag = Math.hypot(gx, gy);
  if (coverage >= 0.58 && mag < 1.8) {
    const glyph = coverage > 0.84 ? 'A' : coverage > 0.7 ? '8' : '0';
    return {
      glyph,
      light: 0.52 + coverage * 0.42,
      alpha: 0.58 + coverage * 0.4
    };
  }
  return {
    glyph: edgeGlyphFor(gx, gy, mag, coverage),
    light: 0.38 + coverage * 0.42,
    alpha: 0.28 + coverage * 0.62
  };
}

export function wordmarkLayout(width, height) {
  const mobile = width <= 768;
  const aspect = DDUO_VIEWBOX.height / DDUO_VIEWBOX.width;
  let w = width * (mobile ? 0.84 : 0.7);
  let h = w * aspect;
  const maxH = height * 0.35;
  if (h > maxH) {
    h = maxH;
    w = h / aspect;
  }
  return {
    x: (width - w) / 2,
    y: height * 0.36 - h / 2,
    w,
    h
  };
}

export function sampleWordmark(width, height) {
  const w = Math.max(1, width);
  const h = Math.max(1, height);
  const layout = wordmarkLayout(w, h);
  const cols = Math.max(120, Math.ceil(layout.w / 7));
  const stepX = layout.w / cols;
  const stepY = stepX * 1.15;
  const du = stepX / layout.w;
  const dv = stepY / layout.h;
  const eps = Math.max(du, dv) * 0.6;
  const cells = [];
  for (let row = 0, y = layout.y + stepY / 2; y < layout.y + layout.h; y += stepY, row++) {
    for (let col = 0, x = layout.x + stepX / 2; x < layout.x + layout.w; x += stepX, col++) {
      const u = (x - layout.x) / layout.w;
      const v = (y - layout.y) / layout.h;
      const coverage = sampleCellCoverage(u, v, du, dv);
      if (coverage < 0.12) continue;
      const gx = (wordmarkCoverage(u + eps, v) - wordmarkCoverage(u - eps, v)) / (2 * eps);
      const gy = (wordmarkCoverage(u, v + eps) - wordmarkCoverage(u, v - eps)) / (2 * eps);
      const style = styleFromCoverage(coverage, gx, gy);
      cells.push({
        col,
        row,
        u,
        v,
        x: x / w,
        y: y / h,
        coverage,
        ...style
      });
    }
  }
  return {
    layout,
    stepX,
    stepY,
    cols,
    drawSize: Math.min(stepX / 0.56, stepY * 1.02),
    cells
  };
}
