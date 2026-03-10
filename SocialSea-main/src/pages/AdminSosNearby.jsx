import { useEffect, useState } from "react"
import { API_BASE } from "../services/api"
import { authFetch } from "../services/auth"

const fmt = value => {
  if (!value) return "-"
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return "-"
  return d.toLocaleString()
}

export default function AdminSosNearby() {
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    authFetch(`${API_BASE}/api/admin/sos-nearby`)
      .then(async res => {
        if (!res.ok) {
          const text = await res.text()
          throw new Error(text || `Request failed (${res.status})`)
        }
        return res.json()
      })
      .then(data => {
        setRows(Array.isArray(data) ? data : [])
      })
      .catch(err => setError(err.message || "Failed to load SOS nearby data"))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div style={{ padding: 20 }}>
      <h2>Admin SOS Nearby (5 km)</h2>
      <p style={{ marginTop: 6, opacity: 0.85 }}>
        Alerts triggered via SOS 3-tap, with users found within 5 km radius.
      </p>

      {loading && <p>Loading SOS alerts...</p>}
      {error && <p style={{ color: "#ff9f9f" }}>{error}</p>}
      {!loading && !error && rows.length === 0 && <p>No SOS alerts found.</p>}

      {!loading && !error && rows.map(alert => (
        <div
          key={alert.alertId}
          style={{
            marginBottom: 18,
            border: "1px solid #2f3e57",
            borderRadius: 10,
            background: "#101926",
            color: "white",
            padding: 14
          }}
        >
          <div style={{ display: "flex", flexWrap: "wrap", gap: 12, marginBottom: 10 }}>
            <span><b>Alert:</b> {alert.alertId}</span>
            <span><b>Reporter:</b> {alert.reporterName || "-"} ({alert.reporterEmail || "-"})</span>
            <span><b>Started:</b> {fmt(alert.startedAt)}</span>
            <span><b>Status:</b> {alert.active ? "Active" : "Stopped"}</span>
            <span><b>Location:</b> {alert.latitude ?? "-"}, {alert.longitude ?? "-"}</span>
            <span><b>Radius:</b> {alert.radiusMeters ?? 5000} m</span>
            <span><b>Nearby users:</b> {alert.nearbyCount ?? 0}</span>
          </div>

          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr style={{ textAlign: "left", borderBottom: "1px solid #2f3e57" }}>
                  <th style={{ padding: "8px 6px" }}>Name</th>
                  <th style={{ padding: "8px 6px" }}>Email</th>
                  <th style={{ padding: "8px 6px" }}>Distance</th>
                  <th style={{ padding: "8px 6px" }}>Lat</th>
                  <th style={{ padding: "8px 6px" }}>Lon</th>
                  <th style={{ padding: "8px 6px" }}>Location Updated</th>
                </tr>
              </thead>
              <tbody>
                {Array.isArray(alert.nearbyUsers) && alert.nearbyUsers.length > 0 ? (
                  alert.nearbyUsers.map(user => (
                    <tr key={`${alert.alertId}-${user.id}`} style={{ borderBottom: "1px solid #1d2a3c" }}>
                      <td style={{ padding: "8px 6px" }}>{user.name || "-"}</td>
                      <td style={{ padding: "8px 6px" }}>{user.email || "-"}</td>
                      <td style={{ padding: "8px 6px" }}>{user.distanceMeters ?? "-"} m</td>
                      <td style={{ padding: "8px 6px" }}>{user.latitude ?? "-"}</td>
                      <td style={{ padding: "8px 6px" }}>{user.longitude ?? "-"}</td>
                      <td style={{ padding: "8px 6px" }}>{fmt(user.locationUpdatedAt)}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} style={{ padding: "10px 6px", opacity: 0.8 }}>
                      No users found within 5 km for this alert.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  )
}

