<template>
  <div class="knowledge-selector-compact">
    <div ref="fieldRef" class="kb-field" :class="{ open: isOpen }">
      <span v-if="firstSelected" class="kb-tag">
        <span class="kb-tag-text">{{ firstSelected.name }}</span>
        <button
          class="kb-tag-remove"
          type="button"
          :aria-label="`移除知识库 ${firstSelected.name}`"
          @click="removeKnowledgeBase(firstSelected.id)"
        >
          <app-icon icon="xmark" aria-hidden="true" />
        </button>
      </span>
      <span v-if="extraCount > 0" class="kb-tag kb-tag-more" :title="extraNames">+{{ extraCount }}</span>
      <button
        ref="triggerRef"
        class="kb-trigger"
        type="button"
        aria-haspopup="listbox"
        :aria-expanded="isOpen"
        :aria-controls="isOpen ? listId : undefined"
        :aria-label="triggerLabel"
        @click="toggle"
        @keydown="handleTriggerKeydown"
      >
        <span v-if="selectedIds.length === 0" class="kb-placeholder">选择知识库</span>
        <svg class="kb-caret" viewBox="0 0 12 12" aria-hidden="true">
          <path d="M3 4.5 6 7.5 9 4.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>

    <Teleport to="body">
      <Transition name="kb-pop">
        <div
          v-if="isOpen"
          ref="panelRef"
          class="kb-panel"
          :class="`placement-${placement}`"
          :style="panelStyle"
          @focusout="handleFocusOut"
        >
          <ul
            v-if="knowledgeBases.length > 0"
            :id="listId"
            ref="listRef"
            class="kb-list"
            role="listbox"
            aria-multiselectable="true"
            aria-label="知识库"
            tabindex="-1"
            :aria-activedescendant="activeIndex >= 0 ? optionId(activeIndex) : undefined"
            @keydown="handleListKeydown"
          >
            <li
              v-for="(kb, index) in knowledgeBases"
              :id="optionId(index)"
              :key="kb.id"
              class="kb-option"
              :class="{ active: index === activeIndex, selected: isSelected(kb.id) }"
              role="option"
              :aria-selected="isSelected(kb.id)"
              @click="toggleOption(kb.id)"
              @mousemove="activeIndex = index"
            >
              <span class="kb-check" aria-hidden="true">
                <svg v-if="isSelected(kb.id)" viewBox="0 0 12 12">
                  <path d="M2.5 6.2 5 8.5 9.5 3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-info">{{ kb.documentCount || 0 }} 个文档</span>
            </li>
          </ul>
          <div
            v-else
            :id="listId"
            ref="listRef"
            class="knowledge-empty"
            tabindex="-1"
            @keydown="handleListKeydown"
          >
            {{ loading ? '加载中…' : (isGuest ? '登录后可用' : '暂无数据') }}
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';
import { knowledgeApiService } from '@/services/knowledgeApi';
import { getApiErrorMessage } from '@/services/apiErrorService';
import { notify } from '@/services/feedback.mjs';
import { isGuest as readIsGuest } from '@/services/sessionStore.mjs';

const props = defineProps({
  modelValue: { type: Array, default: () => [] }
});

const emit = defineEmits(['update:modelValue', 'mode-change']);

const PANEL_MAX_HEIGHT = 260;
const PANEL_MIN_WIDTH = 240;
const PANEL_GAP = 6;
const VIEWPORT_MARGIN = 8;

const listId = `kb-list-${useId()}`;
const optionId = (index) => `${listId}-opt-${index}`;

const fieldRef = ref(null);
const triggerRef = ref(null);
const panelRef = ref(null);
const listRef = ref(null);

const selectedIds = ref([]);
const knowledgeBases = ref([]);
const loading = ref(false);
const isGuest = readIsGuest();
const isOpen = ref(false);
const activeIndex = ref(-1);
const placement = ref('bottom');
const panelStyle = ref({});

watch(() => props.modelValue, (value) => {
  selectedIds.value = [...(value || [])];
}, { immediate: true });

const findKnowledgeBase = (id) => knowledgeBases.value.find((kb) => kb.id === id);
const nameOf = (id) => findKnowledgeBase(id)?.name || `知识库 ${id}`;

const firstSelected = computed(() => {
  const id = selectedIds.value[0];
  return id === undefined ? null : { id, name: nameOf(id) };
});
const extraCount = computed(() => Math.max(0, selectedIds.value.length - 1));
const extraNames = computed(() => selectedIds.value.slice(1).map(nameOf).join('、'));
const triggerLabel = computed(() => (
  selectedIds.value.length === 0
    ? '选择知识库'
    : `选择知识库，已选 ${selectedIds.value.length} 个`
));

const isSelected = (id) => selectedIds.value.includes(id);

const emitChange = () => {
  emit('update:modelValue', [...selectedIds.value]);
  emit('mode-change', selectedIds.value.length > 0 ? 'rag' : 'normal');
};

const toggleOption = (id) => {
  selectedIds.value = isSelected(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id];
  emitChange();
};

const removeKnowledgeBase = (id) => {
  selectedIds.value = selectedIds.value.filter((item) => item !== id);
  emitChange();
};

// 面板挂在 body 上并使用 fixed 定位，避免被输入区的滚动容器裁切；下方空间不足时向上展开
const updatePosition = () => {
  const field = fieldRef.value;
  if (!field) return;
  const rect = field.getBoundingClientRect();
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  const width = Math.min(Math.max(rect.width, PANEL_MIN_WIDTH), viewportWidth - VIEWPORT_MARGIN * 2);
  const left = Math.min(Math.max(rect.left, VIEWPORT_MARGIN), viewportWidth - width - VIEWPORT_MARGIN);
  const spaceBelow = viewportHeight - rect.bottom - PANEL_GAP - VIEWPORT_MARGIN;
  const spaceAbove = rect.top - PANEL_GAP - VIEWPORT_MARGIN;
  const openUp = spaceBelow < Math.min(PANEL_MAX_HEIGHT, 180) && spaceAbove > spaceBelow;

  placement.value = openUp ? 'top' : 'bottom';
  panelStyle.value = {
    left: `${left}px`,
    width: `${width}px`,
    maxHeight: `${Math.min(PANEL_MAX_HEIGHT, openUp ? spaceAbove : spaceBelow)}px`,
    ...(openUp
      ? { bottom: `${viewportHeight - rect.top + PANEL_GAP}px` }
      : { top: `${rect.bottom + PANEL_GAP}px` })
  };
};

const scrollActiveIntoView = () => {
  nextTick(() => {
    if (activeIndex.value < 0) return;
    document.getElementById(optionId(activeIndex.value))?.scrollIntoView({ block: 'nearest' });
  });
};

const handleOutsidePointer = (event) => {
  const target = event.target;
  if (fieldRef.value?.contains(target) || panelRef.value?.contains(target)) return;
  close({ restoreFocus: false });
};

// 焦点被移到面板和输入框以外（例如弹出对话框）时收起面板
const handleFocusOut = (event) => {
  const next = event.relatedTarget;
  if (!next || fieldRef.value?.contains(next) || panelRef.value?.contains(next)) return;
  close({ restoreFocus: false });
};

const addListeners = () => {
  document.addEventListener('pointerdown', handleOutsidePointer, true);
  window.addEventListener('resize', updatePosition);
  window.addEventListener('scroll', updatePosition, true);
};

const removeListeners = () => {
  document.removeEventListener('pointerdown', handleOutsidePointer, true);
  window.removeEventListener('resize', updatePosition);
  window.removeEventListener('scroll', updatePosition, true);
};

const open = async () => {
  if (isOpen.value) return;
  updatePosition();
  const firstSelectedIndex = knowledgeBases.value.findIndex((kb) => isSelected(kb.id));
  activeIndex.value = knowledgeBases.value.length > 0 ? Math.max(0, firstSelectedIndex) : -1;
  isOpen.value = true;
  addListeners();
  await nextTick();
  listRef.value?.focus({ preventScroll: true });
  scrollActiveIntoView();
};

function close({ restoreFocus = true } = {}) {
  if (!isOpen.value) return;
  isOpen.value = false;
  removeListeners();
  if (restoreFocus) triggerRef.value?.focus({ preventScroll: true });
}

const toggle = () => {
  if (isOpen.value) {
    close();
  } else {
    open();
  }
};

const handleTriggerKeydown = (event) => {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    open();
  }
};

const moveActive = (delta) => {
  const count = knowledgeBases.value.length;
  if (count === 0) return;
  activeIndex.value = (activeIndex.value + delta + count) % count;
  scrollActiveIntoView();
};

const handleListKeydown = (event) => {
  const count = knowledgeBases.value.length;
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault();
      moveActive(1);
      break;
    case 'ArrowUp':
      event.preventDefault();
      moveActive(-1);
      break;
    case 'Home':
      event.preventDefault();
      if (count) { activeIndex.value = 0; scrollActiveIntoView(); }
      break;
    case 'End':
      event.preventDefault();
      if (count) { activeIndex.value = count - 1; scrollActiveIntoView(); }
      break;
    case 'Enter':
    case ' ':
      event.preventDefault();
      if (activeIndex.value >= 0) toggleOption(knowledgeBases.value[activeIndex.value].id);
      break;
    case 'Escape':
    case 'Tab':
      // 面板挂在 body 末尾，Tab 直接移出会跳到页面末尾，因此先回到触发按钮
      event.preventDefault();
      close();
      break;
    default:
      break;
  }
};

const loadKnowledgeBases = async () => {
  try {
    loading.value = true;
    const response = await knowledgeApiService.getKnowledgeBases();
    knowledgeBases.value = Array.isArray(response.data) ? response.data : [];
  } catch (error) {
    notify.error(getApiErrorMessage(error, '加载知识库列表失败'));
  } finally {
    loading.value = false;
  }
};

onMounted(loadKnowledgeBases);
onBeforeUnmount(removeListeners);
</script>

<style scoped>
.knowledge-selector-compact {
  align-items: center;
  display: flex;
}

.kb-field {
  align-items: center;
  background: var(--surface-2);
  border-radius: 4px;
  box-shadow: 0 0 0 1px var(--border-subtle) inset;
  display: flex;
  gap: 4px;
  min-height: 34px;
  padding: 3px 4px 3px 6px;
  transition: background var(--transition-fast), box-shadow var(--transition-fast);
  width: 174px;
}

.kb-field:hover,
.kb-field.open,
.kb-field:focus-within {
  background: var(--surface-hover);
  box-shadow: 0 0 0 1px var(--border-strong) inset;
}

.kb-tag {
  align-items: center;
  background: var(--surface-3);
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  color: var(--text-primary);
  display: inline-flex;
  flex: 0 1 auto;
  font-size: var(--font-size-xs);
  gap: 2px;
  height: 22px;
  min-width: 0;
  padding: 0 2px 0 6px;
}

.kb-tag-more {
  flex: 0 0 auto;
  padding: 0 6px;
}

.kb-tag-text {
  max-width: 78px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-tag-remove {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 3px;
  color: var(--text-muted);
  cursor: pointer;
  display: inline-flex;
  font-size: 10px;
  height: 18px;
  justify-content: center;
  width: 18px;
}

.kb-tag-remove:hover,
.kb-tag-remove:focus-visible {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.kb-trigger {
  align-items: center;
  align-self: stretch;
  background: transparent;
  border: 0;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  flex: 1 1 auto;
  font: inherit;
  font-size: var(--font-size-xs);
  gap: 6px;
  justify-content: space-between;
  min-width: 20px;
  padding: 0 2px;
}

.kb-trigger:focus-visible {
  border-radius: 3px;
  outline: 2px solid var(--text-primary);
  outline-offset: 1px;
}

.kb-placeholder {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-caret {
  color: var(--text-muted);
  flex: 0 0 12px;
  height: 12px;
  margin-left: auto;
  transition: transform var(--transition-fast);
  width: 12px;
}

.kb-field.open .kb-caret {
  transform: rotate(180deg);
}

.kb-panel {
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-lg);
  box-shadow: 0 12px 32px color-mix(in srgb, #000 35%, transparent);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: fixed;
  z-index: var(--z-dropdown);
}

.kb-list {
  list-style: none;
  margin: 0;
  outline: none;
  overflow-y: auto;
  padding: 6px;
}

.kb-option {
  align-items: center;
  border-radius: var(--border-radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  font-size: var(--font-size-sm);
  gap: 8px;
  min-height: 36px;
  padding: 0 10px 0 6px;
}

.kb-option.active {
  background: var(--surface-3);
  color: var(--text-primary);
}

.kb-list:focus-visible .kb-option.active {
  box-shadow: 0 0 0 1px var(--border-strong) inset;
}

.kb-option.selected {
  color: var(--text-primary);
  font-weight: 600;
}

.kb-check {
  color: var(--text-primary);
  flex: 0 0 14px;
  height: 14px;
  width: 14px;
}

.kb-check svg {
  display: block;
  height: 14px;
  width: 14px;
}

.kb-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-info {
  color: var(--text-muted);
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 400;
}

.knowledge-empty {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  outline: none;
  padding: 20px;
  text-align: center;
}

.kb-pop-enter-active {
  transition: opacity 140ms linear, transform 200ms cubic-bezier(0.22, 1, 0.36, 1);
}

.kb-pop-leave-active {
  transition: opacity 100ms linear;
}

.kb-pop-enter-from,
.kb-pop-leave-to {
  opacity: 0;
}

.kb-pop-enter-from.placement-bottom {
  transform: translateY(-4px);
}

.kb-pop-enter-from.placement-top {
  transform: translateY(4px);
}

@media (max-width: 768px) {
  .knowledge-selector-compact,
  .kb-field {
    width: 100%;
  }
}
</style>
