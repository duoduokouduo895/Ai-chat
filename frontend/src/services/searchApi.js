import apiClient from './apiClient';

/**
 * 搜索API服务
 */
export const SearchApiService = {
  /**
   * 统一搜索接口
   * @param {Object} searchRequest - 包含所有搜索参数的对象
   * @param {string} searchRequest.query - 搜索关键词
   * @param {string} [searchRequest.startDate] - 开始时间
   * @param {string} [searchRequest.endDate] - 结束时间
   * @param {string[]} [searchRequest.models] - AI模型列表
   * @param {number} [searchRequest.minMessages] - 最小消息数
   * @param {number} [searchRequest.maxMessages] - 最大消息数
   * @param {number} page - 页码（从0开始）
   * @param {number} size - 每页大小
   * @returns {Promise} 搜索结果
   */
  async search(searchRequest, page = 0, size = 10) {
    // 清理空值参数，避免发送空的查询参数
    const cleanedParams = {};
    for (const key in searchRequest) {
      if (searchRequest[key] !== null && searchRequest[key] !== '' && searchRequest[key] !== undefined) {
        cleanedParams[key] = searchRequest[key];
      }
    }
    const response = await apiClient.get('/search', {
      params: { ...cleanedParams, page, size }
    });
    return response.data;
  },

  /**
   * 智能搜索（语义搜索）
   * @param {string} query - 搜索查询
   * @param {number} page - 页码
   * @param {number} size - 每页大小
   * @returns {Promise} 搜索结果
   */
  async intelligentSearch(query, page = 0, size = 10) {
    const response = await apiClient.get('/search/intelligent', { params: { query, page, size } });
    return response.data; // 统一返回 data
  },

  /**
   * 获取搜索建议/自动完成
   * @param {string} query - 部分搜索词
   * @param {number} size - 建议数量
   * @returns {Promise} 搜索建议列表
   */
  async getSearchSuggestions(query, size = 5) {
    const response = await apiClient.get('/search/suggestions', { params: { prefix: query, size } });
    return response.data; // 统一返回 data
  },

  /**
   * 记录搜索历史
   * @param {string} query - 搜索关键词
   * @param {string} type - 搜索类型
   * @returns {Promise} 记录结果
   */
  async recordSearchHistory(query, type = 'fulltext') {
    const response = await apiClient.post('/search/history', {
      query,
      type,
      timestamp: new Date().toISOString()
    });
    return response.data;
  },

  /**
   * 获取搜索历史
   * @param {number} size - 历史记录数量
   * @returns {Promise} 搜索历史列表
   */
  async getSearchHistory(size = 20) {
    const response = await apiClient.get('/search/history', { params: { size } });
    return response.data;
  },

  /**
   * 清空搜索历史
   * @returns {Promise} 清空结果
   */
  async clearSearchHistory() {
    const response = await apiClient.delete('/search/history');
    return response.data;
  },
  
  /**
   * 清理重复的搜索历史
   * @returns {Promise} 清理结果
   */
  async cleanupDuplicateSearchHistory() {
    const response = await apiClient.post('/search/history/cleanup');
    return response.data;
  },
};

export default SearchApiService;
