export const formatDateTime = (value) => {
  if (!value) return '未知';
  return new Date(value).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

export const formatFileSize = (bytes) => {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${Math.round((bytes / (1024 ** index)) * 100) / 100} ${units[index]}`;
};

export const getFileIcon = (fileType = '') => ({
  pdf: 'PDF',
  doc: 'DOC',
  docx: 'DOC',
  txt: 'TXT',
  md: 'MD',
  ppt: 'PPT',
  pptx: 'PPT',
  xls: 'XLS',
  xlsx: 'XLS'
}[fileType.toLowerCase()] || 'FILE');

export const getKnowledgeStatus = (status) => ({
  ACTIVE: { label: '活跃', className: 'status-success' },
  INACTIVE: { label: '未激活', className: 'status-warning' },
  DELETED: { label: '已删除', className: 'status-danger' }
}[status] || { label: status || '未知', className: 'status-info' });

export const getParseStatus = (status) => ({
  PENDING: { label: '待解析', className: 'status-warning' },
  PROCESSING: { label: '处理中', className: 'status-info' },
  COMPLETED: { label: '已完成', className: 'status-success' },
  PARSING: { label: '解析中', className: 'status-info' },
  PARSED: { label: '已解析', className: 'status-success' },
  FAILED: { label: '解析失败', className: 'status-danger' }
}[status] || { label: status || '未知', className: 'status-info' });
