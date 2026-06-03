<template>
  <section class="search-panel">
    <WorkspaceHeader
      kicker="Search"
      title="搜索对话"
      description="检索历史内容，并按模型、时间和消息数量缩小范围。"
    >
      <button type="button" class="close-search" aria-label="关闭搜索" @click="$emit('close')">关闭</button>
    </WorkspaceHeader>

    <SearchInput
      v-model="searchQuery"
      @search="executeSearch"
      @suggestion-selected="handleSuggestionSelected"
    />

    <SearchFilters
      ref="filtersRef"
      :filters="filters"
      @update:filters="updateFilters"
      @apply-filters="executeSearch"
      @clear-filters="clearFilters"
    />

    <div class="search-content custom-scroll">
      <SearchHistory
        v-if="!hasSearched"
        ref="historyRef"
        :show-stats="true"
        @history-selected="handleHistorySelected"
      />

      <SearchResults
        v-else
        :results="searchResults"
        :loading="loading"
        :error="searchError"
        :total-results="totalResults"
        :total-pages="totalPages"
        :current-page="currentPage"
        :search-time="searchTime"
        :search-query="searchQuery"
        :sort-by="sortBy"
        @open-conversation="openConversation"
        @page-change="changePage"
        @sort-change="sortResults"
        @retry-search="executeSearch"
      />
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { SearchApiService } from '../../services/searchApi.js';
import WorkspaceHeader from '../common/WorkspaceHeader.vue';
import SearchFilters from './SearchFilters.vue';
import SearchHistory from './SearchHistory.vue';
import SearchInput from './SearchInput.vue';
import SearchResults from './SearchResults.vue';

const emit = defineEmits(['close', 'openConversation']);
const filtersRef = ref(null);
const historyRef = ref(null);
const searchQuery = ref('');
const searchResults = ref([]);
const loading = ref(false);
const hasSearched = ref(false);
const searchError = ref('');
const totalResults = ref(0);
const totalPages = ref(0);
const currentPage = ref(1);
const searchTime = ref(0);
const sortBy = ref('relevance');
const filters = reactive({ startTime: '', endTime: '', model: '', minMessageCount: '', maxMessageCount: '' });

const handleSuggestionSelected = (query) => {
  searchQuery.value = query;
  executeSearch();
};

const handleHistorySelected = (query) => {
  searchQuery.value = query;
  executeSearch();
};

const executeSearch = async () => {
  if (!searchQuery.value.trim()) return;
  loading.value = true;
  hasSearched.value = true;
  searchError.value = '';
  const startedAt = Date.now();

  try {
    const response = await SearchApiService.search({
      query: searchQuery.value,
      startDate: filters.startTime,
      endDate: filters.endTime,
      models: filters.model ? [filters.model] : [],
      minMessages: filters.minMessageCount,
      maxMessages: filters.maxMessageCount
    }, currentPage.value - 1, 10);

    if (Array.isArray(response)) {
      searchResults.value = response;
      totalResults.value = response.length;
      totalPages.value = Math.ceil(response.length / 10) || 1;
    } else if (Array.isArray(response?.content)) {
      searchResults.value = response.content;
      totalResults.value = response.totalElements || response.content.length;
      totalPages.value = response.totalPages || 0;
    } else {
      searchResults.value = [];
      totalResults.value = 0;
      totalPages.value = 0;
    }

    searchTime.value = Date.now() - startedAt;
    await SearchApiService.recordSearchHistory(searchQuery.value, 'unified').catch(() => {});
    await historyRef.value?.refresh();
  } catch (error) {
    searchError.value = error.response?.data?.message || error.message || '搜索失败，请稍后重试';
    searchResults.value = [];
    totalResults.value = 0;
    totalPages.value = 0;
  } finally {
    loading.value = false;
  }
};

const clearFilters = () => {
  Object.assign(filters, { startTime: '', endTime: '', model: '', minMessageCount: '', maxMessageCount: '' });
};

const updateFilters = (value) => {
  Object.assign(filters, value);
};

const sortResults = (value) => {
  sortBy.value = value;
  executeSearch();
};

const changePage = (page) => {
  if (page < 1 || page > totalPages.value) return;
  currentPage.value = page;
  executeSearch();
};

const openConversation = (conversation) => emit('openConversation', conversation);
</script>

<style src="../../assets/styles/search.css"></style>
