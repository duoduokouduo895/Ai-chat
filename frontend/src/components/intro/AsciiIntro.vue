<template>
  <div class="ascii-intro" :class="{ 'ascii-intro--interactive': interactive }" :data-phase="phase">
    <canvas ref="canvas" class="ascii-intro__canvas" aria-hidden="true"></canvas>
    <button
      v-if="!interactive"
      ref="trigger"
      class="ascii-intro__trigger"
      type="button"
      :aria-label="instruction || '开场'"
      :aria-disabled="!canAdvance"
      @click="advance"
      @pointermove="movePointer"
      @pointerleave="resetPointer"
    ></button>
    <div v-if="!interactive" class="ascii-intro__masthead">
      <span class="ascii-intro__brand" aria-hidden="true">dduo<span> / </span>AI–CHAT</span>
    </div>
    <button class="ascii-intro__skip" type="button" @click="finish">跳过开场 <span aria-hidden="true">↗</span></button>
    <div
      v-if="!interactive && phase !== 'sculpture'"
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

const props = defineProps({ workspace: { type: Object, default: null } });
const emit = defineEmits(['reveal', 'interactive', 'complete', 'phase']);
const canvas = ref(null);
const trigger = ref(null);
const phase = ref('sculpture');
const interactive = ref(false);
let engine = null;
let completed = false;
let unmounted = false;
const canAdvance = computed(() => phase.value === 'sculpture' || phase.value === 'word');
const instruction = computed(() => ({
  sculpture: '点击', morph: '正在聚字', word: '再次点击',
  falling: '', field: '', lock: '', outline: '', reveal: ''
}[phase.value] || ''));
function advance() { if (canAdvance.value) engine?.advance(); }

function reveal(value) {
  if (completed || unmounted) return;
  emit('reveal', value);
  if (value >= 1 && !interactive.value) { interactive.value = true; emit('interactive'); }
}
function finish() {
  if (completed || unmounted) return;
  completed = true;
  engine?.dispose();
  emit('reveal', 1);
  if (!interactive.value) { interactive.value = true; emit('interactive'); }
  emit('complete');
}
function movePointer(event) { engine?.pointer(event.clientX, event.clientY); }
function resetPointer() { engine?.pointer(Number.NaN, Number.NaN); }

onMounted(async () => {
  await nextTick();
  if (unmounted || completed) return;
  try {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !props.workspace) {
      finish(); return;
    }
    engine = createAsciiIntroEngine({
      canvas: canvas.value, workspace: props.workspace,
      onPhase: (value) => { phase.value = value; emit('phase', value); }, onReveal: reveal,
      onComplete: finish,
      onError: () => { console.warn('Intro unavailable; opening workspace.'); finish(); }
    });
    engine.start();
    if (!completed) trigger.value?.focus({ preventScroll: true });
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
.ascii-intro__trigger[aria-disabled="true"] { cursor: default; }
.ascii-intro__trigger:focus-visible { outline: 1px solid var(--accent-color); outline-offset: -8px; }
.ascii-intro__masthead { position: absolute; top: max(24px, env(safe-area-inset-top)); left: 28px; }
.ascii-intro__brand { font-size: 13px; font-weight: 700; letter-spacing: 0.02em; }
.ascii-intro__brand span { color: var(--text-muted); margin: 0 8px; }
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
}
.ascii-intro__skip span { margin-left: 16px; }
.ascii-intro__skip:hover { color: var(--text-primary); }
.ascii-intro__skip:focus-visible { outline: 2px solid var(--accent-color); outline-offset: 2px; }
.ascii-intro__caption {
  position: absolute;
  bottom: max(28px, env(safe-area-inset-bottom));
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  color: var(--text-muted);
  background: transparent;
  font-size: 11px;
  letter-spacing: 0.08em;
  opacity: 0.55;
}
.ascii-intro--interactive .ascii-intro__skip { top: auto; bottom: max(12px, env(safe-area-inset-bottom)); }
@media (max-width: 768px) {
  .ascii-intro__masthead { left: 16px; }
  .ascii-intro__brand { font-size: 11px; }
  .ascii-intro__skip { right: 8px; }
  .ascii-intro__caption { bottom: max(28px, env(safe-area-inset-bottom)); gap: 14px; }
}
</style>
