<template>
  <article class="knowledge-row" :class="{ selected }">
    <button class="knowledge-main" type="button" @click="$emit('select', knowledgeBase)">
      <strong>{{ knowledgeBase.name }}</strong>
      <span>{{ knowledgeBase.description || '暂无描述' }}</span>
      <small>{{ knowledgeBase.documentCount || 0 }} 个文档 · {{ knowledgeBase.isPublic ? '公开' : '私有' }}</small>
    </button>
    <div class="knowledge-row-actions">
      <button type="button" :aria-label="`上传文档到 ${knowledgeBase.name}`" @click="$emit('upload-document', knowledgeBase.id)">上传</button>
      <button type="button" :aria-label="`编辑知识库 ${knowledgeBase.name}`" @click="$emit('edit', knowledgeBase)">编辑</button>
      <button type="button" class="danger" :aria-label="`删除知识库 ${knowledgeBase.name}`" @click="$emit('delete', knowledgeBase)">删除</button>
    </div>
  </article>
</template>

<script setup>
defineProps({
  knowledgeBase: { type: Object, required: true },
  selected: { type: Boolean, default: false }
});

defineEmits(['select', 'edit', 'delete', 'upload-document']);
</script>
