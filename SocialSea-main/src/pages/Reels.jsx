import { useEffect, useState } from "react"
import { API_BASE } from "../services/api"
import { authFetch } from "../services/auth"
import "./Reels.css"

const MAX_REEL_SECONDS = 90

export default function Reels() {
  const [reels, setReels] = useState([])
  const [error, setError] = useState("")

  useEffect(() => {
    authFetch(`${API_BASE}/api/reels`)
      .then(async res => {
        if (!res.ok) {
          const text = await res.text()
          throw new Error(text || `Request failed (${res.status})`)
        }
        return res.json()
      })
      .then(async data => {
        const list = Array.isArray(data) ? data : []
        const withDuration = await Promise.all(
          list.map(async reel => {
            const mediaType = getMediaType(reel)
            if (mediaType !== "VIDEO") return null
            const rawUrl = reel.contentUrl || reel.mediaUrl || ""
            const mediaUrl = resolveUrl(String(rawUrl).trim())
            if (!mediaUrl) return null
            const durationSeconds = await readVideoDuration(mediaUrl)
            if (!Number.isFinite(durationSeconds) || durationSeconds <= 0) return null
            if (durationSeconds > MAX_REEL_SECONDS) return null
            return reel
          })
        )
        setReels(withDuration.filter(Boolean))
      })
      .catch(err => setError(err.message || "Failed to load reels"))
  }, [])

  const resolveUrl = url => {
    if (!url) return ""
    return url.startsWith("http") ? url : `${API_BASE}${url}`
  }

  const getMediaType = reel => {
    const type = (reel?.type || "").toUpperCase()
    if (type) return type
    const url = String(reel?.contentUrl || reel?.mediaUrl || "").toLowerCase()
    if (url.match(/\.(mp4|mov|webm|mkv|m4v)(\?|$)/)) return "VIDEO"
    if (url.match(/\.(png|jpe?g|gif|webp)(\?|$)/)) return "IMAGE"
    return "VIDEO"
  }

  const readVideoDuration = videoUrl =>
    new Promise(resolve => {
      const video = document.createElement("video")
      video.preload = "metadata"
      video.src = videoUrl
      video.onloadedmetadata = () => resolve(Number(video.duration) || 0)
      video.onerror = () => resolve(Number.POSITIVE_INFINITY)
    })

  return (
    <div className="reels-page">
      <h2>Reels</h2>
      {error && <p>{error}</p>}
      {!error && reels.length === 0 && <p>No reels yet (videos must be 1:30 or shorter).</p>}

      <div className="reels-list">
        {reels.map(reel => {
          const rawUrl = reel.contentUrl || reel.mediaUrl || ""
          const mediaUrl = resolveUrl(rawUrl.trim())
          const author = reel.username || reel.user?.email || "Anonymous"

          return (
            <article key={reel.id} className="reel-card">
              <div className="reel-head">
                <strong>{author}</strong>
              </div>
              {!!reel.content && <p className="reel-caption">{reel.content}</p>}
              {!!reel.description && <p className="reel-caption">{reel.description}</p>}

              {mediaUrl ? (
                <video
                  className="reel-video"
                  src={mediaUrl}
                  controls
                  playsInline
                  preload="metadata"
                />
              ) : (
                <p className="reel-missing">No video found for this reel.</p>
              )}
            </article>
          )
        })}
      </div>
    </div>
  )
}
