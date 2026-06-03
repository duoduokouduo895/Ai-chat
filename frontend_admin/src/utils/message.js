import { ElMessage } from 'element-plus'

export function showErrorMessage(message) {
  ElMessage.error(message || '请求失败')
}

export function showSuccessMessage(message) {
  ElMessage.success(message || '操作成功')
}
