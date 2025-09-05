<template>
  <div v-if="visible" class="modal-overlay">
    <div class="modal-container">
      <div class="modal-header">
        <h3>体验额度已用完</h3>
      </div>
      <div class="modal-body">
        <p>游客体验消息上限为5条，请注册后继续使用。</p>
      </div>
      <div class="modal-footer">
        <button @click="closeModal" class="btn-secondary">关闭</button>
        <button @click="handleRegisterClick" class="btn-primary">去注册</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GuestLimitModal',
  props: {
    visible: {
      type: Boolean,
      required: true
    }
  },
  emits: ['close', 'go-to-register'], // 声明事件
  methods: {
    closeModal() {
      this.$emit('close');
    },
    handleRegisterClick() {
      this.$emit('go-to-register');
      this.closeModal();
    }
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
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-container {
  background-color: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
  width: 90%;
  max-width: 400px;
  text-align: center;
}

.modal-header h3 {
  margin-top: 0;
  color: #333;
}

.modal-body p {
  margin-bottom: 20px;
  color: #555;
}

.modal-footer {
  display: flex;
  justify-content: space-around;
}

button {
  padding: 10px 20px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 16px;
}

.btn-primary {
  background-color: #007bff;
  color: white;
}

.btn-secondary {
  background-color: #ccc;
  color: #333;
}
</style>
