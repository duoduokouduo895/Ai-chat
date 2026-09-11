<template>
  <div class="ascii-intro" :class="{ 'ascii-intro--idle': phase === 'idle' }" :data-phase="phase">
    <canvas ref="canvas" class="ascii-intro__canvas" aria-hidden="true"></canvas>
    <button
      ref="trigger"
      class="ascii-intro__trigger"
      type="button"
      :aria-label="phase === 'idle' ? (instruction || '开场') : '跳过开场'"
      @click="onActivate"
      @keydown="onKeydown"
      @keyup="onKeyup"
      @pointermove="movePointer"
      @pointerleave="resetPointer"
    ></button>
    <div v-if="phase === 'idle'" class="ascii-intro__masthead">
      <span class="ascii-intro__brand" aria-hidden="true">dduo<span> / </span>AI–CHAT</span>
    </div>
    <button class="ascii-intro__skip" type="button" @click="finish">跳过开场 <span aria-hidden="true">↗</span></button>
    <div
      v-if="phase === 'idle'"
      class="ascii-intro__caption"
      aria-live="polite"
      role="status"
    >
      <span>{{ instruction }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { createAsciiIntroEngine } from './asciiIntroEngine.mjs';

const props = defineProps({
  getLayout: { type: Function, default: null }
});
const emit = defineEmits(['reveal-start', 'complete', 'phase']);
const canvas = ref(null);
const trigger = ref(null);
const phase = ref('idle');
let engine = null;
let completed = false;
let unmounted = false;
let revealStarted = false;
let holdActivate = false;
const instruction = computed(() => (phase.value === 'idle' ? '点击' : ''));

function advance() {
  if (phase.value === 'idle') engine?.advance();
}

function finish() {
  if (completed || unmounted) return;
  completed = true;
  engine?.dispose();
  emit('complete');
}

function onActivate(event) {
  if (event?.repeat) return;
  if (phase.value === 'idle') {
    advance();
    return;
  }
  if (holdActivate) return;
  finish();
}

function onKeydown(event) {
  if (event.repeat) {
    if (event.key === ' ' || event.key === 'Enter') event.preventDefault();
    return;
  }
  if (event.key === 'Escape') {
    event.preventDefault();
    finish();
    return;
  }
  if (event.key === ' ' || event.key === 'Enter') holdActivate = true;
}

function onKeyup(event) {
  if (event.key === ' ' || event.key === 'Enter') holdActivate = false;
}

function handleRevealStart() {
  if (completed || unmounted || revealStarted) return;
  revealStarted = true;
  emit('reveal-start');
}

function movePointer(event) { engine?.pointer(event.clientX, event.clientY); }
function resetPointer() { engine?.pointer(Number.NaN, Number.NaN); }

onMounted(async () => {
  await nextTick();
  if (unmounted || completed) return;
  try {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      finish();
      return;
    }
    engine = createAsciiIntroEngine({
      canvas: canvas.value,
      getLayout: props.getLayout,
      onPhase: (value) => { phase.value = value; emit('phase', value); },
      onRevealStart: handleRevealStart,
      onComplete: finish,
      onError: () => { console.warn('Intro unavailable; opening workspace.'); finish(); }
    });
    engine.start();
    const mobile = window.matchMedia('(max-width: 768px)').matches;
    if (!completed && !mobile) trigger.value?.focus({ preventScroll: true });
  } catch { finish(); }
});
onBeforeUnmount(() => { unmounted = true; engine?.dispose(); });
</script>

<style scoped>
.ascii-intro {
  position: fixed;
  inset: 0;
  z-index: 100;
  color: var(--text-primary);
  font-family: var(--font-mono);
  pointer-events: none;
  isolation: isolate;
}
.ascii-intro__canvas { display: block; width: 100%; height: 100%; }
.ascii-intro__trigger {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: 0;
  background: transparent;
  cursor: pointer;
  pointer-events: auto;
  touch-action: manipulation;
}
.ascii-intro__trigger:focus-visible { outline: 1px solid var(--text-primary); outline-offset: -8px; }
.ascii-intro__masthead { position: absolute; top: max(24px, env(safe-area-inset-top)); left: 28px; }
.ascii-intro__brand { font-size: 13px; font-weight: 700; letter-spacing: 0.02em; }
.ascii-intro__brand span { color: var(--text-secondary); margin: 0 8px; }
.ascii-intro__skip {
  position: absolute;
  top: max(12px, env(safe-area-inset-top));
  right: 20px;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--text-secondary);
  background: var(--canvas);
  font: 12px var(--font-mono);
  cursor: pointer;
  pointer-events: auto;
  z-index: 1;
}
.ascii-intro__skip span { margin-left: 16px; }
.ascii-intro__skip:hover { color: var(--text-primary); }
.ascii-intro__skip:focus-visible { outline: 2px solid var(--text-primary); outline-offset: 2px; }
.ascii-intro__caption {
  position: absolute;
  bottom: max(28px, env(safe-area-inset-bottom));
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  color: var(--text-secondary);
  background: transparent;
  font-size: 11px;
  letter-spacing: 0.08em;
  opacity: 0.55;
}
@media (max-width: 768px) {
  .ascii-intro__masthead { left: 16px; }
  .ascii-intro__brand { font-size: 11px; }
  .ascii-intro__skip { right: 8px; }
}
</style>
