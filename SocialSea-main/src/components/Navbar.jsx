import { useEffect, useRef, useState } from "react"
import { Link, useLocation, useNavigate } from "react-router-dom"
import { clearTokens, getToken, isAdmin, isAuthenticated, parseJwtPayload } from "../services/auth"
import api from "../services/axios"

export default function Navbar() {
  const navigate = useNavigate()
  const location = useLocation()
  const authed = isAuthenticated()
  const admin = isAdmin()
  const [sosActive, setSosActive] = useState(false)
  const [sosBusy, setSosBusy] = useState(false)
  const [sosError, setSosError] = useState("")
  const [emergencyPopup, setEmergencyPopup] = useState(null)
  const [stopTapCount, setStopTapCount] = useState(0)
  const [stopLabel, setStopLabel] = useState("[SOS] X STOP (tap 4x)")
  const [coordsText, setCoordsText] = useState("")
  const clickTimesRef = useRef([])
  const stopTapMetaRef = useRef({ lastTapAt: 0, count: 0 })
  const sosStateRef = useRef({ active: false, busy: false })
  const alertIdRef = useRef(null)
  const startedAtRef = useRef(0)
  const mediaRecorderRef = useRef(null)
  const mediaChunksRef = useRef([])
  const mediaStreamRef = useRef(null)
  const extraStreamsRef = useRef([])
  const audioCtxRef = useRef(null)
  const oscRef = useRef(null)
  const gainRef = useRef(null)
  const seenNotificationIdsRef = useRef(new Set())
  const seenActiveAlertIdsRef = useRef(new Set())
  const notificationsInitializedRef = useRef(false)

  if (!authed) return null
  if (location.pathname.startsWith("/camera")) return null

  const handleLogout = () => {
    forceStopSos()
    clearTokens()
    navigate("/login", { replace: true })
  }

  const getLocation = () =>
    new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        reject(new Error("Geolocation is not supported on this device"))
        return
      }
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 2000,
      })
    })

  const safeGetUserMedia = async (constraints) => {
    try {
      return await navigator.mediaDevices.getUserMedia(constraints)
    } catch {
      return null
    }
  }

  const startAlarm = async () => {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    if (audioCtxRef.current) return
    const ctx = new Ctx()
    const oscillator = ctx.createOscillator()
    const gain = ctx.createGain()

    oscillator.type = "square"
    oscillator.frequency.value = 900
    gain.gain.value = 0.0001
    oscillator.connect(gain)
    gain.connect(ctx.destination)
    oscillator.start()

    audioCtxRef.current = ctx
    oscRef.current = oscillator
    gainRef.current = gain

    let up = true
    const pulse = () => {
      if (!gainRef.current || !audioCtxRef.current) return
      const now = audioCtxRef.current.currentTime
      gainRef.current.gain.cancelScheduledValues(now)
      gainRef.current.gain.linearRampToValueAtTime(up ? 0.18 : 0.02, now + 0.2)
      up = !up
    }
    pulse()
    const timer = setInterval(pulse, 250)
    gainRef.current._pulseTimer = timer
  }

  const stopAlarm = () => {
    const gain = gainRef.current
    if (gain && gain._pulseTimer) {
      clearInterval(gain._pulseTimer)
    }
    if (oscRef.current) {
      try {
        oscRef.current.stop()
      } catch {
        // no-op
      }
    }
    if (audioCtxRef.current) {
      audioCtxRef.current.close()
    }
    oscRef.current = null
    gainRef.current = null
    audioCtxRef.current = null
  }

  const startRecording = async () => {
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      throw new Error("Camera recording is not supported in this browser")
    }

    const front = await safeGetUserMedia({
      video: { facingMode: "user" },
      audio: true,
    })

    const back =
      (await safeGetUserMedia({ video: { facingMode: { exact: "environment" } }, audio: false })) ||
      (await safeGetUserMedia({ video: { facingMode: "environment" }, audio: false }))

    if (!front && !back) {
      throw new Error("Could not access front/back camera")
    }

    const tracks = []
    if (front) tracks.push(...front.getTracks())
    if (back) tracks.push(...back.getVideoTracks())
    const mergedStream = new MediaStream(tracks)
    mediaStreamRef.current = mergedStream
    extraStreamsRef.current = [front, back].filter(Boolean)

    const recorder = new MediaRecorder(mergedStream)
    mediaRecorderRef.current = recorder
    mediaChunksRef.current = []
    recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        mediaChunksRef.current.push(event.data)
      }
    }
    recorder.start(1000)

    return {
      frontCameraEnabled: !!front?.getVideoTracks()?.length,
      backCameraEnabled: !!back?.getVideoTracks()?.length,
    }
  }

  const stopRecording = async () =>
    new Promise((resolve) => {
      const recorder = mediaRecorderRef.current
      if (!recorder) {
        resolve(null)
        return
      }

      recorder.onstop = () => {
        const blob =
          mediaChunksRef.current.length > 0
            ? new Blob(mediaChunksRef.current, { type: "video/webm" })
            : null
        resolve(blob)
      }

      if (recorder.state !== "inactive") {
        recorder.stop()
      } else {
        resolve(null)
      }
    })

  const stopAllMediaTracks = () => {
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((t) => t.stop())
    }
    if (extraStreamsRef.current.length) {
      extraStreamsRef.current.forEach((s) => s?.getTracks?.().forEach((t) => t.stop()))
    }
    mediaStreamRef.current = null
    extraStreamsRef.current = []
    mediaRecorderRef.current = null
    mediaChunksRef.current = []
  }

  const forceStopSos = () => {
    stopAlarm()
    stopAllMediaTracks()
    alertIdRef.current = null
    startedAtRef.current = 0
    stopTapMetaRef.current = { lastTapAt: 0, count: 0 }
    setSosActive(false)
    setSosBusy(false)
    setStopTapCount(0)
    setStopLabel("[SOS] X STOP (tap 4x)")
  }

  const startSos = async () => {
    if (sosBusy || sosActive) return
    setSosError("")
    setSosBusy(true)
    try {
      const pos = await getLocation()
      const { latitude, longitude, accuracy } = pos.coords
      setCoordsText(`${latitude.toFixed(6)}, ${longitude.toFixed(6)} (+/- ${Math.round(accuracy)}m)`)

      const cameraInfo = await startRecording()
      await startAlarm()

      const response = await api.post("/api/emergency/trigger", {
        latitude,
        longitude,
        accuracyMeters: accuracy,
        radiusMeters: 5000,
        frontCameraEnabled: cameraInfo.frontCameraEnabled,
        backCameraEnabled: cameraInfo.backCameraEnabled,
      })

      alertIdRef.current = response?.data?.alertId
      startedAtRef.current = Date.now()
      setSosActive(true)
    } catch (error) {
      forceStopSos()
      setSosError(error?.response?.data?.message || error?.message || "Emergency mode failed to start")
    } finally {
      setSosBusy(false)
    }
  }

  const stopSos = async () => {
    if (sosBusy) return
    setSosBusy(true)
    try {
      stopAlarm()
      const blob = await stopRecording()

      if (alertIdRef.current) {
        const form = new FormData()
        if (blob) {
          form.append("media", blob, `sos-${alertIdRef.current}.webm`)
        }
        const duration = startedAtRef.current > 0 ? Date.now() - startedAtRef.current : 0
        form.append("durationMs", String(duration))

        await api.post(`/api/emergency/${alertIdRef.current}/stop`, form, {
          headers: { "Content-Type": "multipart/form-data" },
        })
      }
    } catch (error) {
      setSosError(error?.response?.data?.message || error?.message || "Emergency stop failed")
    } finally {
      stopAllMediaTracks()
      alertIdRef.current = null
      startedAtRef.current = 0
      stopTapMetaRef.current = { lastTapAt: 0, count: 0 }
      setStopTapCount(0)
      setStopLabel("[SOS] X STOP (tap 4x)")
      setSosActive(false)
      setSosBusy(false)
    }
  }

  const handleLogoClick = () => {
    const now = Date.now()
    clickTimesRef.current = [...clickTimesRef.current.filter((t) => now - t < 1400), now]
    if (clickTimesRef.current.length >= 3) {
      clickTimesRef.current = []
      startSos()
    }
  }

  const handleStopTap = () => {
    if (!sosActive) return
    const now = Date.now()
    const previous = stopTapMetaRef.current
    const count = now - previous.lastTapAt <= 1800 ? previous.count + 1 : 1
    stopTapMetaRef.current = { lastTapAt: now, count }
    setStopTapCount(count)
    setStopLabel(`[SOS] X STOP (tap 4x) ${count}/4`)
    if (count >= 4) {
      stopSos()
    }
  }

  useEffect(() => () => {
    forceStopSos()
  }, [])

  useEffect(() => {
    sosStateRef.current = { active: sosActive, busy: sosBusy }
  }, [sosActive, sosBusy])

  const suppressEmergencyPopups =
    sosStateRef.current.active ||
    sosStateRef.current.busy ||
    location.pathname.startsWith("/sos")

  useEffect(() => {
    if (!emergencyPopup) return
    if (!suppressEmergencyPopups) return
    dismissEmergencyPopup()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [emergencyPopup, suppressEmergencyPopups])

  useEffect(() => {
    let cancelled = false
    const sendPresence = async () => {
      try {
        if (!navigator.geolocation || !navigator.permissions) return
        const status = await navigator.permissions.query({ name: "geolocation" })
        if (status.state !== "granted" || cancelled) return

        navigator.geolocation.getCurrentPosition(
          async (pos) => {
            if (cancelled) return
            await api.post("/api/emergency/presence", {
              latitude: pos.coords.latitude,
              longitude: pos.coords.longitude,
            })
          },
          () => {},
          { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 }
        )
      } catch {
        // no-op
      }
    }
    sendPresence()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let mounted = true
    let timer = null

    const checkEmergencyNotifications = async () => {
      try {
        const res = await api.get("/api/notifications")
        const items = Array.isArray(res?.data) ? res.data : []
        const initialLoad = !notificationsInitializedRef.current
        const me = parseJwtPayload(getToken())?.sub || null
        const suppressPopups = suppressEmergencyPopups
        const isSelfNotification = (n) => {
          if (!n) return false
          const title = String(n.title || "").toLowerCase()
          const message = String(n.message || "")
          if (title.includes("your sos")) return true
          if (message.startsWith("Your SOS")) return true
          if (me && String(n.reporterEmail || "").toLowerCase() === String(me).toLowerCase()) return true
          return false
        }

        if (initialLoad) {
          notificationsInitializedRef.current = true
          const firstUnreadEmergency = items.find((n) => {
            if (n?.id == null || n.read) return false
            if (!(n.kind === "emergency" || String(n.type || "").toUpperCase() === "EMERGENCY")) return false
            if (isSelfNotification(n)) return false
            const incomingAlertId = inferAlertId(n)
            const activeLocalAlertId =
              alertIdRef.current != null ? String(alertIdRef.current) : null
            return !(incomingAlertId && activeLocalAlertId && String(incomingAlertId) === activeLocalAlertId)
          })

          items.forEach((n) => {
            if (n?.id != null) seenNotificationIdsRef.current.add(n.id)
          })

          if (firstUnreadEmergency && mounted) {
            if (!suppressPopups) setEmergencyPopup(firstUnreadEmergency)
          }
          return
        }

        for (const n of items) {
          if (n?.id == null) continue
          if (seenNotificationIdsRef.current.has(n.id)) continue
          seenNotificationIdsRef.current.add(n.id)
          if ((n.kind === "emergency" || String(n.type || "").toUpperCase() === "EMERGENCY") && !n.read) {
            if (isSelfNotification(n)) continue
            const incomingAlertId = inferAlertId(n)
            const activeLocalAlertId =
              alertIdRef.current != null ? String(alertIdRef.current) : null
            if (incomingAlertId && activeLocalAlertId && String(incomingAlertId) === activeLocalAlertId) {
              continue
            }
            if (mounted && !suppressPopups) setEmergencyPopup(n)
            break
          }
        }
      } catch {
        // no-op
      }
    }

    checkEmergencyNotifications()
    timer = setInterval(checkEmergencyNotifications, 6000)
    return () => {
      mounted = false
      if (timer) clearInterval(timer)
    }
  }, [])

  useEffect(() => {
    let mounted = true
    let timer = null

    const checkActiveSos = async () => {
      try {
        const res = await api.get("/api/emergency/active")
        const alerts = Array.isArray(res?.data) ? res.data : []
        const me = parseJwtPayload(getToken())?.sub || null

        for (const a of alerts) {
          const id = a?.alertId
          if (id == null) continue
          if (seenActiveAlertIdsRef.current.has(id)) continue
          seenActiveAlertIdsRef.current.add(id)
          const isSelfAlert =
            me && String(a?.reporterEmail || "").toLowerCase() === String(me).toLowerCase()
          const activeLocalAlertId =
            alertIdRef.current != null ? String(alertIdRef.current) : null
          if (activeLocalAlertId && String(id) === activeLocalAlertId) continue
          if (isSelfAlert) continue
          if (mounted) {
            if (suppressEmergencyPopups) continue
            setEmergencyPopup({
              id: `active-${id}`,
              title: isSelfAlert ? "Emergency Alert" : "Emergency Alert Nearby",
              message: `SOS active by ${a?.reporterEmail || "a user"}`,
              liveUrl: a?.liveUrl,
              navigateUrl: a?.navigateUrl,
              mapsUrl: a?.mapsUrl,
              type: "EMERGENCY",
              kind: "emergency",
              read: false,
            })
          }
          break
        }
      } catch {
        // no-op
      }
    }

    checkActiveSos()
    timer = setInterval(checkActiveSos, 5000)
    return () => {
      mounted = false
      if (timer) clearInterval(timer)
    }
  }, [])

  const dismissEmergencyPopup = async () => {
    const id = emergencyPopup?.id
    setEmergencyPopup(null)
    if (!id) return
    try {
      await api.post(`/api/notifications/${id}/read`)
    } catch {
      // no-op
    }
  }

  const trimUrl = (value) => String(value || "").replace(/[),.;]+$/g, "")

  const extractUrlContaining = (text, marker) => {
    if (!text || !marker) return null
    const matches = String(text).match(/https?:\/\/\S+/g) || []
    const hit = matches.find((url) => url.includes(marker))
    return hit ? trimUrl(hit) : null
  }

  const inferAlertId = (popup) => {
    if (!popup) return null
    if (popup.alertId != null) return String(popup.alertId)
    const idMatch = String(popup.id || "").match(/active-(\d+)/)
    if (idMatch?.[1]) return idMatch[1]
    const text = `${popup.message || ""} ${popup.liveUrl || ""} ${popup.navigateUrl || ""}`
    const urlMatch = text.match(/\/sos\/(?:live|navigate)\/(\d+)/)
    return urlMatch?.[1] || null
  }

  const resolveEmergencyUrls = (popup) => {
    const alertId = inferAlertId(popup)
    const origin = window.location.origin
    const liveUrl =
      popup?.liveUrl ||
      extractUrlContaining(popup?.message, "/sos/live/") ||
      (alertId ? `${origin}/sos/live/${alertId}` : null)
    const navigateUrl =
      popup?.navigateUrl ||
      popup?.mapsUrl ||
      extractUrlContaining(popup?.message, "/sos/navigate/") ||
      extractUrlContaining(popup?.message, "google.com/maps") ||
      (alertId ? `${origin}/sos/navigate/${alertId}` : null)
    return { liveUrl, navigateUrl }
  }

  const openEmergencyLink = (url) => {
    if (!url) return
    window.open(url, "_blank", "noopener,noreferrer")
  }

  return (
    <>
    <nav style={{ padding: 12, background: "#111" }}>
      <button
        type="button"
        onClick={handleLogoClick}
        style={{ marginInlineEnd: 15, color: "white", background: "transparent", border: "1px solid #3b3b3b", borderRadius: 8, padding: "6px 10px", cursor: "pointer" }}
        title="Tap 3 times quickly to start emergency mode"
      >
        <span
          style={{
            display: "inline-block",
            marginRight: 8,
            padding: "1px 6px",
            borderRadius: 999,
            background: "#9f1d1d",
            border: "1px solid #d85f5f",
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: 0.4,
          }}
        >
          SOS
        </span>
        SocialSea
      </button>
      <Link to="/camera" style={{ marginInlineEnd: 15, color: "white" }} title="Open Camera Studio">
        📷
      </Link>
      <Link to="/" style={{ marginInlineEnd: 15, color: "white" }}>Feed</Link>
      <Link to="/reels" style={{ marginInlineEnd: 15, color: "white" }}>Reels</Link>
      <Link to="/upload" style={{ marginInlineEnd: 15, color: "white" }}>Upload</Link>
      <Link to="/chat" style={{ marginInlineEnd: 15, color: "white" }}>Chat</Link>
      <Link to="/anonymous-upload" style={{ marginInlineEnd: 15, color: "white" }}>Anonymous Upload</Link>
      <Link to="/anonymous-feed" style={{ marginInlineEnd: 15, color: "white" }}>Anonymous Feed</Link>
      {admin && (
        <>
          <Link to="/admin/dashboard" style={{ marginInlineEnd: 15, color: "white" }}>Admin Dashboard</Link>
          <Link to="/admin/pending" style={{ marginInlineEnd: 15, color: "white" }}>Admin Pending</Link>
          <Link to="/admin/sos-nearby" style={{ marginInlineEnd: 15, color: "white" }}>Admin SOS Nearby</Link>
        </>
      )}
      <button type="button" onClick={handleLogout}>Logout</button>
    </nav>
    {(sosBusy || sosActive || sosError) && (
      <div style={{
        position: "fixed",
        right: 16,
        bottom: 16,
        zIndex: 9999,
        width: "min(92vw, 420px)",
        background: sosActive ? "#380606" : "#1a1a1a",
        border: "1px solid #6b1212",
        borderRadius: 12,
        padding: 12,
        color: "white",
        boxShadow: "0 10px 30px rgba(0,0,0,0.45)",
      }}>
        <div style={{ fontWeight: 700, marginBottom: 6 }}>
          {sosActive ? "[SOS] EMERGENCY ACTIVE" : sosBusy ? "[SOS] Starting emergency..." : "[SOS] Emergency Status"}
        </div>
        {coordsText && <div style={{ fontSize: 12, opacity: 0.9, marginBottom: 8 }}>Location: {coordsText}</div>}
        {sosError && <div style={{ fontSize: 12, color: "#ffb5b5", marginBottom: 8 }}>{sosError}</div>}
        {sosActive && (
          <>
            <div style={{ fontSize: 12, marginBottom: 8 }}>
              Alarm is on. Recording front/back camera and audio where supported.
            </div>
            <button
              type="button"
              onClick={handleStopTap}
              style={{
                width: "100%",
                border: "1px solid #ff6f6f",
                background: "#6e0f0f",
                color: "white",
                borderRadius: 10,
                padding: "10px 12px",
                cursor: "pointer",
                fontWeight: 700,
              }}
            >
              {stopLabel}
            </button>
            <div style={{ fontSize: 11, opacity: 0.8, marginTop: 6 }}>
              Taps registered: {stopTapCount}/4
            </div>
          </>
        )}
      </div>
    )}
    {emergencyPopup && (() => {
      const { liveUrl, navigateUrl } = resolveEmergencyUrls(emergencyPopup)
      const actionBtnStyle = {
        border: "1px solid #ff9d9d",
        borderRadius: 8,
        padding: "6px 10px",
        color: "#fff",
        fontWeight: 600,
        cursor: "pointer",
      }

      return (
        <div
          style={{
            position: "fixed",
            left: 16,
            bottom: 16,
            zIndex: 10000,
            width: "min(94vw, 440px)",
            background: "#3a0707",
            border: "1px solid #d45a5a",
            borderRadius: 12,
            padding: 12,
            color: "#fff",
            boxShadow: "0 12px 30px rgba(0,0,0,0.45)",
          }}
        >
          <div style={{ fontWeight: 800, marginBottom: 6 }}>
            [EMERGENCY] {emergencyPopup.title || "Emergency Alert Nearby"}
          </div>
          <div style={{ fontSize: 13, lineHeight: 1.4, marginBottom: 10 }}>
            {emergencyPopup.message || "Someone nearby triggered SOS."}
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button
              type="button"
              onClick={() => openEmergencyLink(liveUrl)}
              disabled={!liveUrl}
              style={{
                ...actionBtnStyle,
                background: liveUrl ? "#8b1717" : "#5a4b4b",
                opacity: liveUrl ? 1 : 0.7,
                cursor: liveUrl ? "pointer" : "not-allowed",
              }}
            >
              Open Live Video
            </button>
            <button
              type="button"
              onClick={() => openEmergencyLink(navigateUrl)}
              disabled={!navigateUrl}
              style={{
                ...actionBtnStyle,
                background: navigateUrl ? "#7b1313" : "#5a4b4b",
                opacity: navigateUrl ? 1 : 0.7,
                cursor: navigateUrl ? "pointer" : "not-allowed",
              }}
            >
              Open Location
            </button>
            <button
              type="button"
              onClick={dismissEmergencyPopup}
              style={{ ...actionBtnStyle, background: "#6e0f0f" }}
            >
              Cancel
            </button>
          </div>
        </div>
      )
    })()}
    </>
  )
}
