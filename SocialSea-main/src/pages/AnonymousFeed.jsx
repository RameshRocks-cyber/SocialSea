import { useEffect, useState } from "react"
import { API_BASE } from "../services/api"

export default function AnonymousFeed() {
  const [posts, setPosts] = useState([])
  const [viewedById, setViewedById] = useState({})

  const readCount = (item, keys) => {
    for (const key of keys) {
      const value = Number(item?.[key])
      if (Number.isFinite(value) && value >= 0) return value
    }
    return 0
  }

  useEffect(() => {
    fetch(`${API_BASE}/api/anonymous/feed`)
      .then(res => res.json())
      .then(data =>
        setPosts(
          (Array.isArray(data) ? data : []).map(item => ({
            ...item,
            likeCount: readCount(item, ["likeCount", "likesCount", "likes"]),
            viewCount: readCount(item, ["viewCount", "viewsCount", "views"])
          }))
        )
      )
  }, [])

  const resolveUrl = url => {
    if (!url) return ""
    return url.startsWith("http") ? url : `${API_BASE}${url}`
  }

  const isVideo = post => {
    const type = (post?.type || "").toLowerCase()
    return type === "video" || type.startsWith("video")
  }

  const updateCounts = (id, payload) => {
    const likeCount = readCount(payload, ["likeCount", "likesCount", "likes"])
    const viewCount = readCount(payload, ["viewCount", "viewsCount", "views"])
    setPosts(prev =>
      prev.map(post =>
        post.id === id
          ? {
              ...post,
              likeCount: likeCount || post.likeCount || 0,
              viewCount: viewCount || post.viewCount || 0
            }
          : post
      )
    )
  }

  const likePost = async id => {
    if (!id) return
    try {
      const res = await fetch(`${API_BASE}/api/anonymous/${id}/like`, { method: "POST" })
      const data = await res.json().catch(() => ({}))
      updateCounts(id, data)
    } catch {
      setPosts(prev =>
        prev.map(post => (post.id === id ? { ...post, likeCount: (post.likeCount || 0) + 1 } : post))
      )
    }
  }

  const markViewed = async id => {
    if (!id || viewedById[id]) return
    setViewedById(prev => ({ ...prev, [id]: true }))
    try {
      const res = await fetch(`${API_BASE}/api/anonymous/${id}/view`, { method: "POST" })
      const data = await res.json().catch(() => ({}))
      updateCounts(id, data)
    } catch {
      setPosts(prev =>
        prev.map(post => (post.id === id ? { ...post, viewCount: (post.viewCount || 0) + 1 } : post))
      )
    }
  }

  return (
    <div style={{ padding: 20 }}>
      <h2>Anonymous Feed</h2>

      {posts.length === 0 && <p>No posts yet.</p>}

      {posts.map(post => (
        <div key={post.id} style={{ marginBottom: 20 }}>
          <p style={{ marginBottom: 8, color: "#8cb7ff" }}>
            <strong>Anonymous Post</strong>
          </p>
          {isVideo(post) ? (
            <video
              src={resolveUrl(post.contentUrl)}
              controls
              width="320"
              style={{ display: "block" }}
              onLoadedData={() => markViewed(post.id)}
            />
          ) : (
            <img
              src={resolveUrl(post.contentUrl)}
              alt={post.description || "Anonymous post"}
              width="320"
              style={{ display: "block" }}
              onLoad={() => markViewed(post.id)}
            />
          )}
          {post.description && <p>{post.description}</p>}
          <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
            <button type="button" onClick={() => likePost(post.id)}>
              Like ({post.likeCount || 0})
            </button>
            <span>{post.viewCount || 0} views</span>
          </div>
        </div>
      ))}
    </div>
  )
}
