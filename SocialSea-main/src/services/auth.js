import { API_BASE } from "./api"

export const getToken = () => localStorage.getItem("accessToken")
export const getRefreshToken = () => localStorage.getItem("refreshToken")

const decodeBase64Url = value => {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/")
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4)
  return atob(padded)
}

export const parseJwtPayload = token => {
  if (!token) return null
  try {
    const [, payload] = token.split(".")
    if (!payload) return null
    return JSON.parse(decodeBase64Url(payload))
  } catch {
    return null
  }
}

export const isAuthenticated = () => !!getToken()

export const getUserRole = () => {
  const savedRole = localStorage.getItem("role")
  if (savedRole) return savedRole.toUpperCase()

  const payload = parseJwtPayload(getToken())
  const roleClaim = payload?.role || payload?.roles?.[0] || payload?.authority
  return roleClaim ? String(roleClaim).toUpperCase() : ""
}

export const isAdmin = () => {
  const role = getUserRole()
  return role === "ADMIN" || role === "SUPER_ADMIN"
}

export const setTokens = ({ token, refreshToken, userId, role, user }) => {
  if (token) {
    localStorage.setItem("accessToken", token)
    localStorage.setItem("token", token)
  }
  if (refreshToken) localStorage.setItem("refreshToken", refreshToken)
  if (userId != null) localStorage.setItem("userId", String(userId))

  const resolvedRole = role || user?.role
  if (resolvedRole) localStorage.setItem("role", String(resolvedRole).toUpperCase())
}

export const clearTokens = () => {
  localStorage.removeItem("accessToken")
  localStorage.removeItem("token")
  localStorage.removeItem("refreshToken")
  localStorage.removeItem("userId")
  localStorage.removeItem("role")
}

const refreshAccessToken = async () => {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return null

  const res = await fetch(`${API_BASE}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken })
  })

  if (!res.ok) {
    clearTokens()
    return null
  }

  const data = await res.json()
  if (data?.accessToken) {
    setTokens({ token: data.accessToken })
    return data.accessToken
  }

  return null
}

export const authFetch = async (url, options = {}) => {
  const token = getToken()
  const headers = { ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(url, { ...options, headers })
  if (res.status !== 401) return res

  const newToken = await refreshAccessToken()
  if (!newToken) return res

  const retryHeaders = { ...(options.headers || {}), Authorization: `Bearer ${newToken}` }
  return fetch(url, { ...options, headers: retryHeaders })
}
