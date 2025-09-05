// API配置文件
// 统一管理API地址，方便部署时配置

// 获取API基础URL
const getApiBaseUrl = () => {
  // 在开发环境中，使用相对路径，由Vite代理处理
  // 在生产环境中，也使用相对路径，这样会自动使用当前域名
  return '/api';
};

// API端点配置
export const API_ENDPOINTS = {
  // 用户相关
  USER_REGISTER: '/users/register',
  USER_LOGIN: '/users/login',
  USER_GUEST: '/users/guest', // 新增：创建游客用户的端点
  
  // 模型相关
  MODELS: '/models',
  
  // 对话相关
  CONVERSATIONS: '/conversations',
  CONVERSATIONS_RECENT: '/conversations/recent',
  CONVERSATION_MESSAGES: (conversationId) => `/conversations/${conversationId}/messages`,
  
  // 消息相关
  MESSAGES_AI_STREAM: '/messages/ai-response-stream',
  
  // 搜索相关
  SEARCH_FULL_TEXT: '/search/full-text',
  SEARCH_ADVANCED: '/search/advanced',
  SEARCH_INTELLIGENT: '/search/intelligent',
  SEARCH_VECTOR: '/search/vector',
  SEARCH_TIME_RANGE: '/search/time-range',
  SEARCH_SUGGESTIONS: '/search/suggestions'
};

// 获取完整的API URL
export const getApiUrl = (endpoint) => {
  const baseUrl = getApiBaseUrl();
  return `${baseUrl}${endpoint}`;
};

// 导出基础URL（用于需要直接使用的场景）
export const API_BASE_URL = getApiBaseUrl();

// 默认导出配置对象
export default {
  baseUrl: API_BASE_URL,
  endpoints: API_ENDPOINTS,
  getUrl: getApiUrl
};