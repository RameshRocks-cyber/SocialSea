import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || "http://43.205.213.14:8080",
  withCredentials: true,
});

const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || "http://43.205.213.14:8080",
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