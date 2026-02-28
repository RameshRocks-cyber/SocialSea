import { useEffect, useState } from "react"
import { API_BASE } from "../services/api"

export default function AnonymousFeed() {
  const [posts, setPosts] = useState([])

  useEffect(() => {
    fetch(`${API_BASE}/api/anonymous/feed`)
      .then(res => res.json())
      .then(data => setPosts(Array.isArray(data) ? data : []))
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
      <h2>Anonymous Feed</h2>

      {posts.length === 0 && <p>No posts yet.</p>}

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
