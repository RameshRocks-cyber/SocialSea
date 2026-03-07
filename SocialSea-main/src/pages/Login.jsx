import { useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { API_BASE } from "../services/api"
import { setTokens } from "../services/auth"

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [msg, setMsg] = useState("")

  const handleLogin = async () => {
    try {
      const identifier = username.trim()
      const trimmedPassword = password
      const res = await fetch(`${API_BASE}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          identifier,
          username: identifier,
          email: identifier.includes("@") ? identifier : undefined,
          password: trimmedPassword
        })
      })
      if (!res.ok) {
        const text = await res.text()
        let message = "Login failed"
        try {
          const parsed = JSON.parse(text)
          message = parsed?.message || parsed?.error || message
        } catch {
          if (text) message = text
        }
        setMsg(message)
        return
      }

      const data = await res.json()
      const token = data.token || data.accessToken
      const refreshToken = data.refreshToken
      const userId = data.userId || data.user?.id
      const role = data.role || data.user?.role
      setTokens({ token, refreshToken, userId, role, user: data.user })
      setMsg("Login successful")
      const from = location.state?.from
      navigate(from || "/feed", { replace: true })
    } catch (error) {
      setMsg("Error: " + error.message)
    }
  }

  return (
    <div style={{ padding: 20 }}>
      <h2>Login</h2>
      <input placeholder="Username" value={username} onChange={e => setUsername(e.target.value)} /><br /><br />
      <input
        placeholder="Password"
        type={showPassword ? "text" : "password"}
        value={password}
        onChange={e => setPassword(e.target.value)}
      /><br />
      <label style={{ display: "inline-flex", alignItems: "center", gap: 6, margin: "8px 0 16px" }}>
        <input
          type="checkbox"
          checked={showPassword}
          onChange={e => setShowPassword(e.target.checked)}
        />
        Show password
      </label><br />
      <button onClick={handleLogin}>Login</button>
      <p>{msg}</p>
    </div>
  )
}
