import { API_BASE } from "./api"

export const getToken = () => localStorage.getItem("accessToken")
export const getRefreshToken = () => localStorage.getItem("refreshToken")

export const setTokens = ({ token, refreshToken, userId }) => {
  if (token) {
    localStorage.setItem("accessToken", token)
    localStorage.setItem("token", token)
  }
  if (refreshToken) localStorage.setItem("refreshToken", refreshToken)
  if (userId != null) localStorage.setItem("userId", String(userId))
}

export const clearTokens = () => {
  localStorage.removeItem("accessToken")
  localStorage.removeItem("token")
  localStorage.removeItem("refreshToken")
  localStorage.removeItem("userId")
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
