<template>
  <div v-if="visible" class="modal-overlay" @click.self="$emit('close')">
    <div class="modal" :class="{ active: visible }">
      <header class="modal-header">
        <h2 class="modal-title">
          {{ isEdit ? '编辑知识库' : '创建知识库' }}
        </h2>
        <button class="modal-close" @click="$emit('close')">✕</button>
      </header>
      
      <form @submit.prevent="handleSubmit" class="modal-body">
        <!-- 知识库名称 -->
        <div class="form-group">
          <label for="kb-name" class="form-label">
            知识库名称 <span class="required">*</span>
          </label>
          <input
            id="kb-name"
            v-model="formData.name"
            type="text"
            class="form-input"
            placeholder="请输入知识库名称"
            required
            maxlength="100"
          >
          <small class="form-help">1-100个字符，建议使用有意义的名称</small>
        </div>

        <!-- 知识库描述 -->
        <div class="form-group">
          <label for="kb-description" class="form-label">描述</label>
          <textarea
            id="kb-description"
            v-model="formData.description"
            class="form-textarea"
            placeholder="请输入知识库描述（可选）"
            rows="3"
            maxlength="500"
          ></textarea>
          <small class="form-help">最多500个字符，描述知识库的用途和内容</small>
        </div>

        <!-- 可见性设置 -->
        <div class="form-group">
          <label class="form-label">可见性设置</label>
          <div class="radio-group">
            <label class="radio-item">
              <input
                v-model="formData.isPublic"
                type="radio"
                :value="false"
                class="radio-input"
              >
              <span class="radio-label">
                <span class="radio-icon">🔒</span>
                <span class="radio-text">
                  <strong>私有</strong>
                  <small>仅创建者可见和使用</small>
                </span>
              </span>
            </label>
            <label class="radio-item">
              <input
                v-model="formData.isPublic"
                type="radio"
                :value="true"
                class="radio-input"
              >
              <span class="radio-label">
                <span class="radio-icon">🌐</span>
                <span class="radio-text">
                  <strong>公开</strong>
                  <small>所有用户都可以查看和使用</small>
                </span>
              </span>
            </label>
          </div>
        </div>

        <!-- 标签 -->
        <div class="form-group">
          <label class="form-label">标签</label>
          <div class="tags-input-container">
            <div class="tags-list">
              <span
                v-for="(tag, index) in formData.tags"
                :key="index"
                class="tag-item"
              >
                {{ tag }}
                <button
                  type="button"
                  class="tag-remove"
                  @click="removeTag(index)"
                >
                  ✕
                </button>
              </span>
            </div>
            <input
              v-model="newTag"
              type="text"
              class="tag-input"
              placeholder="输入标签后按回车添加"
              @keydown.enter.prevent="addTag"
              @keydown.space.prevent="addTag"
              maxlength="20"
            >
          </div>
          <small class="form-help">标签用于分类和搜索，每个标签最多20个字符</small>
        </div>

        <!-- 高级设置 -->
        <div class="form-group">
          <details class="advanced-settings">
            <summary class="advanced-toggle">
              <span>高级设置</span>
              <span class="toggle-icon">▼</span>
            </summary>
            <div class="advanced-content">
              <!-- 状态设置 -->
              <div class="setting-item">
                <label class="form-label">状态</label>
                <select v-model="formData.status" class="form-select">
                  <option value="ACTIVE">活跃</option>
                  <option value="INACTIVE">未激活</option>
                </select>
              </div>

              <!-- 配置选项 -->
              <div class="setting-item">
                <label class="checkbox-item">
                  <input
                    v-model="formData.autoUpdate"
                    type="checkbox"
                    class="checkbox-input"
                  >
                  <span class="checkbox-label">启用自动更新</span>
                </label>
                <small class="form-help">文档更新时自动重新生成向量</small>
              </div>

              <div class="setting-item">
                <label class="checkbox-item">
                  <input
                    v-model="formData.enableRAG"
                    type="checkbox"
                    class="checkbox-input"
                  >
                  <span class="checkbox-label">启用RAG对话</span>
                </label>
                <small class="form-help">允许基于此知识库进行智能对话</small>
              </div>
            </div>
          </details>
        </div>

        <!-- 操作按钮 -->
        <div class="modal-actions">
          <button
            type="button"
            class="btn-base btn-outline"
            @click="$emit('close')"
          >
            取消
          </button>
          <button
            type="submit"
            class="btn-base btn-primary"
            :disabled="!isFormValid || isSubmitting"
          >
            <span v-if="isSubmitting">{{ isEdit ? '保存中...' : '创建中...' }}</span>
            <span v-else>{{ isEdit ? '保存' : '创建' }}</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'

// Props
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  knowledgeBase: {
    type: Object,
    default: null
  },
  isEdit: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits(['close', 'save'])

// 响应式数据
const isSubmitting = ref(false)
const newTag = ref('')

// 表单数据
const formData = reactive({
  name: '',
  description: '',
  isPublic: false,
  tags: [],
  status: 'ACTIVE',
  autoUpdate: true,
  enableRAG: true
})

// 计算属性
const isFormValid = computed(() => {
  return formData.name.trim().length > 0 && formData.name.trim().length <= 100
})

// 监听props变化，初始化表单数据
watch(() => props.knowledgeBase, (newKB) => {
  if (newKB && props.isEdit) {
    Object.assign(formData, {
      name: newKB.name || '',
      description: newKB.description || '',
      isPublic: newKB.isPublic || false,
      tags: Array.isArray(newKB.tags) ? [...newKB.tags] : [],
      status: newKB.status || 'ACTIVE',
      autoUpdate: newKB.autoUpdate !== false,
      enableRAG: newKB.enableRAG !== false
    })
  } else if (!props.isEdit) {
    // 重置表单数据
    Object.assign(formData, {
      name: '',
      description: '',
      isPublic: false,
      tags: [],
      status: 'ACTIVE',
      autoUpdate: true,
      enableRAG: true
    })
  }
}, { immediate: true })

// 添加标签
const addTag = () => {
  const tag = newTag.value.trim()
  if (tag && !formData.tags.includes(tag) && formData.tags.length < 10) {
    formData.tags.push(tag)
    newTag.value = ''
  }
}

// 移除标签
const removeTag = (index) => {
  formData.tags.splice(index, 1)
}

// 提交表单
const handleSubmit = async () => {
  if (!isFormValid.value || isSubmitting.value) {
    return
  }

  try {
    isSubmitting.value = true
    
    // 准备提交数据
    const submitData = {
      name: formData.name.trim(),
      description: formData.description.trim() || null,
      isPublic: formData.isPublic,
      tags: formData.tags,
      status: formData.status,
      autoUpdate: formData.autoUpdate,
      enableRAG: formData.enableRAG
    }

    // 触发保存事件
    emit('save', submitData)
    
  } catch (error) {
    console.error('提交表单失败:', error)
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--spacing-md);
}

.modal {
  background: var(--bg-primary);
  border-radius: var(--border-radius-lg);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transform: scale(0.9);
  opacity: 0;
  transition: all var(--transition-normal);
}

.modal.active {
  transform: scale(1);
  opacity: 1;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-xl);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
}

.modal-title {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  font-size: var(--font-size-lg);
  color: var(--text-secondary);
  cursor: pointer;
  padding: var(--spacing-xs);
  border-radius: var(--border-radius-sm);
  transition: all var(--transition-fast);
}

.modal-close:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.modal-body {
  flex: 1;
  padding: var(--spacing-xl);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.form-label {
  font-weight: 500;
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.required {
  color: var(--danger-text);
}

.form-input,
.form-textarea,
.form-select {
  padding: var(--spacing-md);
  border: 1px solid var(--border-light);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-md);
  color: var(--text-primary);
  background: var(--bg-primary);
  transition: border-color var(--transition-fast);
}

.form-input:focus,
.form-textarea:focus,
.form-select:focus {
  outline: none;
  border-color: var(--border-focus);
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.form-help {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.4;
}

.radio-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.radio-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  cursor: pointer;
  padding: var(--spacing-md);
  border: 1px solid var(--border-light);
  border-radius: var(--border-radius-md);
  transition: all var(--transition-fast);
}

.radio-item:hover {
  border-color: var(--border-focus);
  background: var(--bg-secondary);
}

.radio-input {
  margin: 0;
}

.radio-label {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  flex: 1;
}

.radio-icon {
  font-size: var(--font-size-lg);
  margin-top: 2px;
}

.radio-text {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.radio-text strong {
  color: var(--text-primary);
}

.radio-text small {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.tags-input-container {
  border: 1px solid var(--border-light);
  border-radius: var(--border-radius-md);
  padding: var(--spacing-sm);
  background: var(--bg-primary);
  transition: border-color var(--transition-fast);
}

.tags-input-container:focus-within {
  border-color: var(--border-focus);
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  margin-bottom: var(--spacing-sm);
}

.tag-item {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.tag-remove {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: var(--font-size-xs);
  padding: 0;
  margin-left: var(--spacing-xs);
}

.tag-remove:hover {
  color: var(--danger-text);
}

.tag-input {
  border: none;
  outline: none;
  background: none;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  width: 100%;
  padding: var(--spacing-xs) 0;
}

.advanced-settings {
  border: 1px solid var(--border-light);
  border-radius: var(--border-radius-md);
  overflow: hidden;
}

.advanced-toggle {
  background: var(--bg-secondary);
  padding: var(--spacing-md);
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
  color: var(--text-primary);
  transition: background-color var(--transition-fast);
}

.advanced-toggle:hover {
  background: var(--bg-tertiary);
}

.toggle-icon {
  transition: transform var(--transition-fast);
}

.advanced-settings[open] .toggle-icon {
  transform: rotate(180deg);
}

.advanced-content {
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.setting-item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
}

.checkbox-input {
  margin: 0;
}

.checkbox-label {
  color: var(--text-primary);
  font-weight: 500;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
  padding-top: var(--spacing-lg);
  border-top: 1px solid var(--border-light);
  margin-top: auto;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .modal-overlay {
    padding: var(--spacing-sm);
  }
  
  .modal {
    max-width: none;
    max-height: 95vh;
  }
  
  .modal-header,
  .modal-body {
    padding: var(--spacing-md);
  }
  
  .radio-group {
    gap: var(--spacing-sm);
  }
  
  .radio-item {
    padding: var(--spacing-sm);
  }
  
  .modal-actions {
    flex-direction: column-reverse;
    gap: var(--spacing-sm);
  }
  
  .modal-actions .btn-base {
    width: 100%;
  }
}
</style>