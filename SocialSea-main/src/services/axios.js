import axios from "axios";
import { API_BASE } from "./api";

const api = axios.create({
  baseURL: API_BASE || undefined,
  withCredentials: true,
});

const refreshClient = axios.create({
  baseURL: API_BASE || undefined,
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
