import { useEffect, useState } from "react"
import { API_BASE } from "../services/api"
import { authFetch } from "../services/auth"

export default function AdminPendingAnonymous() {
  const [posts, setPosts] = useState([])
  const [error, setError] = useState("")

  useEffect(() => {
    authFetch(`${API_BASE}/api/admin/anonymous/pending`)
      .then(async res => {
        if (!res.ok) {
          const text = await res.text()
          throw new Error(text || `Request failed (${res.status})`)
        }
        return res.json()
      })
      .then(data => setPosts(Array.isArray(data) ? data : []))
      .catch(err => setError(err.message || "Failed to load pending posts"))
  }, [])

  const resolveUrl = url => {
    if (!url) return ""
    return url.startsWith("http") ? url : `${API_BASE}${url}`
  }

  const isVideo = post => {
    const type = (post?.type || "").toLowerCase()
    return type === "video" || type.startsWith("video")
  }

  return (
    <div style={{ padding: 20 }}>
      <h2>Pending Anonymous</h2>
      {error && <p>{error}</p>}
      {!error && posts.length === 0 && <p>No pending posts.</p>}

      {posts.map(post => (
        <div key={post.id} style={{ marginBottom: 20 }}>
          {isVideo(post) ? (
            <video
              src={resolveUrl(post.contentUrl)}
              controls
              width="320"
              style={{ display: "block" }}
            />
          ) : (
            <img
              src={resolveUrl(post.contentUrl)}
              alt={post.description || "Anonymous post"}
              width="320"
              style={{ display: "block" }}
            />
          )}
          {post.description && <p>{post.description}</p>}
        </div>
      ))}
    </div>
  )
}
