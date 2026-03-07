const envApiBaseRaw = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL

const envApiBase = (() => {
  if (!envApiBaseRaw) return ""
  const normalized = String(envApiBaseRaw).trim().replace(/\/+$/, "")
  if (/^https?:\/\/api\.socialsea\.co\.in$/i.test(normalized)) {
    return "https://socialsea.co.in"
  }
  return normalized
})()

const defaultApiBase = (() => {
  if (typeof window === "undefined") return "http://localhost:8080"
  const host = window.location.hostname
  if (host === "localhost" || host === "127.0.0.1") return "http://localhost:8080"
  return ""
})()

export const API_BASE = (envApiBase || defaultApiBase).replace(/\/+$/, "")

