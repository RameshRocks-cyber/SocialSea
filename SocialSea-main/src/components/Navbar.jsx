import { Link, useNavigate } from "react-router-dom"
import { clearTokens, isAdmin, isAuthenticated } from "../services/auth"

export default function Navbar() {
  const navigate = useNavigate()
  const authed = isAuthenticated()
  const admin = isAdmin()

  if (!authed) return null

  const handleLogout = () => {
    clearTokens()
    navigate("/login", { replace: true })
  }

  return (
    <nav style={{ padding: 12, background: "#111" }}>
      <Link to="/" style={{ marginInlineEnd: 15, color: "white" }}>Feed</Link>
      <Link to="/reels" style={{ marginInlineEnd: 15, color: "white" }}>Reels</Link>
      <Link to="/upload" style={{ marginInlineEnd: 15, color: "white" }}>Upload</Link>
      <Link to="/chat" style={{ marginInlineEnd: 15, color: "white" }}>Chat</Link>
      <Link to="/anonymous-upload" style={{ marginInlineEnd: 15, color: "white" }}>Anonymous Upload</Link>
      <Link to="/anonymous-feed" style={{ marginInlineEnd: 15, color: "white" }}>Anonymous Feed</Link>
      {admin && (
        <>
          <Link to="/admin/dashboard" style={{ marginInlineEnd: 15, color: "white" }}>Admin Dashboard</Link>
          <Link to="/admin/pending" style={{ marginInlineEnd: 15, color: "white" }}>Admin Pending</Link>
        </>
      )}
      <button type="button" onClick={handleLogout}>Logout</button>
    </nav>
  )
}
