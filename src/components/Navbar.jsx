import { Link } from "react-router-dom"

export default function Navbar() {
  return (
    <nav style={{ padding: 12, background: "#111" }}>
      <Link to="/" style={{ marginInlineEnd: 15, color: "white" }}>Feed</Link>
      <Link to="/upload" style={{ marginInlineEnd: 15, color: "white" }}>Upload</Link>
      <Link to="/anonymous-upload" style={{ marginInlineEnd: 15, color: "white" }}>Anonymous Upload</Link>
      <Link to="/anonymous-feed" style={{ color: "white" }}>Anonymous Feed</Link>
    </nav>
  )
}
