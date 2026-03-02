import { useState } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { API_BASE } from "../services/api"
import { setTokens } from "../services/auth"

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [msg, setMsg] = useState("")

  const handleLogin = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
      })
      if (!res.ok) {
        setMsg("Login failed")
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
      <input placeholder="Password" type="password" value={password} onChange={e => setPassword(e.target.value)} /><br /><br />
      <button onClick={handleLogin}>Login</button>
      <p>{msg}</p>
    </div>
  )
}
