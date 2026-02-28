import { useEffect, useState } from "react"
import { API_BASE } from "../services/api"
import { authFetch, getToken } from "../services/auth"
import "./Feed.css"

const MAX_REEL_SECONDS = 120

export default function Feed() {
  const [posts, setPosts] = useState([])
  const [likeCounts, setLikeCounts] = useState({})
  const [commentsByPost, setCommentsByPost] = useState({})
  const [commentTextByPost, setCommentTextByPost] = useState({})
  const [commentsOpenByPost, setCommentsOpenByPost] = useState({})
  const [followingByEmail, setFollowingByEmail] = useState({})
  const [savedPostIds, setSavedPostIds] = useState({})
  const [shareMessageByPost, setShareMessageByPost] = useState({})
  const [error, setError] = useState("")
  const [currentEmail, setCurrentEmail] = useState("")
  const [videoDurationByPost, setVideoDurationByPost] = useState({})

  const parseEmailFromToken = token => {
    try {
      const [, payload] = token.split(".")
      if (!payload) return ""
      const json = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")))
      return json?.sub || ""
    } catch {
      return ""
    }
  }

  useEffect(() => {
    const token = getToken()
    if (!token) {
      setError("Login required to view feed")
      return
    }
    setCurrentEmail(parseEmailFromToken(token))

    authFetch(`${API_BASE}/api/feed`)
      .then(async res => {
        if (!res.ok) {
          const text = await res.text()
          throw new Error(text || `Request failed (${res.status})`)
        }
        return res.json()
      })
      .then(data => setPosts(Array.isArray(data) ? data : []))
      .catch(err => setError(err.message || "Failed to load feed"))
  }, [])

  useEffect(() => {
    try {
      const raw = localStorage.getItem("savedPostIds")
      if (!raw) return
      const ids = JSON.parse(raw)
      if (!Array.isArray(ids)) return
      const map = ids.reduce((acc, id) => ({ ...acc, [id]: true }), {})
      setSavedPostIds(map)
    } catch {
      // ignore invalid localStorage payload
    }
  }, [])

  useEffect(() => {
    if (!posts.length) return

    posts.forEach(post => {
      authFetch(`${API_BASE}/api/likes/${post.id}/count`)
        .then(res => (res.ok ? res.text() : "0"))
        .then(text => {
          const count = Number(text) || 0
          setLikeCounts(prev => ({ ...prev, [post.id]: count }))
        })
        .catch(() => {})
    })
  }, [posts])

  const resolveUrl = url => {
    if (!url) return ""
    return url.startsWith("http") ? url : `${API_BASE}${url}`
  }

  const getMediaType = post => {
    const type = (post?.type || "").toUpperCase()
    if (type) return type
    const url = String(post?.contentUrl || post?.mediaUrl || "").toLowerCase()
    if (url.match(/\.(mp4|mov|webm|mkv|m4v)(\?|$)/)) return "VIDEO"
    if (url.match(/\.(png|jpe?g|gif|webp)(\?|$)/)) return "IMAGE"
    return post?.reel ? "VIDEO" : "IMAGE"
  }

  const loadComments = async postId => {
    const res = await authFetch(`${API_BASE}/api/comments/${postId}`)
    if (!res.ok) return
    const data = await res.json()
    setCommentsByPost(prev => ({ ...prev, [postId]: Array.isArray(data) ? data : [] }))
  }

  const likePost = async postId => {
    const res = await authFetch(`${API_BASE}/api/likes/${postId}`, { method: "POST" })
    if (!res.ok) return
    setLikeCounts(prev => ({ ...prev, [postId]: (prev[postId] || 0) + 1 }))
  }

  const toggleComments = async postId => {
    const nextOpen = !commentsOpenByPost[postId]
    setCommentsOpenByPost(prev => ({ ...prev, [postId]: nextOpen }))
    if (nextOpen) await loadComments(postId)
  }

  const submitComment = async postId => {
    const text = (commentTextByPost[postId] || "").trim()
    if (!text) return

    const res = await authFetch(`${API_BASE}/api/comments/${postId}`, {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body: text
    })
    if (!res.ok) return

    setCommentTextByPost(prev => ({ ...prev, [postId]: "" }))
    await loadComments(postId)
  }

  const followAuthor = async authorEmail => {
    if (!authorEmail || authorEmail === currentEmail) return

    const res = await authFetch(`${API_BASE}/api/follow/${encodeURIComponent(authorEmail)}`, {
      method: "POST"
    })
    if (!res.ok) return

    setFollowingByEmail(prev => ({ ...prev, [authorEmail]: true }))
  }

  const sharePost = async post => {
    const shareUrl = `${window.location.origin}${window.location.pathname}?post=${post.id}`
    const shareText = `${post.content || post.description || "Check this post"} ${shareUrl}`

    try {
      if (navigator.share) {
        await navigator.share({ title: "SocialSea Post", text: shareText, url: shareUrl })
      } else if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(shareUrl)
      }
      setShareMessageByPost(prev => ({ ...prev, [post.id]: "Shared" }))
      setTimeout(() => {
        setShareMessageByPost(prev => ({ ...prev, [post.id]: "" }))
      }, 1500)
    } catch {
      setShareMessageByPost(prev => ({ ...prev, [post.id]: "Share cancelled" }))
      setTimeout(() => {
        setShareMessageByPost(prev => ({ ...prev, [post.id]: "" }))
      }, 1500)
    }
  }

  const toggleSave = postId => {
    setSavedPostIds(prev => {
      const next = { ...prev, [postId]: !prev[postId] }
      const savedIds = Object.keys(next)
        .filter(id => next[id])
        .map(id => Number(id))
      localStorage.setItem("savedPostIds", JSON.stringify(savedIds))
      return next
    })
  }

  return (
    <div style={{ padding: 20 }}>
      <h2>Main Feed</h2>
      {error && <p>{error}</p>}
      {!error && posts.length === 0 && <p>No posts yet.</p>}

      {posts.map(post => {
        const rawUrl = post.contentUrl || post.mediaUrl || ""
        const trimmedUrl = rawUrl.trim()
        const mediaUrl = trimmedUrl ? resolveUrl(trimmedUrl) : ""
        const type = getMediaType(post)
        const authorEmail = post.username || post.user?.email || ""
        const displayName = authorEmail || "Anonymous"
        const isOwnPost = !!currentEmail && authorEmail === currentEmail
        const isLongVideo = type === "VIDEO" && (videoDurationByPost[post.id] || 0) > MAX_REEL_SECONDS
        return (
          <div key={post.id} className={`feed-post ${isLongVideo ? "feed-post-long" : ""}`}>
            <div className="feed-post-head">
              <strong>{displayName}</strong>
              {isLongVideo && <span className="long-video-chip">Long Video</span>}
              {!isOwnPost && !!authorEmail && (
                <button
                  className="feed-follow-btn"
                  onClick={() => followAuthor(authorEmail)}
                  disabled={!!followingByEmail[authorEmail]}
                  type="button"
                >
                  {followingByEmail[authorEmail] ? "Following" : "Follow +"}
                </button>
              )}
            </div>
            {post.content && <p>{post.content}</p>}
            {post.description && <p>{post.description}</p>}

            {mediaUrl && type === "IMAGE" && (
              <img src={mediaUrl} alt="post" className="feed-media" />
            )}

            {mediaUrl && type === "VIDEO" && (
              <video
                src={mediaUrl}
                controls
                className="feed-media"
                onLoadedMetadata={e =>
                  setVideoDurationByPost(prev => ({
                    ...prev,
                    [post.id]: Number(e.currentTarget.duration) || 0
                  }))
                }
              />
            )}

            <div className="feed-actions">
              <button type="button" onClick={() => likePost(post.id)}>
                Like {likeCounts[post.id] != null ? `(${likeCounts[post.id]})` : ""}
              </button>
              <button type="button" onClick={() => toggleComments(post.id)}>
                Comment ({(commentsByPost[post.id] || []).length})
              </button>
              <button type="button" onClick={() => sharePost(post)}>
                Share {shareMessageByPost[post.id] ? `(${shareMessageByPost[post.id]})` : ""}
              </button>
              <button
                type="button"
                className={savedPostIds[post.id] ? "is-saved" : ""}
                onClick={() => toggleSave(post.id)}
              >
                {savedPostIds[post.id] ? "Saved" : "Save"}
              </button>
            </div>

            {commentsOpenByPost[post.id] && (
              <div className="feed-comments">
                <div className="feed-comment-input-row">
                  <input
                    type="text"
                    placeholder="Write a comment..."
                    value={commentTextByPost[post.id] || ""}
                    onChange={e =>
                      setCommentTextByPost(prev => ({ ...prev, [post.id]: e.target.value }))
                    }
                  />
                  <button type="button" onClick={() => submitComment(post.id)}>
                    Post
                  </button>
                </div>

                {(commentsByPost[post.id] || []).map(comment => (
                  <div className="feed-comment-item" key={comment.id}>
                    <strong>{comment.user?.email || "User"}:</strong> {comment.text}
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
