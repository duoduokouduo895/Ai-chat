// API base URL configuration shared by Axios and streaming fetch clients.
const normalizeBaseUrl = (url) => {
  return url.endsWith('/') ? url.slice(0, -1) : url;
};

const DEFAULT_API_BASE_URL = '/api';

const getApiBaseUrl = () => {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL;
  if (configuredBaseUrl && configuredBaseUrl.trim()) {
    return normalizeBaseUrl(configuredBaseUrl.trim());
  }
  return DEFAULT_API_BASE_URL;
};

export const API_BASE_URL = getApiBaseUrl();

export default {
  baseUrl: API_BASE_URL
};
