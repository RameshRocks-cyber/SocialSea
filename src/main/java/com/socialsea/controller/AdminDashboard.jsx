import { useEffect, useState } from "react";
import api from "../axios";

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);

  useEffect(() => {
    api.get("/admin/dashboard")
      .then((res) => setStats(res.data))
      .catch(() => console.error("Failed to load stats"));
  }, []);

  if (!stats) return <p>Loading dashboard...</p>;

  return (
    <div style={{ padding: 20 }}>
      <h2>📊 Admin Dashboard</h2>

      <div style={{ display: "flex", gap: 20, marginBlockStart: 20 }}>
        <StatCard title="👤 Users" value={stats.totalUsers} />
        <StatCard title="📝 Posts" value={stats.totalPosts} />
        <StatCard title="🕵️ Pending Anonymous" value={stats.pendingAnonymousPosts} />
      </div>
    </div>
  );
}

function StatCard({ title, value }) {
  return (
    <div
      style={{
        padding: 20,
        minInlineSize: 180,
        background: "#1f2937",
        color: "white",
        borderRadius: 10,
        textAlign: "center",
      }}
    >
      <h3>{title}</h3>
      <p style={{ fontSize: 28, fontWeight: "bold" }}>{value}</p>
    </div>
  );
}