import { useEffect, useState } from "react"
import { API_BASE } from "../services/api"
import { authFetch } from "../services/auth"

function StatCard({ label, value }) {
  return (
    <div
      style={{
        minWidth: 180,
        padding: 16,
        borderRadius: 10,
        background: "#1f2937",
        color: "white"
      }}
    >
      <p style={{ margin: 0 }}>{label}</p>
      <p style={{ margin: "8px 0 0", fontSize: 28, fontWeight: 700 }}>{value ?? 0}</p>
    </div>
  )
}

export default function AdminDashboard() {
  const [stats, setStats] = useState(null)
  const [error, setError] = useState("")

  useEffect(() => {
    authFetch(`${API_BASE}/api/admin/dashboard/stats`)
      .then(async res => {
        if (!res.ok) {
          const text = await res.text()
          throw new Error(text || `Request failed (${res.status})`)
        }
        return res.json()
      })
      .then(data => setStats(data || {}))
      .catch(err => setError(err.message || "Failed to load admin dashboard"))
  }, [])

  return (
    <div style={{ padding: 20 }}>
      <h2>Admin Dashboard</h2>
      {error && <p>{error}</p>}
      {!error && !stats && <p>Loading dashboard...</p>}

      {!!stats && (
        <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
          <StatCard label="Total Users" value={stats.totalUsers} />
          <StatCard label="Total Posts" value={stats.totalPosts} />
          <StatCard label="Pending Anonymous" value={stats.pendingAnonymousPosts} />
        </div>
      )}
    </div>
  )
}
