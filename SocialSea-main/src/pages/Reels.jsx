import { useEffect, useRef, useState } from "react"
import { API_BASE } from "../services/api"
import { authFetch } from "../services/auth"
import "./Reels.css"

const MAX_REEL_SECONDS = 90
const GESTURE_COOLDOWN_MS = 1300
const GESTURE_SCRIPT_TF = "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.22.0/dist/tf.min.js"
const GESTURE_SCRIPT_HANDPOSE =
  "https://cdn.jsdelivr.net/npm/@tensorflow-models/handpose@0.0.7/dist/handpose.min.js"

function loadScript(src, id) {
  if (document.getElementById(id)) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const script = document.createElement("script")
    script.src = src
    script.id = id
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(`Failed to load ${src}`))
    document.head.appendChild(script)
  })
}

export default function Reels() {
  const [reels, setReels] = useState([])
  const [gestureEnabled, setGestureEnabled] = useState(false)
  const [gestureStatus, setGestureStatus] = useState("Gesture control is off")
  const [gestureError, setGestureError] = useState("")
  const [likedByGesture, setLikedByGesture] = useState({})
  const [error, setError] = useState("")
  const reelNodesRef = useRef(new Map())
  const cameraVideoRef = useRef(null)
  const cameraStreamRef = useRef(null)
  const detectionFrameRef = useRef(0)
  const handModelRef = useRef(null)
  const gestureRunningRef = useRef(false)
  const lastGestureAtRef = useRef(0)
  const lastLikeAtRef = useRef(0)

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

  useEffect(
    () => () => {
      stopGestureControl()
    },
    []
  )

  useEffect(() => {
    if (!gestureEnabled) {
      stopGestureControl()
      setGestureStatus("Gesture control is off")
      setGestureError("")
      return
    }
    startGestureControl().catch(err => {
      setGestureError(err.message || "Failed to start hand gesture control")
      setGestureEnabled(false)
      stopGestureControl()
    })
  }, [gestureEnabled])

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

  const setReelNode = (id, node) => {
    if (node) reelNodesRef.current.set(id, node)
    else reelNodesRef.current.delete(id)
  }

  const getCenteredReelIndex = () => {
    const nodes = reels
      .map(reel => reelNodesRef.current.get(reel.id))
      .filter(Boolean)
    if (!nodes.length) return -1

    const viewportCenter = window.innerHeight / 2
    let winner = 0
    let smallestDelta = Number.POSITIVE_INFINITY
    for (let i = 0; i < nodes.length; i += 1) {
      const rect = nodes[i].getBoundingClientRect()
      const center = rect.top + rect.height / 2
      const delta = Math.abs(center - viewportCenter)
      if (delta < smallestDelta) {
        smallestDelta = delta
        winner = i
      }
    }
    return winner
  }

  const scrollToReelByOffset = offset => {
    if (!reels.length) return
    const current = getCenteredReelIndex()
    if (current < 0) return
    const next = Math.max(0, Math.min(reels.length - 1, current + offset))
    const reel = reels[next]
    const node = reel ? reelNodesRef.current.get(reel.id) : null
    if (!node) return
    node.scrollIntoView({ behavior: "smooth", block: "center" })
  }

  const likeCenteredReel = async () => {
    const current = getCenteredReelIndex()
    if (current < 0) return
    const reel = reels[current]
    if (!reel || likedByGesture[reel.id]) return
    const now = Date.now()
    if (now - lastLikeAtRef.current < GESTURE_COOLDOWN_MS) return
    lastLikeAtRef.current = now
    try {
      const res = await authFetch(`${API_BASE}/api/likes/${reel.id}`, { method: "POST" })
      if (!res.ok) {
        setGestureStatus("Hand detected: unable to like this reel")
        return
      }
      setLikedByGesture(prev => ({ ...prev, [reel.id]: true }))
      setGestureStatus("Hand detected: liked this reel")
    } catch {
      setGestureStatus("Hand detected: unable to like this reel")
    }
  }

  const classifyGesture = landmarks => {
    if (!landmarks || landmarks.length < 21) return "none"

    const wrist = landmarks[0]
    const indexTip = landmarks[8]
    const indexPip = landmarks[6]
    const middleTip = landmarks[12]
    const middlePip = landmarks[10]
    const ringTip = landmarks[16]
    const ringPip = landmarks[14]
    const pinkyTip = landmarks[20]
    const pinkyPip = landmarks[18]

    const indexUp = indexTip[1] < indexPip[1] - 16
    const indexDown = indexTip[1] > indexPip[1] + 16
    const middleFolded = middleTip[1] > middlePip[1] - 5
    const ringFolded = ringTip[1] > ringPip[1] - 5
    const pinkyFolded = pinkyTip[1] > pinkyPip[1] - 5

    const allTipsCloseToWrist =
      Math.abs(indexTip[1] - wrist[1]) < 80 &&
      Math.abs(middleTip[1] - wrist[1]) < 80 &&
      Math.abs(ringTip[1] - wrist[1]) < 80 &&
      Math.abs(pinkyTip[1] - wrist[1]) < 80

    const fist = middleFolded && ringFolded && pinkyFolded && !indexUp && !indexDown && allTipsCloseToWrist
    if (fist) return "like"
    if (indexUp && middleFolded && ringFolded && pinkyFolded) return "scroll-up"
    if (indexDown && middleFolded && ringFolded && pinkyFolded) return "scroll-down"
    return "none"
  }

  const stopGestureControl = () => {
    gestureRunningRef.current = false
    if (detectionFrameRef.current) {
      cancelAnimationFrame(detectionFrameRef.current)
      detectionFrameRef.current = 0
    }
    if (cameraVideoRef.current) {
      cameraVideoRef.current.pause()
      cameraVideoRef.current.srcObject = null
      cameraVideoRef.current = null
    }
    if (cameraStreamRef.current) {
      cameraStreamRef.current.getTracks().forEach(track => track.stop())
      cameraStreamRef.current = null
    }
  }

  const startGestureControl = async () => {
    if (gestureRunningRef.current) return
    setGestureError("")
    setGestureStatus("Starting hand gesture control...")
    await loadScript(GESTURE_SCRIPT_TF, "tfjs-gesture")
    await loadScript(GESTURE_SCRIPT_HANDPOSE, "handpose-gesture")
    if (!window.handpose) throw new Error("Hand model is unavailable in this browser")

    if (!handModelRef.current) {
      handModelRef.current = await window.handpose.load()
    }

    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user" },
      audio: false
    })
    cameraStreamRef.current = stream

    const video = document.createElement("video")
    video.autoplay = true
    video.muted = true
    video.playsInline = true
    video.width = 320
    video.height = 240
    video.srcObject = stream
    cameraVideoRef.current = video
    await video.play()

    gestureRunningRef.current = true
    setGestureStatus("Gesture control active")

    const detect = async () => {
      if (!gestureRunningRef.current || !cameraVideoRef.current || !handModelRef.current) return
      try {
        const predictions = await handModelRef.current.estimateHands(cameraVideoRef.current, true)
        if (predictions.length > 0) {
          const gesture = classifyGesture(predictions[0].landmarks)
          const now = Date.now()
          if (gesture !== "none" && now - lastGestureAtRef.current > GESTURE_COOLDOWN_MS) {
            lastGestureAtRef.current = now
            if (gesture === "scroll-up") {
              setGestureStatus("Hand detected: scrolling up")
              scrollToReelByOffset(-1)
            } else if (gesture === "scroll-down") {
              setGestureStatus("Hand detected: scrolling down")
              scrollToReelByOffset(1)
            } else if (gesture === "like") {
              likeCenteredReel()
            }
          }
        }
      } catch {
        setGestureError("Unable to read hand gestures from camera")
      }
      detectionFrameRef.current = requestAnimationFrame(detect)
    }
    detectionFrameRef.current = requestAnimationFrame(detect)
  }

  return (
    <div className="reels-page">
      <h2>Reels</h2>
      <div className="reels-gesture-toggle">
        <label htmlFor="gesture-enable">
          <input
            id="gesture-enable"
            type="checkbox"
            checked={gestureEnabled}
            onChange={e => setGestureEnabled(e.target.checked)}
          />
          Enable hand signal control
        </label>
        <p className="reels-gesture-help">
          Index finger up: scroll up, index finger down: scroll down, closed fist: like
        </p>
        <p className="reels-gesture-status">{gestureStatus}</p>
        {!!gestureError && <p className="reels-gesture-error">{gestureError}</p>}
      </div>
      {error && <p>{error}</p>}
      {!error && reels.length === 0 && <p>No reels yet (videos must be 1:30 or shorter).</p>}

      <div className="reels-list">
        {reels.map(reel => {
          const rawUrl = reel.contentUrl || reel.mediaUrl || ""
          const mediaUrl = resolveUrl(rawUrl.trim())
          const author = reel.username || reel.user?.email || "Anonymous"

          return (
            <article key={reel.id} className="reel-card" ref={node => setReelNode(reel.id, node)}>
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
