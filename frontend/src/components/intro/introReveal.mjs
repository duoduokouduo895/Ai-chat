import { computed, inject, provide } from 'vue';

export const INTRO_REVEAL_KEY = Symbol('dduo-intro-reveal');

const inactive = {
  decrypting: computed(() => false),
  paused: computed(() => false)
};

export function provideIntroReveal(state) {
  provide(INTRO_REVEAL_KEY, state);
}

export function useIntroReveal() {
  return inject(INTRO_REVEAL_KEY, inactive);
}
