import { useState } from "react"

export default function Login() {
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [msg, setMsg] = useState("")

  const handleLogin = async () => {
    try {
      const res = await fetch("http://localhost:8080/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
      })
      setMsg(res.ok ? "Login successful" : "Login failed")
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
