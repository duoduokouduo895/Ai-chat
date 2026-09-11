<template>
  <span class="decrypt-text" :class="{ 'is-playing': playing }">
    <span class="decrypt-text__base">{{ text }}</span>
    <span v-if="playing" class="decrypt-text__overlay" aria-hidden="true">{{ shown }}</span>
  </span>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue';
import { createDecryptPlayback } from './introModel.mjs';
import { useIntroReveal } from './introReveal.mjs';

const props = defineProps({
  text: { type: String, default: '' }
});

const intro = useIntroReveal();
const playing = ref(false);
const shown = ref(props.text);
let playback = null;

function stop(showFinal = true) {
  playback?.cancel();
  playback = null;
  playing.value = false;
  if (showFinal) shown.value = props.text;
}

function start() {
  stop(false);
  shown.value = props.text;
  if (!props.text) return;
  playing.value = true;
  playback = createDecryptPlayback({
    text: props.text,
    getPaused: () => Boolean(intro.paused?.value),
    onUpdate: (value) => { shown.value = value; },
    onComplete: () => {
      shown.value = props.text;
      playing.value = false;
      playback = null;
    }
  });
  playback.start();
}

watch(() => intro.decrypting.value, (active) => {
  if (active) start();
  else stop(true);
}, { immediate: true });

watch(() => props.text, (value) => {
  if (playing.value) stop(true);
  else shown.value = value;
});

onBeforeUnmount(() => stop(false));
</script>

<style scoped>
.decrypt-text {
  display: inline-block;
  max-width: 100%;
  position: relative;
}
.decrypt-text__base,
.decrypt-text__overlay {
  white-space: pre-wrap;
}
.decrypt-text.is-playing .decrypt-text__base {
  color: transparent;
}
.decrypt-text__overlay {
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  position: absolute;
  user-select: none;
}
</style>
