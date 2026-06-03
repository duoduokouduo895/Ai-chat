import { getAdminConversations } from '@/api/conversation'
import { getDashboardStats } from '@/api/admin'
import { getModelList } from '@/api/model'

function toArray(data) {
  return Array.isArray(data) ? data : []
}

function mapConversationActivities(conversations) {
  return toArray(conversations).slice(0, 5).map((item, index) => ({
    id: item.id || `conversation-${index}`,
    title: item.title || `Conversation #${item.id ?? index + 1}`,
    subtitle: item.lastMessage || item.firstUserMessage || '暂无消息预览',
    status: 'ACTIVE',
    duration: '进行中',
    platform: 'Web / Browser',
    time: item.updatedAt || item.createdAt || '刚刚',
  }))
}

export async function getDashboardData() {
  const [statsResult, modelResult, conversationResult] = await Promise.allSettled([
    getDashboardStats(),
    getModelList(),
    getAdminConversations({ page: 0, size: 20 }),
  ])

  const stats = statsResult.status === 'fulfilled' ? statsResult.value || {} : {}
  const models = modelResult.status === 'fulfilled' ? toArray(modelResult.value) : []
  const conversations = conversationResult.status === 'fulfilled' ? toArray(conversationResult.value) : []

  return {
    stats: [
      {
        key: 'users',
        title: 'Total Users',
        titleZh: '用户数量',
        value: stats.userCount ?? 0,
        icon: 'group',
        iconClass: 'bg-cyan-100 text-cyan-600',
      },
      {
        key: 'models',
        title: 'Available Models',
        titleZh: '模型数量',
        value: stats.modelCount ?? models.length,
        icon: 'deployed_code',
        iconClass: 'bg-blue-100 text-blue-600',
      },
      {
        key: 'conversations',
        title: 'Total Sessions',
        titleZh: '会话数量',
        value: stats.conversationCount ?? conversations.length,
        icon: 'forum',
        iconClass: 'bg-green-100 text-green-600',
      },
      {
        key: 'knowledge-bases',
        title: 'Knowledge Bases',
        titleZh: '知识库数量',
        value: stats.knowledgeBaseCount ?? 0,
        icon: 'database',
        iconClass: 'bg-orange-100 text-orange-600',
      },
    ],
    systemStatus: {
      label: 'Stable',
      items: [
        { label: 'API Auth Mode', value: 'JWT Sliding' },
        { label: 'Model Providers', value: `${models.length} online` },
        { label: 'Knowledge Bases', value: `${stats.knowledgeBaseCount ?? 0} loaded` },
      ],
    },
    activities: mapConversationActivities(conversations),
  }
}
