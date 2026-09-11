import { clamp } from './introModel.mjs';

export const INPUT_RAMP = '0A86942?-';

export function pointInRoundRect(px, py, box) {
  const x = box.x, y = box.y, w = box.w, h = box.h;
  if (px < x || py < y || px > x + w || py > y + h) return false;
  const r = Math.max(0, Math.min(box.radius || 0, w / 2, h / 2));
  if (!r) return true;
  const dx = px < x + r ? x + r - px : px > x + w - r ? px - (x + w - r) : 0;
  const dy = py < y + r ? y + r - py : py > y + h - r ? py - (y + h - r) : 0;
  if (dx && dy && dx * dx + dy * dy > r * r) return false;
  return true;
}

export function rasterGridMetrics(width, height) {
  const w = Math.max(32, width);
  const h = Math.max(32, height);
  const stepX = Math.max(7, w / Math.max(64, Math.round(w / 8)));
  const stepY = Math.max(9, stepX * 1.2);
  return {
    stepX,
    stepY,
    cols: Math.max(8, Math.floor(w / stepX)),
    rows: Math.max(8, Math.floor(h / stepY)),
    drawSize: Math.min(stepX / 0.56, stepY * 0.92)
  };
}

function pushCell(cells, x, y, glyph, light, alpha, row) {
  cells.push({ x, y, glyph, light, alpha, row });
}

function outlineBox(cells, box, stepX, stepY, light = 0.78) {
  const x0 = box.x, y0 = box.y, x1 = box.x + box.w, y1 = box.y + box.h;
  for (let x = x0 + stepX / 2; x < x1; x += stepX) {
    const top = y0 + stepY * 0.45;
    const bottom = y1 - stepY * 0.45;
    if (pointInRoundRect(x, top, box)) pushCell(cells, x, top, '=', light, 0.9, Math.floor(top / stepY));
    if (pointInRoundRect(x, bottom, box)) pushCell(cells, x, bottom, '-', light * 0.85, 0.72, Math.floor(bottom / stepY));
  }
  for (let y = y0 + stepY / 2; y < y1; y += stepY) {
    const left = x0 + stepX * 0.45;
    const right = x1 - stepX * 0.45;
    if (pointInRoundRect(left, y, box)) pushCell(cells, left, y, '|', light, 0.86, Math.floor(y / stepY));
    if (pointInRoundRect(right, y, box)) pushCell(cells, right, y, '|', light, 0.86, Math.floor(y / stepY));
  }
}

function fillInput(cells, box, stepX, stepY) {
  for (let y = box.y + stepY / 2; y < box.y + box.h; y += stepY) {
    for (let x = box.x + stepX / 2; x < box.x + box.w; x += stepX) {
      if (!pointInRoundRect(x, y, box)) continue;
      const localY = clamp((y - box.y) / Math.max(1, box.h));
      const nearTop = y - box.y <= stepY * 0.85;
      const nearBottom = box.y + box.h - y <= stepY * 0.85;
      const nearLeft = x - box.x <= stepX * 0.85;
      const nearRight = box.x + box.w - x <= stepX * 0.85;
      let glyph = INPUT_RAMP[Math.min(INPUT_RAMP.length - 1, Math.floor(localY * INPUT_RAMP.length))];
      let light = 0.28 + (1 - localY) * 0.18;
      let alpha = 0.42;
      if (nearTop) { glyph = '='; light = 0.82; alpha = 0.9; }
      else if (nearLeft || nearRight) { glyph = '|'; light = 0.76; alpha = 0.84; }
      else if (nearBottom) { glyph = '-'; light = 0.62; alpha = 0.7; }
      pushCell(cells, x, y, glyph, light, alpha, Math.floor(y / stepY));
    }
  }
}

function fillTitle(cells, box, stepX, stepY, text) {
  const chars = Array.from(String(text || '').replace(/\s+/g, ' ').trim());
  if (!chars.length) {
    outlineBox(cells, box, stepX, stepY, 0.7);
    return;
  }
  const y = box.y + Math.max(stepY * 0.55, box.h * 0.55);
  let x = box.x + stepX * 0.7;
  const maxX = box.x + box.w - stepX * 0.4;
  for (const ch of chars) {
    if (x > maxX) break;
    if (ch !== ' ') pushCell(cells, x, y, ch, 0.94, 0.96, Math.floor(y / stepY));
    x += stepX * (ch === ' ' ? 0.7 : 1.05);
  }
}

function fillNav(cells, box, stepX, stepY, text) {
  outlineBox(cells, box, stepX, stepY, 0.7);
  const label = String(text || '').trim();
  if (label) {
    fillTitle(cells, {
      x: box.x + stepX * 0.4,
      y: box.y + box.h * 0.25,
      w: box.w - stepX * 0.8,
      h: box.h * 0.5,
      radius: 0
    }, stepX, stepY, label);
    return;
  }
  const cx = box.x + box.w / 2;
  const cy = box.y + box.h / 2;
  if (pointInRoundRect(cx, cy, box)) pushCell(cells, cx, cy, '#', 0.62, 0.7, Math.floor(cy / stepY));
}

function fillDivider(cells, box, stepX, stepY) {
  const y = box.y + box.h / 2;
  for (let x = box.x + stepX / 2; x < box.x + box.w; x += stepX) {
    pushCell(cells, x, y, '-', 0.5, 0.55, Math.floor(y / stepY));
  }
}

export function buildWorkspaceRaster(width, height, items = []) {
  const grid = rasterGridMetrics(width, height);
  const cells = [];
  const seen = new Set();
  for (const item of items || []) {
    if (!item || !(item.w > 0) || !(item.h > 0)) continue;
    const box = {
      x: item.x,
      y: item.y,
      w: item.w,
      h: item.h,
      radius: item.radius || 0
    };
    if (box.x + box.w < 0 || box.y + box.h < 0 || box.x > width || box.y > height) continue;
    const kind = item.kind || 'frame';
    const before = cells.length;
    if (kind === 'input') fillInput(cells, box, grid.stepX, grid.stepY);
    else if (kind === 'title') fillTitle(cells, box, grid.stepX, grid.stepY, item.text);
    else if (kind === 'nav') fillNav(cells, box, grid.stepX, grid.stepY, item.text);
    else if (kind === 'divider') fillDivider(cells, box, grid.stepX, grid.stepY);
    else outlineBox(cells, box, grid.stepX, grid.stepY, 0.68);
    for (let i = before; i < cells.length; i++) {
      const key = `${Math.round(cells[i].x / grid.stepX)}:${Math.round(cells[i].y / grid.stepY)}`;
      if (seen.has(key)) {
        cells.splice(i, 1);
        i -= 1;
        continue;
      }
      seen.add(key);
    }
  }
  return { ...grid, cells };
}

export function rasterScanFront(elapsed, height, scanStart, scanSpan) {
  const t = clamp((elapsed - scanStart) / Math.max(1e-4, scanSpan));
  return t * height;
}

export function rasterCellAlpha(cell, frontY, fade = 1, band = 48) {
  if (cell.y > frontY + 2) return 0;
  const appear = clamp((frontY - cell.y) / Math.max(8, band) + 0.15);
  return cell.alpha * appear * fade;
}

export function readIntroLayout(root, canvasRect) {
  if (!root || !canvasRect) return [];
  let nodes;
  try { nodes = root.querySelectorAll('[data-intro]'); } catch { return []; }
  const items = [];
  for (const node of nodes) {
    let box, css;
    try {
      box = node.getBoundingClientRect();
      css = getComputedStyle(node);
    } catch { continue; }
    if (!box.width || !box.height) continue;
    if (box.bottom <= canvasRect.top || box.top >= canvasRect.bottom) continue;
    if (css.visibility === 'hidden' || css.display === 'none' || css.opacity === '0') continue;
    items.push({
      kind: node.getAttribute('data-intro') || 'frame',
      x: box.left - canvasRect.left,
      y: box.top - canvasRect.top,
      w: box.width,
      h: box.height,
      radius: Math.min(parseFloat(css.borderTopLeftRadius) || 0, box.width / 2, box.height / 2),
      text: node.getAttribute('data-intro-text') || ''
    });
  }
  return items;
}
