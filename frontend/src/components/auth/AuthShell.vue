<template>
  <main class="auth-shell">
    <section class="auth-panel" :aria-labelledby="titleId">
      <header class="auth-brand">
        <img src="/icon/OIP-C.webp" alt="" aria-hidden="true">
        <div>
          <p>Dduo Workspace</p>
          <h1 :id="titleId">{{ title }}</h1>
        </div>
      </header>

      <p class="auth-description">{{ description }}</p>
      <slot />
    </section>

    <aside class="auth-context" aria-label="产品说明">
      <div class="context-content">
        <span class="context-kicker">AI CHAT WORKSPACE</span>
        <h2>让对话、搜索与知识库保持在同一个安静的工作区。</h2>
        <p>选择模型，沉浸提问，需要时再打开历史与知识库。</p>
        <div class="context-points">
          <span>多模型切换</span>
          <span>知识库问答</span>
          <span>历史检索</span>
        </div>
      </div>
    </aside>
  </main>
</template>

<script setup>
defineProps({
  titleId: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true }
});
</script>

<style scoped>
.auth-shell {
  background: var(--canvas);
  color: var(--text-primary);
  display: grid;
  font-family: var(--font-family);
  grid-template-columns: minmax(360px, 480px) 1fr;
  min-height: 100dvh;
}

.auth-panel {
  align-self: center;
  padding: 46px clamp(24px, 5vw, 72px);
}

.auth-brand {
  align-items: center;
  display: flex;
  gap: 14px;
}

.auth-brand img {
  border-radius: 14px;
  height: 48px;
  object-fit: cover;
  width: 48px;
}

.auth-brand p,
.auth-brand h1 {
  margin: 0;
}

.auth-brand p,
.context-kicker {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.auth-brand h1 {
  font-size: 25px;
  letter-spacing: -0.025em;
  margin-top: 3px;
}

.auth-description {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  margin: 20px 0 26px;
}

.auth-context {
  align-items: center;
  background: var(--surface-1);
  border-left: 1px solid var(--border-subtle);
  display: flex;
  padding: 7vw;
}

.context-content {
  max-width: 620px;
}

.context-content h2 {
  font-size: clamp(28px, 4vw, 50px);
  letter-spacing: -0.05em;
  line-height: 1.12;
  margin: 14px 0;
}

.context-content p {
  color: var(--text-secondary);
  line-height: 1.75;
  max-width: 54ch;
}

.context-points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 26px;
}

.context-points span {
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  padding: 7px 10px;
}

@media (max-width: 860px) {
  .auth-shell {
    display: flex;
  }

  .auth-panel {
    margin: auto;
    max-width: 480px;
    width: 100%;
  }

  .auth-context {
    display: none;
  }
}
</style>
