<template>
  <div v-if="visible" class="modal-overlay" @click.self="closeDialog">
    <div
      ref="dialogRef"
      class="modal"
      :class="{ active: visible }"
      role="dialog"
      aria-modal="true"
      aria-labelledby="knowledge-base-modal-title"
      tabindex="-1"
    >
      <header class="modal-header">
        <h2 id="knowledge-base-modal-title" class="modal-title">
          {{ isEdit ? '编辑知识库' : '创建知识库' }}
        </h2>
        <button
          ref="closeButtonRef"
          type="button"
          class="modal-close"
          aria-label="关闭知识库表单"
          @click="closeDialog"
        >
          ✕
        </button>
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
                  :aria-label="`删除标签 ${tag}`"
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
              aria-label="添加标签"
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
                <label for="kb-status" class="form-label">状态</label>
                <select id="kb-status" v-model="formData.status" class="form-select">
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
            @click="closeDialog"
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
import { ref, reactive, computed, toRef, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { useModalDialog } from '@/composables/useModalDialog.js'
import { getApiErrorMessage } from '@/services/apiErrorService.js'

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
const dialogRef = ref(null)
const closeButtonRef = ref(null)

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

const closeDialog = () => {
  emit('close')
}

useModalDialog({
  isOpen: toRef(props, 'visible'),
  dialogRef,
  initialFocusRef: closeButtonRef,
  onClose: closeDialog
})

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
    ElMessage.error(getApiErrorMessage(error, '表单提交失败，请稍后重试'))
  } finally {
    isSubmitting.value = false
  }
}
</script>
