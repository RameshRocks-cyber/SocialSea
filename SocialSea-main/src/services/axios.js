import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
});

const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token =
    localStorage.getItem("accessToken") ||
    sessionStorage.getItem("accessToken");

  const normalized =
    token && token !== "null" && token !== "undefined" ? token.trim() : null;

  if (normalized) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${normalized}`;
  }

  return config;
});

export default api;